package org.attentionstats.detection.social.tiktok

import org.attentionstats.detection.AdapterFrame
import org.attentionstats.detection.ScreenSnapshot
import org.attentionstats.detection.ShortFormAdapter
import org.attentionstats.detection.shared.ViewerLocator
import org.attentionstats.reels.ReelApp

/** For You, Following, Friends, search, profiles and shares all use the same vertical pager. */
object TikTokAdapter : ShortFormAdapter {
    override val app = ReelApp.TIKTOK
    private val pagerIds = setOf("viewpager")

    override fun read(snapshot: ScreenSnapshot): AdapterFrame {
        if (snapshot.typing) return AdapterFrame.TYPING
        val viewer = ViewerLocator.locate(snapshot, pagerIds) ?: return AdapterFrame.OUTSIDE
        return ViewerLocator.frame(snapshot, viewer, "tiktok.feed")
    }
}
