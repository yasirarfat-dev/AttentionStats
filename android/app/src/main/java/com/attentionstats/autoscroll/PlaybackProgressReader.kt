package com.attentionstats.autoscroll

import android.os.Build
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.attentionstats.reels.ReelApp
import org.json.JSONArray
import org.json.JSONObject

/** Bounded numeric playback investigation. No titles, descriptions, messages or pixels. */
internal object PlaybackProgressReader {
    fun inspect(root: AccessibilityNodeInfo, app: ReelApp): JSONObject {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val ranges = JSONArray()
        val controls = JSONArray()
        val ids = linkedSetOf<String>()
        val classes = linkedSetOf<String>()
        val times = JSONArray()
        var count = 0
        var rejected = false
        queue.add(root)
        try {
            while (queue.isNotEmpty() && count < 192) {
                val node = queue.removeFirst()
                try {
                    count++
                    if (node.packageName?.toString() != app.packageName || !node.isVisibleToUser) continue
                    if (node.isEditable || node.isPassword) { rejected = true; continue }
                    val range = node.rangeInfo
                    val prefix = app.packageName + ":id/"
                    val id = node.viewIdResourceName?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
                        ?.takeIf { Regex("[A-Za-z0-9_]{1,80}").matches(it) } ?: ""
                    if (id.isNotEmpty() && ids.size < 96) ids.add(id)
                    val nodeClass = node.className?.toString()?.takeIf { Regex("[A-Za-z0-9_.$]{1,120}").matches(it) } ?: ""
                    if (classes.size < 24) classes.add(nodeClass)
                    if (nodeClass == "android.widget.SeekBar" || id == "reel_time_bar") {
                        for (value in listOf(node.text, node.contentDescription,
                            if (Build.VERSION.SDK_INT >= 30) node.stateDescription else null)) {
                            val pair = PlaybackTimeParser.parse(value)
                            if (pair != null && times.length() < 8) times.put(JSONObject().put("id", id)
                                .put("elapsedMs", pair.first).put("durationMs", pair.second))
                            else PlaybackTimeParser.elapsed(value)?.let { elapsed ->
                                if (times.length() < 8) times.put(JSONObject().put("id", id).put("elapsedMs", elapsed))
                            }
                        }
                    }
                    val token = com.attentionstats.detectors.accessibility.InterfaceLabelMatcher.match(app, node, true, true)
                    if (controls.length() < 32 && (token != null ||
                        (node.isClickable && Regex(".*(clips|reel|short|tab).*", RegexOption.IGNORE_CASE).matches(id)))) {
                        val rect = Rect().also { node.getBoundsInScreen(it) }
                        controls.put(JSONObject().put("id", id).put("token", token ?: "")
                            .put("left", rect.left).put("top", rect.top).put("right", rect.right).put("bottom", rect.bottom))
                    }
                    if (range != null && ranges.length() < 8) {
                        val rect = Rect().also { node.getBoundsInScreen(it) }
                        val kind = node.className?.toString()?.takeIf { Regex("[A-Za-z0-9_.$]{1,120}").matches(it) } ?: ""
                        if (range.min.isFinite() && range.max.isFinite() && range.current.isFinite())
                            ranges.put(JSONObject().put("id", id).put("class", kind).put("min", range.min.toDouble())
                                .put("max", range.max.toDouble()).put("current", range.current.toDouble())
                                .put("left", rect.left).put("top", rect.top).put("right", rect.right).put("bottom", rect.bottom))
                    }
                    val remaining = 192 - count - queue.size
                    if (node.childCount > remaining) rejected = true
                    for (index in 0 until minOf(node.childCount, remaining)) node.getChild(index)?.let { queue.add(it) }
                } finally { if (node !== root) recycle(node) }
            }
            return JSONObject().put("app", app.key).put("bounded", queue.isEmpty() && !rejected)
                .put("nodes", count).put("ranges", ranges).put("controls", controls)
                .put("ids", JSONArray(ids.toList())).put("classes", JSONArray(classes.toList())).put("times", times)
        } finally { queue.forEach { if (it !== root) recycle(it) } }
    }

    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo) { if (Build.VERSION.SDK_INT < 33) node.recycle() }
}
