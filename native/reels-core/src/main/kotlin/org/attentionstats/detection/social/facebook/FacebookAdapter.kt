package org.attentionstats.detection.social.facebook

import org.attentionstats.detection.AdapterFrame
import org.attentionstats.detection.ScreenSnapshot
import org.attentionstats.detection.ScreenNode
import org.attentionstats.detection.LayoutBounds
import org.attentionstats.detection.ShortFormAdapter
import org.attentionstats.detection.shared.FeedLocator
import org.attentionstats.detection.shared.Sponsored
import org.attentionstats.detection.shared.ViewerLocator
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface
import org.attentionstats.reels.ReelRoute

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
        if (!snapshot.complete) return AdapterFrame(Surface.UNKNOWN)
        if (snapshot.typing) return AdapterFrame.TYPING
        if (snapshot.nodes.any { it.selected && it.label?.startsWith("Marketplace,", ignoreCase = true) == true }) return AdapterFrame.OUTSIDE
        val dedicatedTab = snapshot.nodes.any { node -> node.selected && node.clickable &&
            node.label?.matches(Regex("Reels, [Tt]ab [0-9]+ of [0-9]+(?:,.*)?")) == true }
        val knownFeed = snapshot.withId("list").firstOrNull { it.scrollable }
        if (knownFeed == null || dedicatedTab) ViewerLocator.locate(snapshot)?.let { viewer ->
            val frame = ViewerLocator.frame(snapshot, viewer, "facebook.viewer")
            val page = viewer.page ?: return frame
            // Facebook's standalone-Reels ad information pill: small clickable native Button,
            // bottom left of a settled full-page viewer, with its own exact Ad label child.
            // Broad sponsored classification/captions never authorize immediate skipping.
            val markers = snapshot.within(page.bounds).filter { node ->
                node.cls == "android.widget.Button" && node.clickable && node.label == "Ad" &&
                    node.bounds.left < page.bounds.left + page.bounds.width * .2 &&
                    node.bounds.top > page.bounds.top + page.bounds.height * .8 &&
                    node.bounds.width in 1..(page.bounds.width / 4) &&
                    node.bounds.height in 1..(page.bounds.height / 12) &&
                    snapshot.descendants(node).any { child -> child.cls == "android.view.ViewGroup" &&
                        (child.label == "Ad" || child.label == null) && !child.clickable &&
                        node.bounds.contains(child.bounds) && child.bounds.width in 1..node.bounds.width &&
                        child.bounds.height in 1..node.bounds.height }
            }
            // Full-screen accessibility wrappers share generic labels across unrelated ads.
            // Use bounded lower-page publisher/caption anchors for action continuity instead.
            val anchors = snapshot.within(page.bounds).filter { node -> node.label != null &&
                node.bounds.left < page.bounds.left + page.bounds.width * .25 &&
                node.bounds.top > page.bounds.top + page.bounds.height * .6 &&
                node.bounds.width >= page.bounds.width * .15 && node.bounds.height <= page.bounds.height * .2 }
            val keys = org.attentionstats.detection.shared.Fingerprint.of(anchors)?.keys ?: emptySet()
            return frame.copy(standaloneAd = dedicatedTab && frame.category == org.attentionstats.reels.PageCategory.AD &&
                markers.size == 1 && keys.size >= 2, standaloneAdKeys = keys)
        }
        // Reels temporarily exposes list-shaped wrappers while its pager moves. A selected
        // Reels tab without a settled viewer is unknown, not evidence of the Home feed.
        // This can withhold confirmation until the existing deadline, never authorize input.
        if (dedicatedTab) return AdapterFrame(Surface.UNKNOWN, ReelRoute.VIEWER, method = "facebook.viewer.transition")
        val list = knownFeed ?: FeedLocator.list(snapshot)
            ?: return AdapterFrame.OUTSIDE
        val screen = snapshot.screen
        val large = snapshot.nodes.filter { node ->
            list.bounds.contains(node.bounds) && node.bounds.width >= screen.width * 0.8 &&
                node.bounds.height >= screen.height * 0.18 && node.bounds.height < list.bounds.height * 0.95
        }
        fun videoLabel(node: ScreenNode) = node.label?.trim()?.lowercase() in videoLabels
        val positiveVideos = large.filter { node -> videoLabel(node) || snapshot.descendants(node).any {
            videoLabel(it) && it.bounds.width < screen.width / 3
        } }
        val photoLeaves = snapshot.nodes.filter { node ->
            val box = node.bounds
            (node.cls.endsWith("ImageView") ||
                (node.cls.endsWith("Button") && node.label?.trim()?.lowercase()?.let {
                    it == "photo" || it.startsWith("photo ") || it.startsWith("photo:")
                } == true) ||
                (node.cls.endsWith("TextureView") && node.label?.trim()?.lowercase() == "360 photo")) && !node.label.isNullOrBlank() &&
                box.width >= screen.width * 0.18 && box.height >= screen.height * 0.06 &&
                box.bottom > list.bounds.top && box.top < list.bounds.bottom &&
                positiveVideos.none { node === it || snapshot.isDescendant(node, it) } &&
                (node.label.trim().lowercase() == "photo" || snapshot.nodes.none { parent ->
                    parent.scrollable && parent.bounds.width > parent.bounds.height && snapshot.isDescendant(node, parent)
                })
        }
        // Albums use several narrow tiles. Group their nearest full-width media ancestor;
        // never group across the feed list or admit avatars as independent photos.
        val grouped = photoLeaves.groupBy { photo ->
            var parent = photo.parent
            var group = photo
            while (parent >= 0 && parent != list.index) {
                val ancestor = snapshot.nodes[parent]
                if (ancestor.scrollable || ancestor.bounds.height >= list.bounds.height * 1.8) break
                if (ancestor.bounds.width >= screen.width * 0.8 && photoLeaves.count { snapshot.isDescendant(it, ancestor) } > 1) {
                    group = ancestor; break
                }
                parent = ancestor.parent
            }
            group
        }
        val photos = grouped.map { (parent, leaves) ->
            if (leaves.size == 1) leaves.first() else ScreenNode(parent.index, parent.parent, parent.depth, parent.id,
                parent.cls, null, LayoutBounds(leaves.minOf { it.bounds.left }, leaves.minOf { it.bounds.top },
                    leaves.maxOf { it.bounds.right }, leaves.maxOf { it.bounds.bottom }), false, parent.clickable, parent.selected, parent.key, null)
        }
        val videos = large.filter { node ->
            val inside = snapshot.descendants(node)
            inside.none { chrome.containsMatchIn(it.label ?: "") } && (node.label?.trim()?.lowercase() in videoLabels ||
                inside.any { it.label?.trim()?.lowercase() in videoLabels && it.bounds.width < screen.width / 3 } ||
                (photos.none { snapshot.isDescendant(it, node) || it.index == node.index || node.bounds.contains(it.bounds) } && FeedLocator.hasCornerToggle(snapshot, node)))
        }
        val markedVideos = videos.filter(::videoLabel)
        val videoRegions = videos.filter { node -> markedVideos.none {
            it !== node && (snapshot.isDescendant(node, it) || node.bounds.contains(it.bounds) || it.bounds.contains(node.bounds))
        } }
        val innermost = videoRegions.filter { node -> videoRegions.none { it !== node && snapshot.isDescendant(it, node) } }
        if (innermost.isEmpty() && photos.isEmpty()) return AdapterFrame(Surface.OUTSIDE, ReelRoute.HOME, method = "facebook.feed")
        val media = (innermost + photos.filter { photo -> innermost.none { snapshot.isDescendant(photo, it) } }).map { node ->
            val header = FeedLocator.headerBand(snapshot, list.bounds, node.bounds)
            val ad = header.any(Sponsored::node) || snapshot.nodes.any {
                Sponsored.label(it.label) && it.bounds.bottom <= node.bounds.top + 4 &&
                    it.bounds.top >= node.bounds.top - screen.height / 3
            }
            FeedLocator.Media(node, node !in photos, ad, header)
        }
        // Wait for a video to cross the focus line or fit fully. A bottom-edge preview
        // can replace its header/controls on playback before it reaches the viewer's focus.
        val result = FeedLocator.frame(snapshot, list, media, "facebook.feed", countPhotos = true, allowPartialVideos = false)
        return if (result.surface == Surface.EXCLUDED) result.copy(surface = Surface.OUTSIDE) else result
    }
}
