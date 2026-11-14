package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.detection.AdapterRegistry
import org.attentionstats.detection.PlaybackTimeParser
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.ReelRoute
import org.attentionstats.reels.Surface
import org.json.JSONObject

/** No-action operator audit. Only exact numeric formats and fixed player tokens leave live nodes. */
internal object NumericPlaybackAudit {
    fun run(instrumentation: Instrumentation, key: String, refreshProbe: Boolean = false): String {
        check(!refreshProbe || Build.VERSION.SDK_INT >= 34) { "cache comparison requires API34+" }
        val app = checkNotNull(ReelApp.byKey(key))
        val coordinator = ReelsCoordinator.get(instrumentation.targetContext)
        val loaded = java.util.concurrent.CountDownLatch(1)
        var failed = false
        coordinator.snapshot({ loaded.countDown() }, { failed = true; loaded.countDown() })
        check(loaded.await(15, java.util.concurrent.TimeUnit.SECONDS) && !failed)
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val matches = mutableMapOf<String, Int>()
        val values = mutableMapOf<String, MutableSet<Double>>()
        var viewers = 0
        var progressSamples = 0
        var standaloneAds = 0
        val classifications = mutableMapOf<String, Int>()
        var rootRefreshes = 0; var cacheClears = 0
        var rootNumeric = 0; var cacheNumeric = 0
        var rootRecovered = 0; var cacheRecovered = 0; var sameItemPairs = 0
        fun sourceRoot(): AccessibilityNodeInfo {
            val until = SystemClock.elapsedRealtime() + 5_000
            do {
                for (window in automation.windows) {
                    if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isFocused) continue
                    val candidate = window.root ?: continue
                    if (app.owns(candidate.packageName?.toString())) return candidate
                    SnapshotReader.recycle(candidate)
                }
                val active = automation.rootInActiveWindow
                if (active != null) {
                    if (app.owns(active.packageName?.toString())) return active
                    SnapshotReader.recycle(active)
                }
                SystemClock.sleep(100)
            } while (SystemClock.elapsedRealtime() < until)
            error("selected source root unavailable; no content inspected")
        }
        repeat(50) {
            check(coordinator.allowed(app) && !com.attentionstats.autoscroll.AutoScrollSession.running)
            val root = sourceRoot()
            try {
                check(app.owns(root.packageName?.toString())) { "source left foreground" }
                val snapshot = SnapshotReader.read(root, app.packages)
                val frame = AdapterRegistry.of(app).read(snapshot)
                check(snapshot.complete && !snapshot.typing) { "incomplete/editable viewer" }
                classifications.merge("${frame.surface.name}/${frame.route.name}/${frame.category.name}/${frame.mediaKind}", 1, Int::plus)
                // Include a selected-source ordinary watch page in no-action skip-control research.
                // It never becomes an eligible auto-scroll viewer from this audit.
                if (frame.surface == Surface.REELS && frame.route == ReelRoute.VIEWER) viewers++
                if (frame.progress != null) progressSamples++
                if (frame.standaloneAd) standaloneAds++
                if (refreshProbe) {
                    check(frame.surface == Surface.REELS && frame.route == ReelRoute.VIEWER && frame.identity != null) {
                        "cache comparison requires a qualified dedicated viewer"
                    }
                    // Paired, no-action experiment: cached root, refreshed root, then a
                    // newly acquired root after clearing only UiAutomation's own cache.
                    // This does not change the production service's cache or settings.
                    if (root.refresh()) {
                        rootRefreshes++
                        check(app.owns(root.packageName?.toString())) { "source changed after refresh" }
                        val fresh = SnapshotReader.read(root, app.packages)
                        check(fresh.complete && !fresh.typing) { "refreshed source incomplete/editable" }
                        val updated = AdapterRegistry.of(app).read(fresh)
                        if (updated.progress != null) rootNumeric++
                        if (frame.identity != null && frame.identity == updated.identity && frame.progress == null && updated.progress != null)
                            rootRecovered++
                    }
                    if (Build.VERSION.SDK_INT >= 34 && automation.clearCache()) {
                        cacheClears++
                        val freshRoot = sourceRoot()
                        try {
                            check(app.owns(freshRoot.packageName?.toString())) { "source changed after cache clear" }
                            val fresh = SnapshotReader.read(freshRoot, app.packages)
                            check(fresh.complete && !fresh.typing) { "uncached source incomplete/editable" }
                            val updated = AdapterRegistry.of(app).read(fresh)
                            if (updated.progress != null) cacheNumeric++
                            if (frame.identity != null && frame.identity == updated.identity && snapshot.windowId == fresh.windowId) {
                                sameItemPairs++
                                if (frame.progress == null && updated.progress != null) cacheRecovered++
                            }
                        } finally { SnapshotReader.recycle(freshRoot) }
                    }
                    SystemClock.sleep(200)
                    return@repeat
                }
                val viewer = frame.surface == Surface.REELS && frame.route == ReelRoute.VIEWER
                val publicWatchAd = app == ReelApp.YOUTUBE && snapshot.has("watch_player") &&
                    snapshot.has("ad_details_container_layout") && !snapshot.has("comment_composer")
                if (!viewer && !publicWatchAd) return@repeat
                val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
                var count = 0
                try {
                    while (queue.isNotEmpty()) {
                        check(count++ < 900) { "bounded audit truncated" }
                        val node = queue.removeFirst()
                        try {
                            if (!app.owns(node.packageName?.toString()) || !node.isVisibleToUser || node.isEditable || node.isPassword) continue
                            val cls = node.className?.toString().orEmpty()
                            val id = SnapshotReader.shortId(node.viewIdResourceName).orEmpty()
                            fun found(field: String, value: Double? = null) {
                                val code = "$cls/$id/$field"
                                matches[code] = (matches[code] ?: 0) + 1
                                if (value != null) values.getOrPut(code) { mutableSetOf() }.add(value)
                            }
                            for ((field, label) in listOf("text" to node.text, "description" to node.contentDescription,
                                "state" to if (Build.VERSION.SDK_INT >= 30) node.stateDescription else null)) {
                                PlaybackTimeParser.parse(label)?.let { (position, duration) -> found(field, position.toDouble() / duration) }
                                PlaybackTimeParser.elapsed(label)?.let { found("$field-clock", it.toDouble()) }
                                if (label?.toString() in setOf("Play", "Pause", "Replay", "Replay video", "Watch again")) found("fixed_player_token")
                                if (label?.toString() in setOf("Skip ad", "Skip ads", "Skip Ad", "Skip Ads", "Skip")) {
                                    found("skip_label")
                                    if (node.isClickable && node.isEnabled) found("skip_label_clickable_enabled")
                                }
                            }
                            node.rangeInfo?.let { range ->
                                if (range.min.isFinite() && range.max.isFinite() && range.max > range.min && range.current in range.min..range.max)
                                    found("range", ((range.current - range.min) / (range.max - range.min)).toDouble())
                            }
                            for (index in 0 until node.childCount) node.getChild(index)?.let { queue.add(it) }
                        } finally { if (node !== root) SnapshotReader.recycle(node) }
                    }
                } finally { queue.forEach { if (it !== root) SnapshotReader.recycle(it) } }
            } finally { SnapshotReader.recycle(root) }
            SystemClock.sleep(200)
        }
        return JSONObject().put("viewerSamples", viewers).put("samples", 50).put("matches", JSONObject(matches as Map<*, *>))
            .put("classifications", JSONObject(classifications as Map<*, *>)).put("progressSamples", progressSamples)
            .put("standaloneAdSamples", standaloneAds)
            .put("distinctValues", JSONObject(values.mapValues { it.value.size } as Map<*, *>))
            .put("refreshProbe", refreshProbe).put("rootRefreshes", rootRefreshes).put("cacheClears", cacheClears)
            .put("rootNumericSamples", rootNumeric).put("uncachedNumericSamples", cacheNumeric)
            .put("sameItemPairs", sameItemPairs).put("sameItemRootRecoveries", rootRecovered).put("sameItemCacheRecoveries", cacheRecovered)
            .put("sourceGestures", 0).put("rawLabelsRetained", false).toString()
    }
}
