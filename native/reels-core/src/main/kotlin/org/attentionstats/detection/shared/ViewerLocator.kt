package org.attentionstats.detection.shared

import org.attentionstats.detection.AdapterFrame
import org.attentionstats.detection.LayoutBounds
import org.attentionstats.detection.PlaybackTimeParser
import org.attentionstats.detection.ScreenNode
import org.attentionstats.detection.ScreenSnapshot
import org.attentionstats.reels.PageCategory
import org.attentionstats.reels.ReelRoute
import org.attentionstats.reels.Surface

/**
 * Finds a full-screen vertical short-video viewer however the user reached it (dedicated tab,
 * a feed tap, a profile grid, a share link, search results). Every such viewer is a pager
 * that fills the screen, shows one page at a time, and carries a stacked action rail on the
 * right edge. Known pager IDs short-circuit the geometry test where an app exposes them.
 */
object ViewerLocator {
    data class Viewer(val pager: ScreenNode, val page: ScreenNode?, val rail: Int)

    private fun pagerLike(node: ScreenNode): Boolean = node.scrollable || node.cls.contains("RecyclerView") ||
        node.cls.contains("ViewPager") || node.id?.contains("pager", ignoreCase = true) == true ||
        node.id?.contains("recycler", ignoreCase = true) == true

    fun locate(snapshot: ScreenSnapshot, pagerIds: Set<String> = emptySet()): Viewer? {
        val screen = snapshot.screen
        if (screen.empty || screen.height < screen.width) return null // Portrait viewers only.
        val candidates = snapshot.nodes.filter { node ->
            node.bounds.width >= screen.width * 0.9 && node.bounds.height >= screen.height * 0.7 &&
                ((node.id != null && node.id in pagerIds) || pagerLike(node))
        }.sortedWith(compareByDescending<ScreenNode> { it.id != null && it.id in pagerIds }.thenByDescending { it.depth })
        for (pager in candidates) {
            val known = pager.id != null && pager.id in pagerIds
            val page = pageOf(snapshot, pager)
            val rail = railCount(snapshot, page?.bounds ?: pager.bounds)
            if (known && rail < 2) continue
            if (!known && (page == null || rail < 3 || containsFeed(snapshot, page))) continue
            return Viewer(pager, page, rail)
        }
        return null
    }

    /** The single page filling the pager; null while two pages are mid-transition. */
    fun pageOf(snapshot: ScreenSnapshot, pager: ScreenNode): ScreenNode? {
        val clip = pager.bounds
        return snapshot.nodes.asSequence()
            .filter { it.index > pager.index && it.bounds.width >= clip.width * 0.95 &&
                it.bounds.visibleHeightWithin(clip) >= clip.height * 0.86 && snapshot.isDescendant(it, pager) }
            .minByOrNull { it.depth }
    }

    /**
     * Length of the longest contiguous vertical stack of small clickable controls (like, comment,
     * share...) hugging the right edge. Feed posts also have right-edge controls, but scattered.
     */
    fun railCount(snapshot: ScreenSnapshot, area: LayoutBounds): Int {
        val screen = snapshot.screen
        val controls = snapshot.nodes.filter { node ->
            node.clickable && node.bounds.centerX >= screen.left + screen.width * 0.78 &&
                node.bounds.height in 1..(screen.height / 5) && node.bounds.width <= screen.width / 4 &&
                node.bounds.centerY in area.top..area.bottom
        }.sortedBy { it.bounds.top }
        val gap = screen.height * 0.08
        val rowStep = screen.height / 40
        var best = 0
        var run = 0
        var bottom: Int? = null
        var rowTop: Int? = null
        for (control in controls) {
            if (bottom == null || control.bounds.top - bottom > gap) { run = 0; rowTop = null }
            if (rowTop == null || control.bounds.top - rowTop >= rowStep) { run++; rowTop = control.bounds.top }
            bottom = maxOf(bottom ?: control.bounds.bottom, control.bounds.bottom)
            best = maxOf(best, run)
        }
        return best
    }

    /** Feeds also fill the screen; they are tall vertical lists showing several partial-height rows at once. */
    fun containsFeed(snapshot: ScreenSnapshot, page: ScreenNode): Boolean = snapshot.nodes.any { list ->
        list.scrollable && list.bounds.height > list.bounds.width && list.bounds.height >= snapshot.screen.height / 2 &&
            page.bounds.contains(list.bounds) &&
            snapshot.nodes.count { it.parent == list.index && it.bounds.height in 1 until (list.bounds.height * 0.85).toInt() } >= 2
    }

    /** Metadata of the focused page, excluding the action rail and the top tab bar. */
    fun metadata(snapshot: ScreenSnapshot, page: LayoutBounds): List<ScreenNode> {
        val screen = snapshot.screen
        val topBar = screen.top + screen.height * 0.12
        val rail = screen.left + screen.width * 0.8
        return snapshot.within(page).filter { node ->
            node.label != null && ((node.bounds.centerX < rail && node.bounds.top >= topBar &&
                node.bounds.height < page.height * 0.5) ||
                (node.bounds.height >= page.height * 0.5 && node.bounds.width >= page.width * 0.9)) // "Reel by ..."
        }
    }

    /** Normalized playback position from a seek/progress bar in the lower part of the page. */
    fun progress(snapshot: ScreenSnapshot, page: LayoutBounds): Double? {
        val bars = snapshot.nodes.filter { node ->
            node.bounds.width >= page.width * 0.7 && node.bounds.top >= page.top + page.height * 0.6 &&
                node.bounds.bottom <= page.bottom + page.height * 0.2
        }
        bars.firstNotNullOfOrNull { node ->
            node.range?.takeIf { it.max > it.min && it.current in it.min..it.max }
                ?.let { ((it.current - it.min) / (it.max - it.min)).toDouble() }
        }?.let { return it }
        return bars.firstNotNullOfOrNull { node ->
            PlaybackTimeParser.parse(node.label)?.let { (at, total) -> at.toDouble() / total }
        }
    }

    fun defaultCategory(nodes: List<ScreenNode>): PageCategory =
        if (nodes.any(Sponsored::node)) PageCategory.AD else PageCategory.REEL

    /** Standard frame for a located viewer. */
    fun frame(snapshot: ScreenSnapshot, viewer: Viewer, method: String,
        category: (List<ScreenNode>) -> PageCategory = ::defaultCategory): AdapterFrame {
        val page = viewer.page ?: return AdapterFrame(Surface.REELS, ReelRoute.VIEWER, method = method)
        val print = Fingerprint.of(metadata(snapshot, page.bounds))
        return AdapterFrame(Surface.REELS, ReelRoute.VIEWER, print?.identity, print?.keys ?: emptySet(),
            category(snapshot.within(page.bounds)), page.bounds, null, progress(snapshot, page.bounds), method)
    }
}
