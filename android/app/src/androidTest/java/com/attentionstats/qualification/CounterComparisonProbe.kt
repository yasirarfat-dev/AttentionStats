package com.attentionstats.qualification

import android.app.UiAutomation
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.json.JSONArray
import org.json.JSONObject

/** Explicit operator observation of a supplied comparison overlay. No app identity or totals emitted. */
internal class CounterComparisonProbe(private val automation: UiAutomation, private val packageName: String) {
    private var baseline: List<Long>? = null
    fun inspect(): JSONObject {
        val values = mutableListOf<Long>()
        for (window in automation.windows) {
            try {
                if (window.type !in setOf(AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY, AccessibilityWindowInfo.TYPE_SYSTEM)) continue
                val root = window.root ?: continue
                val queue = ArrayDeque<AccessibilityNodeInfo>()
                try {
                    if (root.packageName?.toString() != packageName) continue
                    queue.add(root)
                    var nodes = 0
                    while (queue.isNotEmpty() && nodes < 64) {
                        val node = queue.removeFirst()
                        try {
                            nodes++
                            if (!node.isVisibleToUser || node.isEditable || node.isPassword || node.packageName?.toString() != packageName) continue
                            for (text in listOf(node.text, node.contentDescription)) {
                                if (text == null || text.length !in 1..80) continue
                                for (match in Regex("(?<![0-9])[0-9]{1,9}(?![0-9])").findAll(text)) {
                                    if (values.size >= 4) break
                                    values.add(match.value.toLong())
                                }
                            }
                            val remaining = 64 - nodes - queue.size
                            for (index in 0 until minOf(node.childCount, remaining)) node.getChild(index)?.let { queue.add(it) }
                        } finally { if (node !== root) recycle(node) }
                    }
                } finally { queue.forEach { if (it !== root) recycle(it) }; recycle(root) }
            } finally { recycle(window) }
        }
        val first = baseline
        if (first == null && values.isNotEmpty()) baseline = values.toList()
        val aligned = first != null && values.isNotEmpty() && first.size == values.size
        return JSONObject().put("available", values.isNotEmpty()).put("aligned", aligned)
            .put("deltas", JSONArray(if (aligned) values.mapIndexed { index, value -> value - checkNotNull(first)[index] } else emptyList<Long>()))
    }
    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo) { if (Build.VERSION.SDK_INT < 33) node.recycle() }
    @Suppress("DEPRECATION")
    private fun recycle(window: AccessibilityWindowInfo) { if (Build.VERSION.SDK_INT < 33) window.recycle() }
}
