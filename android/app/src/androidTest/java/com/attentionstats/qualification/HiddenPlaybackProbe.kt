package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.detection.AdapterRegistry
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.ReelRoute
import org.attentionstats.reels.Surface
import org.json.JSONArray
import org.json.JSONObject

/** Operator-only numeric investigation. Never reads hidden text or enables production hidden ranges. */
internal object HiddenPlaybackProbe {
    fun run(instrumentation: Instrumentation, key: String): String {
        val app = checkNotNull(ReelApp.byKey(key))
        val owner = ReelsCoordinator.get(instrumentation.targetContext)
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        data class Numeric(val visible: Boolean, val box: Rect, val minimum: Float, val maximum: Float,
            val values: MutableSet<Float>)
        val ranges = mutableMapOf<String, Numeric>()
        repeat(10) {
            check(owner.allowed(app)) { "source consent unavailable" }
            val root = checkNotNull(automation.rootInActiveWindow)
            try {
                check(app.owns(root.packageName?.toString())) { "source ownership changed" }
                val visible = SnapshotReader.read(root, app.packages)
                val frame = AdapterRegistry.of(app).read(visible)
                check(visible.complete && !visible.typing && frame.surface == Surface.REELS && frame.route == ReelRoute.VIEWER) {
                    "admitted visible viewer required"
                }
                val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
                try {
                    var count = 0
                    while (queue.isNotEmpty()) {
                        check(count++ < 900) { "numeric traversal truncated" }
                        val node = queue.removeFirst()
                        try {
                            if (!app.owns(node.packageName?.toString()) || node.isEditable || node.isPassword) continue
                            val cls = node.className?.toString() ?: ""
                            if (cls.endsWith("SeekBar") || cls.endsWith("ProgressBar")) {
                                node.rangeInfo?.let { range ->
                                    val box = Rect().also { node.getBoundsInScreen(it) }
                                    val identity = "$cls:${box.left}:${box.top}:${box.right}:${box.bottom}:${node.isVisibleToUser}"
                                    ranges.getOrPut(identity) { Numeric(node.isVisibleToUser, box, range.min, range.max, mutableSetOf()) }
                                        .values.add(range.current)
                                }
                            }
                            for (index in 0 until node.childCount) node.getChild(index)?.let { queue.add(it) }
                        } finally { if (node !== root) SnapshotReader.recycle(node) }
                    }
                } finally { queue.forEach { if (it !== root) SnapshotReader.recycle(it) } }
            } finally { SnapshotReader.recycle(root) }
            SystemClock.sleep(400)
        }
        return JSONObject().put("samples", 10).put("numericControls", JSONArray(ranges.values.map { value ->
            JSONObject().put("visible", value.visible).put("left", value.box.left).put("top", value.box.top)
                .put("right", value.box.right).put("bottom", value.box.bottom).put("minimum", value.minimum)
                .put("maximum", value.maximum).put("distinctValues", value.values.size)
                .put("firstBound", value.values.minOrNull()).put("lastBound", value.values.maxOrNull())
        })).put("hiddenTextRead", false).put("realGestures", 0).toString()
    }
}
