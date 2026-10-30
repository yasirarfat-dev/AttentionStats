package com.attentionstats.detection

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import org.attentionstats.detection.LayoutBounds
import org.attentionstats.detection.ScreenNode
import org.attentionstats.detection.ScreenSnapshot

/** Bounded breadth-first copy of the active window. The only place that touches live nodes. */
object SnapshotReader {
    private const val NODE_LIMIT = 900
    private const val LABEL_LIMIT = 160

    fun read(root: AccessibilityNodeInfo, packages: Collection<String>): ScreenSnapshot {
        val nodes = ArrayList<ScreenNode>(256)
        val screen = Rect().also { root.getBoundsInScreen(it) }
        var typing = false
        data class Pending(val node: AccessibilityNodeInfo, val parent: Int, val depth: Int, val owned: Boolean)
        val queue = ArrayDeque<Pending>()
        queue.add(Pending(root, -1, 0, false))
        var truncated = false
        try {
            while (queue.isNotEmpty()) {
                val pending = queue.removeFirst()
                val node = pending.node
                try {
                    if (nodes.size >= NODE_LIMIT) { truncated = true; continue }
                    if (!node.isVisibleToUser || node.packageName?.toString() !in packages) continue
                    if (node.isPassword) continue
                    if (node.isEditable && node.isFocused) typing = true
                    val rect = Rect().also { node.getBoundsInScreen(it) }
                    val index = nodes.size
                    val text = node.text?.takeIf { it.isNotBlank() } ?: node.contentDescription?.takeIf { it.isNotBlank() }
                    val label = if (node.isEditable) null else text?.let { if (it.length > LABEL_LIMIT) it.subSequence(0, LABEL_LIMIT) else it }?.toString()
                    nodes.add(ScreenNode(index, pending.parent, pending.depth, shortId(node.viewIdResourceName),
                        node.className?.toString() ?: "", label,
                        LayoutBounds(rect.left, rect.top, rect.right, rect.bottom), node.isScrollable,
                        node.isClickable, node.isSelected, node.hashCode(),
                        node.rangeInfo?.let { ScreenNode.Range(it.min, it.max, it.current) }))
                    for (child in 0 until node.childCount) {
                        node.getChild(child)?.let { queue.add(Pending(it, index, pending.depth + 1, true)) }
                    }
                } finally { if (pending.owned) recycle(node) }
            }
        } finally { queue.forEach { if (it.owned) recycle(it.node) } }
        return ScreenSnapshot(root.packageName?.toString() ?: "", root.windowId,
            LayoutBounds(screen.left, screen.top, screen.right, screen.bottom), nodes, !truncated, typing)
    }

    fun shortId(value: String?): String? {
        if (value == null) return null
        val at = value.indexOf(":id/")
        return (if (at >= 0) value.substring(at + 4) else value).takeIf { it.isNotEmpty() && it.length <= 80 }
    }

    @Suppress("DEPRECATION") // recycle is required on API 24-32 and a no-op afterwards.
    fun recycle(node: AccessibilityNodeInfo?) { if (Build.VERSION.SDK_INT < 33) node?.recycle() }
}
