package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.attentionstats.autoscroll.AutoScrollSession
import com.attentionstats.detection.SnapshotReader
import org.json.JSONObject
import org.attentionstats.detection.AdapterRegistry
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.ReelRoute

/** Operator-only actions are confined to fixed controls in this app's own overlay. */
internal object LiveHudVerification {
    data class State(val header: Rect?, val controls: List<Pair<String, Rect>>, val waiting: Boolean = false,
        val expanded: Boolean = false, val paused: Boolean = false) {
        fun json() = JSONObject().put("visible", header != null).put("autoControls", controls.size)
    }
    private const val HEADER = "header"
    private fun scan(instrumentation: Instrumentation, automation: UiAutomation, click: String? = null): State {
        var header: Rect? = null
        val controls = mutableListOf<Pair<String, Rect>>()
        var clicked = false
        var waiting = false
        var expanded = false
        var paused = false
        for (window in automation.windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
            val root = window.root ?: continue
            try {
                if (root.packageName?.toString() != instrumentation.targetContext.packageName) continue
                val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
                var visited = 0
                try {
                    while (queue.isNotEmpty() && visited++ < 120) {
                        val node = queue.removeFirst()
                        try {
                            if (!node.isVisibleToUser) continue
                            val description = node.contentDescription?.toString() ?: ""
                            val text = node.text?.toString() ?: ""
                            val isHeader = description.startsWith("Estimated viewer pages.") || description.startsWith("Counting paused.")
                            val isControl = text == "Autoscroll"
                            if (text == "Waiting for playback progress") waiting = true
                            val controlState = if (description.startsWith("Auto-scroll on")) "on" else "off"
                            val box = Rect().also { node.getBoundsInScreen(it) }
                            if (isHeader) {
                                header = box; expanded = node.isSelected
                                paused = description.startsWith("Counting paused.")
                            }
                            if (isControl) controls.add(controlState to box)
                            if (!clicked && click != null && ((click == HEADER && isHeader) || (isControl && click == controlState))) {
                                check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)); clicked = true
                            }
                            for (index in 0 until node.childCount) node.getChild(index)?.let { queue.add(it) }
                        } finally { if (node !== root) SnapshotReader.recycle(node) }
                    }
                } finally { queue.forEach { if (it !== root) SnapshotReader.recycle(it) } }
            } finally { SnapshotReader.recycle(root) }
        }
        if (click != null) check(clicked) { "Owned HUD control unavailable" }
        return State(header, controls, waiting, expanded, paused)
    }
    fun state(instrumentation: Instrumentation, automation: UiAutomation) = scan(instrumentation, automation)
    fun choose(instrumentation: Instrumentation, automation: UiAutomation, control: String) = scan(instrumentation, automation, control)

    fun run(instrumentation: Instrumentation, automation: UiAutomation): Int {
        val foreground = checkNotNull(automation.rootInActiveWindow)
        try {
            val app = checkNotNull(ReelApp.byPackage(foreground.packageName?.toString() ?: ""))
            val captured = SnapshotReader.read(foreground, app.packages)
            val frame = AdapterRegistry.of(app).read(captured)
            check(captured.complete && !captured.typing && frame.route == ReelRoute.HOME && frame.method.endsWith(".feed")) {
                "Live HUD test requires a qualified feed; no viewer gestures permitted"
            }
        } finally { SnapshotReader.recycle(foreground) }
        var checks = 0
        fun expect(value: Boolean) { check(value) { "live HUD ${checks + 1}" }; checks++ }
        var wasEnabled = false
        instrumentation.runOnMainSync {
            check(!AutoScrollSession.running)
            wasEnabled = AutoScrollSession.enabled
            AutoScrollSession.configure(true, 1) // Explicit operator session Allow; never a production consent bypass.
        }
        try {
            SystemClock.sleep(700)
            val initial = state(instrumentation, automation)
            expect(initial.header != null && initial.controls.isEmpty())
            scan(instrumentation, automation, HEADER); SystemClock.sleep(300)
            val expanded = state(instrumentation, automation)
            expect(expanded.controls.singleOrNull()?.first == "off" && !AutoScrollSession.running)
            capture(instrumentation, automation, expanded, "off")
            scan(instrumentation, automation, "off"); SystemClock.sleep(500)
            expect(AutoScrollSession.running)
            val on = state(instrumentation, automation)
            expect(on.controls.singleOrNull()?.first == "on")
            expect(on.controls.single().second.top >= checkNotNull(on.header).bottom)
            capture(instrumentation, automation, on, "on")
            scan(instrumentation, automation, HEADER); SystemClock.sleep(300)
            expect(AutoScrollSession.running && state(instrumentation, automation).controls.singleOrNull()?.first == "on")
            SystemClock.sleep(1_000)
            expect(AutoScrollSession.running && AutoScrollSession.attempts == 0) // Home is not an eligible completion viewer.
            scan(instrumentation, automation, "on"); SystemClock.sleep(300)
            expect(!AutoScrollSession.running && state(instrumentation, automation).controls.isEmpty())
            scan(instrumentation, automation, HEADER); SystemClock.sleep(300)
            expect(state(instrumentation, automation).controls.singleOrNull()?.first == "off")
            scan(instrumentation, automation, HEADER)
        } finally {
            instrumentation.runOnMainSync { AutoScrollSession.stop(); AutoScrollSession.configure(wasEnabled, 1) }
        }
        return checks
    }
    private fun capture(instrumentation: Instrumentation, automation: UiAutomation, state: State, name: String) {
        val header = state.header ?: return
        val control = state.controls.singleOrNull()?.second ?: return
        val full = automation.takeScreenshot() ?: return
        try {
            val box = Rect(minOf(header.left, control.left), header.top, maxOf(header.right, control.right), control.bottom)
            if (box.left < 0 || box.top < 0 || box.right > full.width || box.bottom > full.height || box.isEmpty) return
            val cropped = android.graphics.Bitmap.createBitmap(full, box.left, box.top, box.width(), box.height())
            try { java.io.File(instrumentation.targetContext.cacheDir, "operator-hud-$name.png").outputStream().use {
                check(cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            } } finally { if (cropped !== full) cropped.recycle() }
        } finally { full.recycle() }
    }
}
