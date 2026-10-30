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
        val identified: Boolean = surface == Surface.REELS)

    private val expectations: Map<String, Expect> = mapOf(
        "instagram/reels_tab.xml" to Expect(Surface.REELS, ReelRoute.VIEWER, PageCategory.REEL),
        "instagram/home_feed_ad_video.xml" to Expect(Surface.REELS, ReelRoute.HOME, PageCategory.AD),
        "facebook/home_no_video.xml" to Expect(Surface.OUTSIDE, identified = false),
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
        "facebook/home_feed_2.xml" to Expect(Surface.OUTSIDE),
        "facebook/marketplace.xml" to Expect(Surface.OUTSIDE),
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
            require((frame.identity != null) == expect.identified, "identified=${expect.identified}")
        }
        check(failures.isEmpty()) { failures.joinToString("\n", "Fixture failures:\n") }
        return checks
    }
}
