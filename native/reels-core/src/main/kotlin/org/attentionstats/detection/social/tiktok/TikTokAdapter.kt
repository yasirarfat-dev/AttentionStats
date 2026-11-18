package org.attentionstats.detection.social.tiktok

import org.attentionstats.detection.AdapterFrame
import org.attentionstats.detection.ScreenSnapshot
import org.attentionstats.detection.ShortFormAdapter
import org.attentionstats.detection.shared.ViewerLocator
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface
import org.attentionstats.reels.PageCategory
import org.attentionstats.detection.shared.Sponsored

/** For You, Following, Friends, search, profiles and shares all use the same vertical pager. */
object TikTokAdapter : ShortFormAdapter {
    override val app = ReelApp.TIKTOK
    private val pagerIds = setOf("viewpager")
    private val photoLabel = Regex("^(photo(s)?|image(s)?)( \\d+ (of|/) \\d+)?$", RegexOption.IGNORE_CASE)

    override fun read(snapshot: ScreenSnapshot): AdapterFrame {
        if (!snapshot.complete) return AdapterFrame(Surface.UNKNOWN)
        if (snapshot.typing) return AdapterFrame.TYPING
        val viewer = ViewerLocator.locate(snapshot, pagerIds) ?: return AdapterFrame.OUTSIDE
        return ViewerLocator.frame(snapshot, viewer, "tiktok.feed") { nodes ->
            when {
                nodes.any(Sponsored::node) -> PageCategory.AD
                nodes.any { node -> node.label?.trim()?.let(photoLabel::matches) == true &&
                    // A wide pager label, or the compact "Photo" badge TikTok 47.x shows beside the author.
                    (node.bounds.width > snapshot.screen.width / 2 || node.id == "tv_label") } -> PageCategory.PHOTO
                else -> PageCategory.REEL
            }
        }.withSkippableAd(snapshot)
    }
}
