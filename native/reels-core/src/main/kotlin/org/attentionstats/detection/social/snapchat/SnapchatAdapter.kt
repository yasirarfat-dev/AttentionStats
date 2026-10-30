package org.attentionstats.detection.social.snapchat

import org.attentionstats.detection.AdapterFrame
import org.attentionstats.detection.ScreenSnapshot
import org.attentionstats.detection.ShortFormAdapter
import org.attentionstats.detection.shared.ViewerLocator
import org.attentionstats.reels.ReelApp

/** Spotlight. Snapchat obfuscates resource names, so the geometry locator does the work. */
object SnapchatAdapter : ShortFormAdapter {
    override val app = ReelApp.SNAPCHAT
    private val pagerIds = setOf("spotlight_container", "spotlight_view_pager")

    override fun read(snapshot: ScreenSnapshot): AdapterFrame {
        if (snapshot.typing) return AdapterFrame.TYPING
        val viewer = ViewerLocator.locate(snapshot, pagerIds) ?: return AdapterFrame.OUTSIDE
        return ViewerLocator.frame(snapshot, viewer, "snapchat.spotlight")
    }
}
