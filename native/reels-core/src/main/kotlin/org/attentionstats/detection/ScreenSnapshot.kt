package org.attentionstats.detection

/** Screen rectangle in pixels. */
data class LayoutBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val empty: Boolean get() = width <= 0 || height <= 0
    fun contains(other: LayoutBounds, slack: Int = 2): Boolean = other.left >= left - slack && other.right <= right + slack &&
        other.top >= top - slack && other.bottom <= bottom + slack
    fun visibleHeightWithin(clip: LayoutBounds): Int = maxOf(0, minOf(bottom, clip.bottom) - maxOf(top, clip.top))
}

/**
 * One visible node, copied out of the accessibility tree so adapters never touch live nodes.
 * [label] is the node's text or content description, held in memory for one scan only and
 * never written to storage, logs or diagnostics.
 */
class ScreenNode(
    val index: Int,
    val parent: Int,
    val depth: Int,
    val id: String?,
    val cls: String,
    val label: String?,
    val bounds: LayoutBounds,
    val scrollable: Boolean,
    val clickable: Boolean,
    val selected: Boolean,
    val key: Int,
    val range: Range?,
) {
    class Range(val min: Float, val max: Float, val current: Float)
    val shortClass: String get() = cls.substringAfterLast('.')
}

class ScreenSnapshot(
    val packageName: String,
    val windowId: Int,
    val screen: LayoutBounds,
    val nodes: List<ScreenNode>,
    val complete: Boolean,
    val typing: Boolean,
) {
    private val byId: Map<String, List<ScreenNode>> by lazy { nodes.filter { it.id != null }.groupBy { it.id!! } }
    fun withId(id: String): List<ScreenNode> = byId[id] ?: emptyList()
    fun has(id: String): Boolean = byId.containsKey(id)
    fun hasAny(ids: Collection<String>): Boolean = ids.any { byId.containsKey(it) }

    fun isDescendant(node: ScreenNode, ancestor: ScreenNode): Boolean {
        var cursor = node.parent
        while (cursor >= 0) {
            if (cursor == ancestor.index) return true
            cursor = nodes[cursor].parent
        }
        return false
    }

    fun descendants(ancestor: ScreenNode): List<ScreenNode> = nodes.filter { it.index > ancestor.index && isDescendant(it, ancestor) }
    fun within(area: LayoutBounds): List<ScreenNode> = nodes.filter { area.contains(it.bounds) }
}
