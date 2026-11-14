package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
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
import org.json.JSONArray
import org.json.JSONObject

/** Operator-only fixed-control investigation. Never changes a provider setting. */
internal object BuiltInReelsProbe {
    @Suppress("DEPRECATION") // Compat node copies/checked state on API24+, test process only.
    fun run(instrumentation: Instrumentation, key: String, openMenu: Boolean): String {
        check(key in setOf("facebook", "instagram"))
        val app = checkNotNull(ReelApp.byKey(key))
        val owner = ReelsCoordinator.get(instrumentation.targetContext)
        val loaded = java.util.concurrent.CountDownLatch(1)
        var failed = false
        owner.snapshot({ loaded.countDown() }, { failed = true; loaded.countDown() })
        check(loaded.await(15, java.util.concurrent.TimeUnit.SECONDS) && !failed)
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun sourceRoot(): AccessibilityNodeInfo {
            check(owner.allowed(app) && !AutoScrollSession.running) { "source consent/run unavailable" }
            for (window in automation.windows) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isFocused) continue
                val root = window.root ?: continue
                if (app.owns(root.packageName?.toString())) return root
                SnapshotReader.recycle(root)
                error("source lost foreground")
            }
            error("focused source root unavailable")
        }
        fun scan(action: Boolean): JSONObject {
            val root = sourceRoot()
            val candidates = mutableListOf<AccessibilityNodeInfo>()
            val controls = JSONArray()
            var nodes = 0
            var complete = true
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            try {
                val snapshot = SnapshotReader.read(root, app.packages)
                check(snapshot.complete && !snapshot.typing) { "incomplete/editable source" }
                val frame = AdapterRegistry.of(app).read(snapshot)
                val epoch = owner.epoch()
                if (action) check(frame.surface == Surface.REELS && frame.route == ReelRoute.VIEWER && frame.identity != null) {
                    "menu action requires qualified dedicated viewer"
                }
                queue.add(AccessibilityNodeInfo.obtain(root))
                while (queue.isNotEmpty()) {
                    if (++nodes > 512) { complete = false; break }
                    val node = queue.removeFirst()
                    try {
                        check(app.owns(node.packageName?.toString()) && !node.isEditable && !node.isPassword) {
                            "foreign/editable source; no menu action"
                        }
                        if (!node.isVisibleToUser) continue
                        val fixed = listOf(node.text, node.contentDescription).mapNotNull {
                            it?.toString()?.trim()?.lowercase(java.util.Locale.ROOT)
                        }.firstOrNull { it in setOf("auto scroll", "auto-scroll", "autoscroll", "playback", "playback settings", "more", "more options") }
                        if (fixed != null) {
                            val state = if (Build.VERSION.SDK_INT >= 30) node.stateDescription?.toString()?.lowercase(java.util.Locale.ROOT) else null
                            controls.put(JSONObject().put("token", fixed).put("clickable", node.isClickable)
                                .put("checkable", node.isCheckable).put("checked", if (node.isCheckable) node.isChecked else JSONObject.NULL)
                                .put("state", state?.takeIf { it in setOf("on", "off") } ?: JSONObject.NULL))
                            if (action && fixed in setOf("more", "more options") && node.isClickable && node.isEnabled)
                                candidates.add(AccessibilityNodeInfo.obtain(node))
                        }
                        for (index in 0 until node.childCount) node.getChild(index)?.let { queue.add(it) }
                    } finally { SnapshotReader.recycle(node) }
                }
                check(complete) { "truncated source; no menu action" }
                if (action) {
                    check(candidates.size == 1) { "unique visible source menu unavailable" }
                    val fresh = sourceRoot()
                    try {
                        check(fresh.windowId == root.windowId && candidates.single().refresh()) { "source menu changed" }
                        val finalSnapshot = SnapshotReader.read(fresh, app.packages)
                        val finalFrame = AdapterRegistry.of(app).read(finalSnapshot)
                        check(owner.epoch() == epoch && finalSnapshot.complete && !finalSnapshot.typing && finalFrame.surface == Surface.REELS &&
                            finalFrame.route == ReelRoute.VIEWER && finalFrame.identity == frame.identity) { "viewer changed" }
                        check(listOf(candidates.single().text, candidates.single().contentDescription).any {
                            it?.toString()?.trim()?.lowercase(java.util.Locale.ROOT) in setOf("more", "more options")
                        }) { "menu identity changed" }
                        check(candidates.single().isVisibleToUser && candidates.single().isEnabled && candidates.single().isClickable) { "source menu unavailable" }
                        check(candidates.single().performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "menu click rejected" }
                    } finally { SnapshotReader.recycle(fresh) }
                }
                return JSONObject().put("nodes", nodes).put("surface", frame.surface.name)
                    .put("route", frame.route.name).put("controls", controls)
            } finally {
                queue.forEach { SnapshotReader.recycle(it) }
                candidates.forEach { SnapshotReader.recycle(it) }
                SnapshotReader.recycle(root)
            }
        }
        val before = scan(openMenu)
        if (openMenu) SystemClock.sleep(500)
        return JSONObject().put("source", key).put("before", before)
            .put("after", if (openMenu) scan(false) else JSONObject.NULL)
            .put("menuActions", if (openMenu) 1 else 0).put("providerToggleActions", 0)
            .put("rawLabelsRetained", false).toString()
    }
}
