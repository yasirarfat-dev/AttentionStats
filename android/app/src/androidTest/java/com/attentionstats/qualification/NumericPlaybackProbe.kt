package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.KeyguardManager
import android.app.UiAutomation
import android.os.Build
import android.os.PowerManager
import android.view.accessibility.AccessibilityNodeInfo
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.reels.ReelApp
import org.json.JSONArray
import org.json.JSONObject

/** Operator-only investigation of numeric player controls, including hidden controls.
 * Never reads text/descriptions or captures pixels. No production capability admission.
 */
internal object NumericPlaybackProbe {
    fun inspect(instrumentation: Instrumentation, automation: UiAutomation): JSONObject {
        fun withheld(reason: String) = JSONObject().put("withheld", reason)
        val context = instrumentation.targetContext
        val coordinator = ReelsCoordinator.get(context)
        if (!context.getSystemService(PowerManager::class.java).isInteractive ||
            context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return withheld("locked")
        val root = automation.rootInActiveWindow ?: return withheld("root_unavailable")
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        try {
            val app = ReelApp.byPackage(root.packageName?.toString() ?: "") ?: return withheld("outside")
            if (!coordinator.grantEnabled() || !coordinator.allowed(app)) return withheld("source_off")
            val ids = when (app) {
                ReelApp.YOUTUBE -> setOf("reel_time_bar")
                ReelApp.TIKTOK -> setOf("vt6")
                else -> return withheld("outside_probe_scope")
            }
            val epoch = coordinator.epoch()
            val ranges = JSONArray()
            var nodes = 0
            var truncated = false
            queue.add(root)
            while (queue.isNotEmpty() && nodes < 192) {
                val node = queue.removeFirst()
                try {
                    nodes++
                    if (node.packageName?.toString() != app.packageName || node.isEditable || node.isPassword) continue
                    val id = node.viewIdResourceName?.removePrefix(app.packageName + ":id/")
                    if (id in ids && ranges.length() < 8) node.rangeInfo?.let {
                        if (it.min.isFinite() && it.max.isFinite() && it.current.isFinite())
                            ranges.put(JSONObject().put("id", id).put("visible", node.isVisibleToUser)
                                .put("min", it.min.toDouble()).put("max", it.max.toDouble()).put("current", it.current.toDouble()))
                    }
                    val remaining = 192 - nodes - queue.size
                    if (node.childCount > remaining) truncated = true
                    for (index in 0 until minOf(node.childCount, remaining)) node.getChild(index)?.let { queue.add(it) }
                } finally { if (node !== root) recycle(node) }
            }
            val current = automation.rootInActiveWindow
            val valid = try { current != null && current.packageName == root.packageName && current.windowId == root.windowId }
                finally { if (current != null) recycle(current) }
            if (!valid || coordinator.epoch() != epoch || !coordinator.allowed(app)) return withheld("context_changed")
            return JSONObject().put("app", app.key).put("bounded", queue.isEmpty() && !truncated).put("ranges", ranges)
        } finally {
            queue.forEach { if (it !== root) recycle(it) }
            recycle(root)
        }
    }
    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo) { if (Build.VERSION.SDK_INT < 33) node.recycle() }
}
