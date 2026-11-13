package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.attentionstats.autoscroll.AutoScrollSession
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.detection.AdapterRegistry
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.ReelRoute
import org.attentionstats.reels.Surface
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Operator-only numeric research. No provider controls, titles, artwork or production grant. */
internal object NumericMediaSessionProbe {
    fun run(instrumentation: Instrumentation, key: String): String {
        check(Build.VERSION.SDK_INT >= 29) { "shell-scoped probe requires API29" }
        val app = checkNotNull(ReelApp.byKey(key))
        val context = instrumentation.targetContext
        val owner = ReelsCoordinator.get(context)
        val loaded = CountDownLatch(1)
        var failed = false
        owner.snapshot({ loaded.countDown() }, { failed = true; loaded.countDown() })
        check(loaded.await(15, TimeUnit.SECONDS) && !failed) { "source choices unavailable" }
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun sourceRoot(): AccessibilityNodeInfo {
            for (window in automation.windows) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isFocused) continue
                val root = window.root ?: continue
                if (app.owns(root.packageName?.toString())) return root
                SnapshotReader.recycle(root)
                error("selected source lost foreground")
            }
            val root = checkNotNull(automation.rootInActiveWindow) { "source root unavailable" }
            if (app.owns(root.packageName?.toString())) return root
            SnapshotReader.recycle(root)
            error("selected source lost foreground")
        }
        val manager = context.getSystemService(MediaSessionManager::class.java)
        var matching = 0
        var ambiguous = 0
        var positiveDuration = 0
        var playing = 0
        var usable = 0
        val positions = mutableSetOf<Long>()
        val durations = mutableSetOf<Long>()
        automation.adoptShellPermissionIdentity("android.permission.MEDIA_CONTENT_CONTROL")
        try {
            repeat(30) {
                check(owner.allowed(app) && !AutoScrollSession.running) { "source consent/run changed" }
                val root = sourceRoot()
                val snapshot = try { SnapshotReader.read(root, app.packages) } finally { SnapshotReader.recycle(root) }
                val frame = AdapterRegistry.of(app).read(snapshot)
                check(snapshot.complete && !snapshot.typing && frame.surface == Surface.REELS && frame.route == ReelRoute.VIEWER) {
                    "complete noneditable dedicated viewer required"
                }
                // Inspect numeric fields only after package filtering; never retain other sessions.
                val sessions = manager.getActiveSessions(null).filter { app.owns(it.packageName) }
                if (sessions.isNotEmpty()) matching++
                if (sessions.size > 1) ambiguous++
                sessions.singleOrNull()?.let { session ->
                    val duration = session.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                    val state = session.playbackState
                    val position = state?.position ?: -1L
                    if (duration > 0) { positiveDuration++; durations.add(duration) }
                    if (state?.state == PlaybackState.STATE_PLAYING) playing++
                    if (duration > 0 && position in 0..duration && state?.state == PlaybackState.STATE_PLAYING) {
                        usable++; positions.add(position)
                    }
                }
                SystemClock.sleep(250)
            }
        } finally { automation.dropShellPermissionIdentity() }
        return JSONObject().put("samples", 30).put("matchingSessionSamples", matching)
            .put("ambiguousSessionSamples", ambiguous).put("positiveDurationSamples", positiveDuration)
            .put("playingStateSamples", playing).put("usableNumericSamples", usable)
            .put("distinctUsablePositions", positions.size).put("distinctPositiveDurations", durations.size)
            .put("sourceGestures", 0).put("contentFieldsRead", false).put("productionPermissionAdded", false).toString()
    }
}
