package org.attentionstats.reels

fun main() {
    var checks = 0
    fun expect(value: Boolean, message: String) { check(value) { message }; checks++ }
    fun frame(at: Long, id: Long?, keys: Set<Long> = id?.let { setOf(it, it + 1, it + 2) } ?: emptySet(),
        scroll: Long = 0, surface: Surface = Surface.REELS, route: ReelRoute = ReelRoute.VIEWER,
        category: PageCategory = PageCategory.REEL, sameSlot: Boolean = false) =
        ItemFrame(at, surface, route, id, keys, category, scroll, sameSlot)

    // First item counts after its dwell, with no scroll required.
    val t = EncounterTracker()
    expect(t.observe(frame(0, 10)) == null, "dwell not reached")
    val first = t.observe(frame(300, 10))
    expect(first != null && !first.revisit && first.ordinal == 1L, "first item counts on entry")
    expect(t.observe(frame(900, 10)) == null, "same item never recounts")

    // A like/follow toggle changes one metadata string without scrolling: adopted, not counted.
    val toggled = setOf(10L, 11L, 99L)
    expect(t.observe(frame(1_000, 77, toggled)) == null, "candidate")
    expect(t.observe(frame(1_400, 77, toggled)) == null, "toggle adopted silently")
    expect(t.focused == 77L, "adopted identity")

    // Scrolling to a new item counts it.
    expect(t.observe(frame(2_000, 20, scroll = 1)) == null, "candidate after scroll")
    val second = t.observe(frame(2_300, 20, scroll = 1))
    expect(second != null && second.ordinal == 2L, "scrolled item counts")

    // Auto-advance without a scroll event still counts when the metadata is entirely different.
    t.observe(frame(3_000, 30, scroll = 1))
    expect(t.observe(frame(3_300, 30, scroll = 1))?.ordinal == 3L, "auto-advance counts")

    // A fling that passes an item faster than the dwell does not count it.
    t.observe(frame(4_000, 40, scroll = 2))
    t.observe(frame(4_100, 50, scroll = 3))
    expect(t.observe(frame(4_400, 50, scroll = 3))?.ordinal == 4L, "only the settled item counts")

    // Scrolling back to an already-seen item is a revisit, not a new encounter.
    t.observe(frame(5_000, 20, scroll = 4))
    val back = t.observe(frame(5_300, 20, scroll = 4))
    expect(back != null && back.revisit && back.ordinal == 4L, "revisit flagged, ordinal unchanged")

    // Typing a comment excludes the surface but keeps the current item.
    expect(t.observe(frame(5_400, null, surface = Surface.EXCLUDED)) == null, "excluded")
    expect(t.observe(frame(5_800, 20, scroll = 4)) == null, "returning from comments does not recount")

    // Leaving the app and re-entering: the first item counts again only if it is new.
    t.observe(frame(6_000, null, surface = Surface.OUTSIDE))
    t.observe(frame(7_000, 60, scroll = 5))
    expect(t.observe(frame(7_300, 60, scroll = 5))?.revisit == false, "fresh entry counts new item")
    t.observe(frame(8_000, null, surface = Surface.OUTSIDE))
    t.observe(frame(8_100, 60, scroll = 5))
    expect(t.observe(frame(8_400, 60, scroll = 5))?.revisit == true, "re-entry on same item is a revisit")

    // Feed items need a longer dwell; same-slot continuity adopts changed metadata.
    val feed = EncounterTracker()
    feed.observe(frame(0, 1, route = ReelRoute.HOME))
    expect(feed.observe(frame(300, 1, route = ReelRoute.HOME)) == null, "feed dwell longer")
    expect(feed.observe(frame(450, 1, route = ReelRoute.HOME)) != null, "feed counts after dwell")
    expect(feed.observe(frame(600, 2, scroll = 1, route = ReelRoute.HOME, sameSlot = true)) == null, "same slot")
    expect(feed.focused == 2L, "same slot adopted")
    // Switching route (feed -> viewer) is a fresh entry.
    feed.observe(frame(700, 3, scroll = 1))
    expect(feed.observe(frame(1_000, 3, scroll = 1))?.route == ReelRoute.VIEWER, "route switch counts")

    // Session reset forgets seen items; time regression also resets.
    feed.reset()
    feed.observe(frame(2_000, 1, route = ReelRoute.HOME))
    expect(feed.observe(frame(2_500, 1, route = ReelRoute.HOME))?.revisit == false, "reset forgets")
    feed.observe(frame(10, 1, route = ReelRoute.HOME))
    expect(feed.observe(frame(500, 1, route = ReelRoute.HOME))?.revisit == false, "time regression resets")

    expect(EncounterTracker.overlap(setOf(1, 2, 3), setOf(1, 2, 9)) > 0.6, "overlap")
    expect(EncounterTracker.overlap(emptySet(), setOf(1)) == 0.0, "empty overlap")
    expect(ReelApp.byPackage("com.ss.android.ugc.trill") == ReelApp.TIKTOK, "regional TikTok")
    expect(ReelApp.byPackage("com.snapchat.android") == ReelApp.SNAPCHAT, "Snapchat")
    expect(ReelApp.byKey("unsupported") == null, "unknown key")
    expect(ReelApp.allPackages.toSet().size == ReelApp.allPackages.size, "unique packages")
    expect(PageCategory.byKey("ad") == PageCategory.AD && !PageCategory.AD.video, "ad category")
    val fixtures = java.io.File(System.getProperty("fixtures") ?: "../../native/reels-core/src/test/resources/fixtures")
    val fixtureChecks = org.attentionstats.detection.FixtureTests.run(fixtures)
    println("PASS: $checks encounter checks, $fixtureChecks fixture checks")
}
