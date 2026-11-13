package org.attentionstats.detection

import org.attentionstats.reels.PageCategory
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.ReelRoute
import org.attentionstats.reels.Surface
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Real-device screen layouts (sanitized with tools/sanitize_fixture.py) run through the same
 * adapters the accessibility service uses. To add a case: dump the screen with
 * `adb shell uiautomator dump`, sanitize it into fixtures/<app>/<case>.xml and add an expectation.
 */
object FixtureTests {
    data class Expect(val surface: Surface, val route: ReelRoute? = null, val category: PageCategory? = null,
        val identified: Boolean = surface == Surface.REELS, val mediaKind: String? = null,
        val standaloneAd: Boolean? = null)

    private val expectations: Map<String, Expect> = mapOf(
        "facebook/standalone_reels_video_ad.xml" to Expect(Surface.REELS, ReelRoute.VIEWER, PageCategory.AD, standaloneAd = true),
        "facebook/standalone_reels_video_ad_blank_child.xml" to Expect(Surface.REELS, ReelRoute.VIEWER, PageCategory.AD, standaloneAd = true),
        "facebook/feed_photo_button_ad.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.AD, mediaKind = "photo"),
        "facebook/feed_photo_button_album.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.PHOTO, mediaKind = "photo"),
        "facebook/feed_panorama_photo.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.PHOTO, mediaKind = "photo"),
        "facebook/feed_video_entering.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.PHOTO, mediaKind = "photo"),
        "facebook/feed_video_focused.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.REEL, mediaKind = "video"),
        "facebook/feed_image_ad.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.AD, mediaKind = "photo"),
        "facebook/feed_clipped_photo.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.PHOTO, mediaKind = "photo"),
        "facebook/feed_portrait_photo.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.PHOTO, mediaKind = "photo"),
        "facebook/feed_music_photo.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.PHOTO, mediaKind = "photo"),
        "facebook/feed_square_photo.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.PHOTO, mediaKind = "photo"),
        "facebook/feed_reels_row_only.xml" to Expect(Surface.OUTSIDE, ReelRoute.HOME, identified = false),
        "facebook/feed_tall_video_ad.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.AD, mediaKind = "video"),
        "snapchat/stories.xml" to Expect(Surface.OUTSIDE),
        "snapchat/story_viewer.xml" to Expect(Surface.OUTSIDE),
        "snapchat/spotlight.xml" to Expect(Surface.REELS, ReelRoute.VIEWER, PageCategory.REEL),
        "snapchat/spotlight_next.xml" to Expect(Surface.REELS, ReelRoute.VIEWER, PageCategory.REEL),
        "instagram/home_photo.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.AD),
        "instagram/home_photo_centered.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.AD),
        "instagram/reels_tab.xml" to Expect(Surface.REELS, ReelRoute.VIEWER, PageCategory.REEL),
        "instagram/home_feed_ad_video.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.AD),
        // Historical "no video" negative contains a positively labelled image advertisement.
        "facebook/home_no_video.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.AD, mediaKind = "photo"),
        "facebook/home_reels_row.xml" to Expect(Surface.OUTSIDE, identified = false),
        "facebook/home_feed_reel.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.REEL),
        "facebook/reel_opened_from_home_row.xml" to Expect(Surface.REELS, ReelRoute.VIEWER, PageCategory.REEL),
        "youtube/shorts_tab.xml" to Expect(Surface.REELS, ReelRoute.VIEWER, PageCategory.REEL),
        "tiktok/for_you.xml" to Expect(Surface.REELS, ReelRoute.VIEWER, PageCategory.REEL),
        "instagram/explore_grid.xml" to Expect(Surface.OUTSIDE),
                "instagram/explore_grid_2.xml" to Expect(Surface.OUTSIDE),
        "instagram/profile.xml" to Expect(Surface.OUTSIDE),
        "instagram/direct_inbox.xml" to Expect(Surface.OUTSIDE),
        "instagram/story.xml" to Expect(Surface.OUTSIDE),
        "instagram/home_suggestion_carousel.xml" to Expect(Surface.EXCLUDED, ReelRoute.HOME),
        "facebook/home_feed_2.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.PHOTO),
        // Historical fixture is a duplicate Home photo (Home tab selected), despite its filename.
        "facebook/marketplace.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.PHOTO),
        "youtube/home_feed.xml" to Expect(Surface.OUTSIDE),
        "youtube/short_opened_from_home.xml" to Expect(Surface.REELS, ReelRoute.VIEWER, PageCategory.REEL),
        "youtube/watch_page.xml" to Expect(Surface.OUTSIDE),
        "tiktok/for_you_2.xml" to Expect(Surface.REELS, ReelRoute.VIEWER),
        "tiktok/for_you_3.xml" to Expect(Surface.REELS, ReelRoute.VIEWER),
        "instagram/home_feed_video.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.REEL),
        "instagram/reel_from_explore.xml" to Expect(Surface.REELS, ReelRoute.VIEWER),
        "instagram/reel_from_explore_next.xml" to Expect(Surface.REELS, ReelRoute.VIEWER),
        "facebook/reels_tab_ad.xml" to Expect(Surface.REELS, ReelRoute.VIEWER, PageCategory.AD),
        "facebook/reels_tab_next.xml" to Expect(Surface.REELS, ReelRoute.VIEWER),
        "tiktok/for_you_next.xml" to Expect(Surface.REELS, ReelRoute.VIEWER),
    )

    fun parse(file: File): ScreenSnapshot {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = mutableListOf<ScreenNode>()
        var packageName = ""
        val bounds = Regex("\\[(-?\\d+),(-?\\d+)]\\[(-?\\d+),(-?\\d+)]")
        fun walk(element: Element, parent: Int, depth: Int) {
            var index = parent
            if (element.tagName == "node") {
                val (l, t, r, b) = bounds.find(element.getAttribute("bounds"))!!.destructured
                val label = element.getAttribute("text").ifBlank { element.getAttribute("content-desc") }.ifBlank { null }
                if (packageName.isEmpty()) packageName = element.getAttribute("package")
                index = nodes.size
                nodes.add(ScreenNode(index, parent, depth, element.getAttribute("resource-id").substringAfter(":id/", "").ifBlank { null },
                    element.getAttribute("class"), label, LayoutBounds(l.toInt(), t.toInt(), r.toInt(), b.toInt()),
                    element.getAttribute("scrollable") == "true", element.getAttribute("clickable") == "true",
                    element.getAttribute("selected") == "true", index, null))
            }
            val children = element.childNodes
            for (i in 0 until children.length) (children.item(i) as? Element)?.let { walk(it, index, depth + 1) }
        }
        walk(document.documentElement, -1, -1)
        val root = nodes.first().bounds
        return ScreenSnapshot(packageName, 1, root, nodes, complete = true, typing = false)
    }

    fun run(root: File): Int {
        if (!root.isDirectory) return 0 // On-device runs carry no fixture files.
        var checks = 0
        val failures = mutableListOf<String>()
        for ((path, expect) in expectations) {
            val file = File(root, path)
            check(file.isFile) { "missing fixture $path" }
            val app = ReelApp.byKey(path.substringBefore('/'))!!
            val frame = AdapterRegistry.of(app).read(parse(file))
            fun require(condition: Boolean, what: String) {
                if (condition) checks++ else failures += "$path: expected $what, got ${frame.surface}/${frame.route}/${frame.category} id=${frame.identity != null} via ${frame.method}"
            }
            require(frame.surface == expect.surface, "surface ${expect.surface}")
            expect.route?.let { require(frame.route == it, "route $it") }
            expect.category?.let { require(frame.category == it, "category $it") }
            expect.mediaKind?.let { require(frame.mediaKind == it, "media kind $it") }
            expect.standaloneAd?.let { require(frame.standaloneAd == it, "standalone advertisement $it") }
            require((frame.identity != null) == expect.identified, "identified=${expect.identified}")
            val full = parse(file)
            val truncated = ScreenSnapshot(full.packageName, full.windowId, full.screen, full.nodes, complete = false, typing = false)
            val unknown = AdapterRegistry.of(app).read(truncated)
            require(unknown.surface == Surface.UNKNOWN && unknown.identity == null, "incomplete layout stays unknown")
        }
        check(failures.isEmpty()) { failures.joinToString("\n", "Fixture failures:\n") }
        val snapchat = AdapterRegistry.of(ReelApp.SNAPCHAT)
        val first = snapchat.read(parse(File(root, "snapchat/spotlight.xml")))
        val next = snapchat.read(parse(File(root, "snapchat/spotlight_next.xml")))
        check(first.identity != next.identity && org.attentionstats.reels.EncounterTracker.overlap(first.keys, next.keys) < 0.5) {
            "Spotlight navigation must not suppress different items"
        }
        checks++
        // Synthetic aspect-ratio/album variations of an observed photo; not live format qualification.
        val facebook = AdapterRegistry.of(ReelApp.FACEBOOK)
        val standalone = parse(File(root, "facebook/standalone_reels_video_ad.xml"))
        val pill = standalone.nodes.single { it.cls == "android.widget.Button" && it.label == "Ad" }
        val pillChild = standalone.nodes.single { it.parent == pill.index && it.label == "Ad" }
        fun modifiedAd(change: (ScreenNode) -> ScreenNode): AdapterFrame = facebook.read(ScreenSnapshot(
            standalone.packageName, standalone.windowId, standalone.screen, standalone.nodes.map(change), true, false))
        fun marker(node: ScreenNode, cls: String = node.cls, label: String? = node.label,
            bounds: LayoutBounds = node.bounds, clickable: Boolean = node.clickable): ScreenNode = ScreenNode(
            node.index, node.parent, node.depth, node.id, cls, label, bounds, node.scrollable, clickable,
            node.selected, node.key, node.range)
        for (change in listOf<(ScreenNode) -> ScreenNode>(
            { if (it === pill) marker(it, cls = "android.view.ViewGroup") else it },
            { if (it === pill) marker(it, clickable = false) else it },
            { if (it === pill) marker(it, label = "Sponsored") else it },
            { if (it === pill) marker(it, bounds = LayoutBounds(23, 200, 112, 245)) else it },
            { if (it === pill) marker(it, bounds = LayoutBounds(400, 1315, 489, 1360)) else it },
            { if (it === pill) marker(it, bounds = LayoutBounds(23, 1315, 600, 1360)) else it },
            { if (it === pillChild) marker(it, label = "synthetic-caption") else it },
            { if (it.selected && it.label?.startsWith("Reels,") == true) marker(it, label = "synthetic-other-tab") else it },
        )) {
            check(!modifiedAd(change).standaloneAd) { "Caption/sponsored text or misplaced/unqualified marker must not authorize ad skipping" }
            checks++
        }
        check(!facebook.read(ScreenSnapshot(standalone.packageName, standalone.windowId, standalone.screen,
            standalone.nodes, false, false)).standaloneAd)
        check(!facebook.read(ScreenSnapshot(standalone.packageName, standalone.windowId, standalone.screen,
            standalone.nodes, true, true)).standaloneAd)
        checks += 2
        val pager = standalone.nodes.first { it.scrollable }
        val listShaped = standalone.nodes.map { node -> if (node === pager) ScreenNode(node.index, node.parent,
            node.depth, "list", node.cls, node.label, node.bounds, node.scrollable, node.clickable,
            node.selected, node.key, node.range) else node }
        val listFrame = facebook.read(ScreenSnapshot(standalone.packageName, standalone.windowId, standalone.screen,
            listShaped, true, false))
        check(listFrame.surface == Surface.REELS && listFrame.route == ReelRoute.VIEWER)
        val unsettled = standalone.nodes.map { node -> marker(node, clickable = false) }.map { node ->
            if (node.selected && node.label?.startsWith("Reels,") == true) marker(node, clickable = true) else node }
        val transition = facebook.read(ScreenSnapshot(standalone.packageName, standalone.windowId, standalone.screen,
            unsettled, true, false))
        check(transition.surface == Surface.UNKNOWN && transition.route == ReelRoute.VIEWER &&
            transition.identity == null && !transition.standaloneAd)
        checks += 2
        val buttonAlbum = facebook.read(parse(File(root, "facebook/feed_photo_button_album.xml")))
        check(buttonAlbum.page == LayoutBounds(0, 531, 720, 1251)) {
            "observed photo-role buttons must group into one album, not focus an individual tile"
        }
        checks++
        val photo = parse(File(root, "facebook/feed_portrait_photo.xml"))
        val leaf = photo.nodes.first { it.cls.endsWith("ImageView") && it.label == "Photo" }
        fun withBounds(node: ScreenNode, box: LayoutBounds, index: Int = node.index) = ScreenNode(index,
            node.parent, node.depth, node.id, node.cls, node.label, box, node.scrollable, node.clickable,
            node.selected, node.key + index - node.index, node.range)
        for (box in listOf(LayoutBounds(0, 650, 720, 810), LayoutBounds(210, 350, 510, 1320))) {
            val nodes = photo.nodes.map { if (it === leaf) withBounds(it, box) else it }
            val frame = facebook.read(ScreenSnapshot(photo.packageName, photo.windowId, photo.screen, nodes, true, false))
            check(frame.surface == Surface.REELS && frame.category == PageCategory.PHOTO && frame.mediaKind == "photo") {
                "positive photo aspect ratio must not be rejected"
            }
            checks++
        }
        val tiles = photo.nodes.map { if (it === leaf) withBounds(it, LayoutBounds(0, 598, 230, 1048)) else it } +
            listOf(withBounds(leaf, LayoutBounds(240, 598, 470, 1048), photo.nodes.size),
                withBounds(leaf, LayoutBounds(480, 598, 720, 1048), photo.nodes.size + 1))
        val album = facebook.read(ScreenSnapshot(photo.packageName, photo.windowId, photo.screen, tiles, true, false))
        check(album.surface == Surface.REELS && album.mediaKind == "photo" && album.page?.width == 720) {
            "one album must group narrow photo tiles into one media region"
        }
        checks++
        return checks
    }
}
