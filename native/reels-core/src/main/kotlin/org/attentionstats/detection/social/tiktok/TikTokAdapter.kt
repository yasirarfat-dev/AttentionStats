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

    override fun read(snapshot: ScreenSnapshot): AdapterFrame {
        if (!snapshot.complete) return AdapterFrame(Surface.UNKNOWN)
        if (snapshot.typing) return AdapterFrame.TYPING
        val viewer = ViewerLocator.locate(snapshot, pagerIds) ?: return AdapterFrame.OUTSIDE
        return ViewerLocator.frame(snapshot, viewer, "tiktok.feed") { nodes ->
            when {
                nodes.any(Sponsored::node) -> PageCategory.AD
                nodes.any { it.label?.trim()?.let { label ->
                    Regex("^(photo(s)?|image(s)?)( \\d+ (of|/) \\d+)?$", RegexOption.IGNORE_CASE).matches(label)
                } == true && it.bounds.width > snapshot.screen.width / 2 } -> PageCategory.PHOTO
                else -> PageCategory.REEL
            }
        }
    }
}
