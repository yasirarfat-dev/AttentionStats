package org.attentionstats.detection.social.youtube

import org.attentionstats.detection.AdapterFrame
import org.attentionstats.detection.ScreenSnapshot
import org.attentionstats.detection.ShortFormAdapter
import org.attentionstats.detection.ScreenNode
import org.attentionstats.detection.shared.Sponsored
import org.attentionstats.detection.shared.ViewerLocator
import org.attentionstats.reels.PageCategory
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface

/** Shorts player from any entry (tab, Home shelf, search, channel, links). */
object YouTubeAdapter : ShortFormAdapter {
    override val app = ReelApp.YOUTUBE
    private val pagerIds = setOf("reel_recycler", "reel_watch_pager")

    override fun read(snapshot: ScreenSnapshot): AdapterFrame {
        if (!snapshot.complete) return AdapterFrame(Surface.UNKNOWN)
        if (snapshot.typing) return AdapterFrame.TYPING
        val viewer = ViewerLocator.locate(snapshot, pagerIds) ?: nonVideoPage(snapshot) ?: return AdapterFrame.OUTSIDE
        return ViewerLocator.frame(snapshot, viewer, "youtube.shorts") { nodes -> category(nodes, snapshot.screen.width) }
            .withSkippableAd(snapshot)
    }

    /**
     * Shorts ads and promotions without a player use a full-page `non_video_page_element_container`
     * inside the Shorts pager and carry no action rail, so the geometry test cannot see them.
     */
    private fun nonVideoPage(snapshot: ScreenSnapshot): ViewerLocator.Viewer? {
        val pager = snapshot.withId("reel_recycler").firstOrNull {
            it.bounds.width >= snapshot.screen.width * 0.9 && it.bounds.height >= snapshot.screen.height * 0.7
        } ?: return null
        val page = snapshot.withId("non_video_page_element_container").firstOrNull {
            snapshot.isDescendant(it, pager) && it.bounds.width >= pager.bounds.width * 0.95 &&
                it.bounds.visibleHeightWithin(pager.bounds) >= pager.bounds.height * 0.86
        } ?: return null
        return ViewerLocator.Viewer(pager, page, 0)
    }

    /** Community posts appear between Shorts in the same pager: images are photos, text-only posts are posts. */
    private fun category(nodes: List<ScreenNode>, width: Int): PageCategory = when {
        nodes.any(Sponsored::node) -> PageCategory.AD
        // A non-video page that is not a recognised ad is never a short video.
        nodes.any { it.id == "non_video_page_element_container" } -> PageCategory.POST
        nodes.none { it.id == "posts_content_root" } -> PageCategory.REEL
        nodes.any { it.shortClass == "ImageView" && it.bounds.width >= width * 0.6 && it.bounds.height >= width * 0.3 } ->
            PageCategory.PHOTO
        else -> PageCategory.POST
    }
}
