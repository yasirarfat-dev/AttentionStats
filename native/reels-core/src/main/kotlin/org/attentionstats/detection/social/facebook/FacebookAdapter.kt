package org.attentionstats.detection.social.facebook

import org.attentionstats.detection.AdapterFrame
import org.attentionstats.detection.ScreenSnapshot
import org.attentionstats.detection.ShortFormAdapter
import org.attentionstats.detection.shared.FeedLocator
import org.attentionstats.detection.shared.Sponsored
import org.attentionstats.detection.shared.ViewerLocator
import org.attentionstats.reels.ReelApp

/**
 * Facebook strips view IDs, so detection is structural: the Reels viewer is found by its
 * full-screen pager and action rail (Reels tab, the Home reels row, a tapped feed video,
 * Video tab, pages, groups); inline feed videos are large media blocks carrying a sound
 * toggle in a corner, or the player's own "Reel"/"Video" label.
 */
object FacebookAdapter : ShortFormAdapter {
    override val app = ReelApp.FACEBOOK
    private val videoLabels = setOf("reel", "video", "play video", "pause video", "play current reel", "watch reel",
        "live video")
    /** Post chrome (reactions, comments, share) lives outside media blocks; its presence means a whole post or row. */
    private val chrome = Regex("^(like|comment|share|react|send|write a comment)", RegexOption.IGNORE_CASE)

    override fun read(snapshot: ScreenSnapshot): AdapterFrame {
        if (snapshot.typing) return AdapterFrame.TYPING
        ViewerLocator.locate(snapshot)?.let { return ViewerLocator.frame(snapshot, it, "facebook.viewer") }
        val list = snapshot.withId("list").firstOrNull { it.scrollable } ?: FeedLocator.list(snapshot)
            ?: return AdapterFrame.OUTSIDE
        val screen = snapshot.screen
        val large = snapshot.nodes.filter { node ->
            list.bounds.contains(node.bounds) && node.bounds.width >= screen.width * 0.8 &&
                node.bounds.height >= screen.height * 0.18 && node.bounds.height < list.bounds.height * 0.95
        }
        val videos = large.filter { node ->
            val inside = snapshot.descendants(node)
            inside.none { chrome.containsMatchIn(it.label ?: "") } && (node.label?.trim()?.lowercase() in videoLabels ||
                inside.any { it.label?.trim()?.lowercase() in videoLabels && it.bounds.width < screen.width / 3 } ||
                FeedLocator.hasCornerToggle(snapshot, node))
        }
        val innermost = videos.filter { node -> videos.none { it !== node && snapshot.isDescendant(it, node) } }
        if (innermost.isEmpty()) return AdapterFrame.OUTSIDE.copy(method = "facebook.feed")
        val media = innermost.map { node ->
            val header = FeedLocator.headerBand(snapshot, list.bounds, node.bounds)
            val ad = header.any(Sponsored::node) || snapshot.nodes.any {
                Sponsored.label(it.label) && it.bounds.bottom <= node.bounds.top + 4 &&
                    it.bounds.top >= node.bounds.top - screen.height / 3
            }
            FeedLocator.Media(node, true, ad, header)
        }
        return FeedLocator.frame(snapshot, list, media, "facebook.feed", countPhotos = false)
    }
}
