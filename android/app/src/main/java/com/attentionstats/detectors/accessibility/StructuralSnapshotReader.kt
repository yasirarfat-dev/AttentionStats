package com.attentionstats.detectors.accessibility

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.attentionstats.detectors.SurfaceDetector
import org.attentionstats.reels.ReelApp
import org.json.JSONArray
import org.json.JSONObject

/** Bounded Android acquisition. Platform adapters receive values, never live nodes. */
internal object StructuralSnapshotReader {
    internal data class Shape(
        val ids: Set<String>, val nodes: Int, val bounded: Boolean, val editable: Boolean,
        val positions: JSONArray, val controls: JSONArray, val centerPage: Long?,
        val classes: Set<String>, val viewport: JSONObject, val anchors: JSONArray,
    )

    private data class Layout(val slot: Int, val id: String, val bounds: Rect, val row: Int, val column: Int) {
        fun json(): JSONObject = JSONObject().put("slot", slot).put("id", id)
            .put("left", bounds.left).put("top", bounds.top).put("right", bounds.right).put("bottom", bounds.bottom)
            .put("row", row).put("column", column)
    }

    fun inspect(app: ReelApp, root: AccessibilityNodeInfo, detector: SurfaceDetector?, diagnostics: Boolean): Shape {
        val ids = linkedSetOf<String>()
        val classes = linkedSetOf<String>()
        val positions = JSONArray()
        val controls = JSONArray()
        val anchors = JSONArray()
        var nodes = 0
        var editable = false
        var centerPage: Long? = null
        var positionCount = 0
        var truncated = false
        val screen = Rect().also { root.getBoundsInScreen(it) }
        val scrollIds = detector?.definition?.scrollSourceIds ?: emptySet()
        data class Pending(val node: AccessibilityNodeInfo, val owner: String?, val owned: Boolean,
            val ancestors: List<Layout>)
        val queue = ArrayDeque<Pending>()
        queue.add(Pending(root, null, false, emptyList()))
        try {
            while (queue.isNotEmpty() && nodes < 192) {
                val pending = queue.removeFirst()
                val node = pending.node
                try {
                    val slot = nodes++ // Scan-local association only, never a target/content identity.
                    if (node.packageName?.toString() != app.packageName || !node.isVisibleToUser) continue
                    if (node.isEditable || node.isPassword) { editable = true; continue }
                    if (diagnostics && classes.size < 24) className(node).takeIf { it.isNotEmpty() }?.let { classes.add(it) }
                    val id = resourceId(app, node.viewIdResourceName)
                    if (id != null && ids.size < 96) ids.add(id)
                    val item = node.collectionItemInfo
                    val bounds = if (diagnostics || item != null) Rect().also { node.getBoundsInScreen(it) } else null
                    val layout = if (diagnostics) Layout(slot, id ?: "", checkNotNull(bounds),
                        item?.rowIndex ?: -1, item?.columnIndex ?: -1) else null
                    if (diagnostics && id != null && node.isClickable && controls.length() < 32) {
                        controls.put(JSONObject().put("id", id).put("class", className(node))
                            .put("left", checkNotNull(bounds).left).put("top", bounds.top)
                            .put("right", bounds.right).put("bottom", bounds.bottom))
                    }
                    if (diagnostics && id in (detector?.diagnosticAnchorIds ?: emptySet()) && anchors.length() < 16) {
                        anchors.put(checkNotNull(layout).json().put("ancestors",
                            JSONArray(pending.ancestors.map { it.json() })))
                    }
                    val owner = if (id != null && (id in scrollIds || node.isScrollable)) id else pending.owner
                    if (item != null && positionCount < 12) {
                        positionCount++
                        val centered = checkNotNull(bounds).contains(screen.centerX(), screen.centerY())
                        if (diagnostics && positions.length() < 12) positions.put(JSONObject().put("id", id ?: "")
                            .put("owner", owner ?: "").put("row", item.rowIndex).put("column", item.columnIndex)
                            .put("centered", centered))
                        if (centered && owner in scrollIds && item.rowIndex >= 0) centerPage = item.rowIndex.toLong()
                    }
                    val ancestors = if (layout == null) emptyList() else (listOf(layout) + pending.ancestors).take(3)
                    val remaining = 192 - nodes - queue.size
                    if (node.childCount > remaining) truncated = true
                    for (index in 0 until minOf(node.childCount, remaining)) {
                        node.getChild(index)?.let { queue.add(Pending(it, owner, true, ancestors)) }
                    }
                } finally { if (pending.owned) recycleCompat(node) }
            }
            return Shape(ids, nodes, queue.isEmpty() && !truncated, editable, positions, controls, centerPage, classes,
                JSONObject().put("left", screen.left).put("top", screen.top).put("right", screen.right)
                    .put("bottom", screen.bottom), anchors)
        } finally { queue.forEach { if (it.owned) recycleCompat(it.node) } }
    }

    fun resourceId(app: ReelApp, value: String?): String? {
        val prefix = app.packageName + ":id/"
        if (value == null || !value.startsWith(prefix)) return null
        return value.removePrefix(prefix).takeIf { Regex("[a-zA-Z0-9_]{1,80}").matches(it) }
    }

    fun className(node: AccessibilityNodeInfo?): String = node?.className?.toString()
        ?.takeIf { Regex("[A-Za-z0-9_.$]{1,120}").matches(it) } ?: ""

    @Suppress("DEPRECATION")
    private fun recycleCompat(node: AccessibilityNodeInfo?) { if (Build.VERSION.SDK_INT < 33) node?.recycle() }
}
