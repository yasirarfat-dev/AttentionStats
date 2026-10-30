package org.attentionstats.detection.shared

import org.attentionstats.detection.AdapterFrame
import org.attentionstats.detection.LayoutBounds
import org.attentionstats.detection.ScreenNode
import org.attentionstats.detection.ScreenSnapshot
import org.attentionstats.reels.PageCategory
import org.attentionstats.reels.ReelRoute
import org.attentionstats.reels.Surface

/**
 * Inline media inside a vertically scrolling feed (Home, profile, groups, pages).
 * An adapter supplies candidate media nodes; this picks the one the user is looking at
 * (the item crossing the middle of the list, or the most visible one) and fingerprints it
 * together with its post header.
 */
object FeedLocator {
    data class Media(val node: ScreenNode, val video: Boolean, val ad: Boolean = false, val header: List<ScreenNode> = emptyList())

    /** The scrolling list that hosts the feed: tallest vertical scrollable. */
    fun list(snapshot: ScreenSnapshot): ScreenNode? = snapshot.nodes.filter {
        it.scrollable && it.bounds.height > it.bounds.width && it.bounds.width >= snapshot.screen.width * 0.9
    }.maxByOrNull { it.bounds.height }

    fun focused(list: LayoutBounds, media: List<Media>): Media? {
        if (media.isEmpty()) return null
        val middle = (list.top + list.bottom) / 2
        media.firstOrNull { it.node.bounds.top <= middle && it.node.bounds.bottom >= middle }?.let { return it }
        // Bounds are clipped to the screen: an item not touching the list edges is fully visible.
        return media.filter { item ->
            val box = item.node.bounds
            (box.top > list.top + 2 && box.bottom < list.bottom - 2) || box.visibleHeightWithin(list) >= list.height * 0.4
        }.minByOrNull { kotlin.math.abs(it.node.bounds.centerY - middle) }
    }

    /** Labelled nodes in the band just above the media (author/time row) within the list. */
    fun headerBand(snapshot: ScreenSnapshot, list: LayoutBounds, media: LayoutBounds): List<ScreenNode> {
        val screen = snapshot.screen
        val top = maxOf(list.top, media.top - (screen.height * 0.2).toInt())
        return snapshot.nodes.filter { node ->
            node.label != null && node.bounds.top >= top && node.bounds.bottom <= media.top + 4 &&
                node.bounds.centerX < screen.left + screen.width * 0.8
        }
    }

    /** A small clickable control in the media's bottom corner: the sound toggle every video carries. */
    fun hasCornerToggle(snapshot: ScreenSnapshot, media: ScreenNode): Boolean {
        val box = media.bounds
        val size = (snapshot.screen.width * 0.16).toInt()
        return snapshot.nodes.any { node ->
            node.index != media.index && node.clickable && box.contains(node.bounds) &&
                node.bounds.width in 1..size && node.bounds.height in 1..size &&
                node.bounds.top >= box.bottom - box.height * 0.3 &&
                (node.bounds.left >= box.right - box.width * 0.25 || node.bounds.right <= box.left + box.width * 0.25)
        }
    }

    fun frame(snapshot: ScreenSnapshot, list: ScreenNode, media: List<Media>, method: String, countPhotos: Boolean): AdapterFrame {
        val item = focused(list.bounds, media) ?: return AdapterFrame(Surface.EXCLUDED, ReelRoute.HOME, method = method)
        if (!item.video && !countPhotos) return AdapterFrame(Surface.EXCLUDED, ReelRoute.HOME, method = method)
        val labelled = item.header + snapshot.within(item.node.bounds).filter { it.label != null }
        val print = Fingerprint.of(labelled)
        val category = when { item.ad -> PageCategory.AD; item.video -> PageCategory.REEL; else -> PageCategory.PHOTO }
        // Without any visible metadata, fall back to the hosting view; layout continuity keeps it stable.
        val identity = print?.identity ?: (item.node.key.toLong() * 7919 + snapshot.windowId)
        return AdapterFrame(Surface.REELS, ReelRoute.HOME, identity, print?.keys ?: emptySet(), category,
            item.node.bounds, item.node.key, null, method)
    }
}
