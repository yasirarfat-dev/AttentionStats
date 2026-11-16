package com.attentionstats.qualification

import android.app.Instrumentation
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityWindowInfo
import com.attentionstats.autoscroll.AutoScrollSession
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.reels.ReelsAccessibilityService
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.detection.AdapterRegistry
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.ReelRoute
import org.attentionstats.reels.Surface
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** After actual Stop only: paired reads on the real service worker/connection, never UiAutomation's cache. */
internal object ServicePlaybackCacheAudit {
    fun run(instrumentation: Instrumentation, key: String): String {
        check(Build.VERSION.SDK_INT >= 33 && com.attentionstats.BuildConfig.DEBUG)
        val owner = ReelsCoordinator.get(instrumentation.targetContext)
        val app = checkNotNull(ReelApp.byKey(key))
        check(key == "tiktok" && owner.allowed(app) && owner.serviceConnected && !AutoScrollSession.running)
        val epoch = owner.epoch()
        val lock = Any()
        val done = CountDownLatch(50)
        var accepting = true
        var samples = 0; var clears = 0; var sameItemPairs = 0
        var beforeProgress = 0; var afterProgress = 0; var missingPairs = 0; var recovered = 0
        var beforeAbsolute = 0; var afterAbsolute = 0; var maximumPairMs = 0L
        var context: Pair<Int, Long>? = null
        var failure: String? = null
        check(ReelsAccessibilityService.onPlaybackAcquisitionDiagnostic == null) { "Existing service probe preserved" }
        val probe: (ReelsAccessibilityService, ReelsAccessibilityService.PlaybackAcquisitionDiagnostic) -> Unit = probe@ { service, before ->
            synchronized(lock) {
                if (!accepting || samples >= 50 || failure != null) return@probe
                var stage = "eligibility"
                try {
                    check(!AutoScrollSession.running && owner.allowed(app) && owner.serviceConnected && owner.epoch() == epoch)
                    check(before.app == app && before.epoch == epoch && before.complete)
                    check(before.frame.surface == Surface.REELS && before.frame.route == ReelRoute.VIEWER)
                    val identity = checkNotNull(before.frame.identity)
                    val current = before.window to identity
                    check(context == null || context == current)
                    context = current
                    stage = "service_cache_clear"
                    check(service.clearCache()); clears++
                    stage = "fresh_service_root"
                    val roots = service.windows
                    val root = checkNotNull(roots.firstOrNull {
                        it.isFocused && it.type == AccessibilityWindowInfo.TYPE_APPLICATION
                    }?.root)
                    try {
                        check(app.owns(root.packageName?.toString()))
                        val snapshot = SnapshotReader.read(root, app.packages)
                        val fresh = AdapterRegistry.of(app).read(snapshot)
                        stage = "same_item_context"
                        check(snapshot.complete && !snapshot.typing && snapshot.windowId == before.window)
                        check(fresh.surface == Surface.REELS && fresh.route == ReelRoute.VIEWER && fresh.identity == identity)
                        check(!AutoScrollSession.running && owner.allowed(app) && owner.epoch() == epoch)
                        stage = "pair_freshness"
                        val pairMs = SystemClock.elapsedRealtime() - before.atMs
                        check(pairMs in 0..1_500)
                        maximumPairMs = maxOf(maximumPairMs, pairMs)
                        sameItemPairs++
                        if (before.frame.progress != null) beforeProgress++
                        if (fresh.progress != null) afterProgress++
                        if (before.frame.positionMs != null && before.frame.durationMs != null) beforeAbsolute++
                        if (fresh.positionMs != null && fresh.durationMs != null) afterAbsolute++
                        if (before.frame.progress == null) {
                            missingPairs++
                            if (fresh.progress != null) recovered++
                        }
                        samples++
                        done.countDown()
                    } finally { SnapshotReader.recycle(root) }
                } catch (_: Throwable) {
                    // Fixed stage only; never publish provider exception text, labels or item identifiers.
                    failure = stage
                    while (done.count > 0) done.countDown()
                }
            }
        }
        ReelsAccessibilityService.onPlaybackAcquisitionDiagnostic = probe
        val completed: Boolean
        try { completed = done.await(60, TimeUnit.SECONDS) }
        finally {
            ReelsAccessibilityService.onPlaybackAcquisitionDiagnostic = null
            synchronized(lock) { accepting = false }
        }
        return synchronized(lock) {
            check(!AutoScrollSession.running) { "Stopped run changed during service audit" }
            JSONObject().put("completed", completed && failure == null && samples == 50)
                .put("samples", samples).put("serviceCacheClears", clears).put("sameItemWindowEpochPairs", sameItemPairs)
                .put("beforeProgressSamples", beforeProgress).put("afterProgressSamples", afterProgress)
                .put("missingProgressPairs", missingPairs).put("sameItemRecoveries", recovered)
                .put("beforeAbsoluteTimeSamples", beforeAbsolute).put("afterAbsoluteTimeSamples", afterAbsolute)
                .put("maximumPairMs", maximumPairMs).put("failureStage", failure ?: JSONObject.NULL)
                .put("sourceGestures", 0).put("rawLabelsOrIdentitiesRetained", false).toString()
        }
    }
}
