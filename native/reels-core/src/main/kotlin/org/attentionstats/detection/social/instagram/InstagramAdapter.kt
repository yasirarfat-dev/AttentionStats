package org.attentionstats.detection.social.instagram

import org.attentionstats.detection.AdapterFrame
import org.attentionstats.detection.ScreenNode
import org.attentionstats.detection.ScreenSnapshot
import org.attentionstats.detection.ShortFormAdapter
import org.attentionstats.detection.shared.FeedLocator
import org.attentionstats.detection.shared.Sponsored
import org.attentionstats.detection.shared.ViewerLocator
import org.attentionstats.reels.PageCategory
import org.attentionstats.reels.ReelApp

/**
 * Reels viewer (tab, Home taps, Explore, profiles, shares) via its clips pager or geometry;
 * Home/profile feed posts via the long-standing row_feed/media IDs.
 */
object InstagramAdapter : ShortFormAdapter {
    override val app = ReelApp.INSTAGRAM
    private val pagerIds = setOf("clips_viewer_view_pager")
    private val mediaIds = setOf("video_container", "media_group", "carousel_media_group", "carousel_video_media_group",
        "row_feed_photo_imageview", "carousel_viewpager")
    private val videoIds = setOf("video_container", "video_states", "carousel_video_media_group", "clips_video_container")
    private val videoLabel = Regex("^(reel|video|clip) by ", RegexOption.IGNORE_CASE)

    override fun read(snapshot: ScreenSnapshot): AdapterFrame {
        if (snapshot.typing) return AdapterFrame.TYPING
        ViewerLocator.locate(snapshot, pagerIds)?.let { viewer ->
            return ViewerLocator.frame(snapshot, viewer, "instagram.viewer", ::viewerCategory)
        }
        if (!snapshot.has("row_feed_profile_header") && !snapshot.has("media_group")) return AdapterFrame.OUTSIDE
        val list = snapshot.withId("list").firstOrNull { it.scrollable } ?: FeedLocator.list(snapshot)
            ?: return AdapterFrame.OUTSIDE
        val candidates = snapshot.nodes.filter {
            it.id in mediaIds && list.bounds.contains(it.bounds) && it.bounds.width >= snapshot.screen.width * 0.8
        }
        val outermost = candidates.filter { node -> candidates.none { it !== node && snapshot.isDescendant(node, it) } }
        val media = outermost.map { node ->
            val inside = listOf(node) + snapshot.descendants(node)
            val header = FeedLocator.headerBand(snapshot, list.bounds, node.bounds)
            val ad = header.any(Sponsored::node) || snapshot.withId("cta_container").any {
                it.bounds.top in (node.bounds.bottom - 8)..(node.bounds.bottom + 60)
            }
            val video = inside.any { it.id in videoIds || videoLabel.containsMatchIn(it.label ?: "") }
            FeedLocator.Media(node, video, ad, header)
        }
        return FeedLocator.frame(snapshot, list, media, "instagram.feed", countPhotos = true)
    }

    private fun viewerCategory(nodes: List<ScreenNode>): PageCategory = when {
        nodes.any(Sponsored::node) -> PageCategory.AD
        nodes.none { it.id in videoIds || it.id == "clips_viewer_video_layout" } &&
            nodes.any { it.id?.contains("carousel") == true || it.id?.contains("image") == true } -> PageCategory.PHOTO
        else -> PageCategory.REEL
    }
}
