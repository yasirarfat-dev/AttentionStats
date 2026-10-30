package org.attentionstats.detection.social.youtube

import org.attentionstats.detection.AdapterFrame
import org.attentionstats.detection.ScreenSnapshot
import org.attentionstats.detection.ShortFormAdapter
import org.attentionstats.detection.shared.ViewerLocator
import org.attentionstats.reels.ReelApp

/** Shorts player from any entry (tab, Home shelf, search, channel, links). */
object YouTubeAdapter : ShortFormAdapter {
    override val app = ReelApp.YOUTUBE
    private val pagerIds = setOf("reel_recycler", "reel_watch_pager")

    override fun read(snapshot: ScreenSnapshot): AdapterFrame {
        if (snapshot.typing) return AdapterFrame.TYPING
        val viewer = ViewerLocator.locate(snapshot, pagerIds) ?: return AdapterFrame.OUTSIDE
        return ViewerLocator.frame(snapshot, viewer, "youtube.shorts")
    }
}
