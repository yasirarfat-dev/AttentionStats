package org.attentionstats.reels

import org.attentionstats.detection.LayoutBounds
import org.attentionstats.detection.PlaybackTimeParser
import org.attentionstats.detection.ScreenNode
import org.attentionstats.detection.ScreenSnapshot
import org.attentionstats.detection.shared.ViewerLocator

fun main() {
    var checks = 0
    fun expect(value: Boolean, message: String) { check(value) { message }; checks++ }
    fun frame(at: Long, id: Long?, keys: Set<Long> = id?.let { setOf(it, it + 1, it + 2) } ?: emptySet(),
        scroll: Long = 0, surface: Surface = Surface.REELS, route: ReelRoute = ReelRoute.VIEWER,
        category: PageCategory = PageCategory.REEL, sameSlot: Boolean = false, slot: Int? = null) =
        ItemFrame(at, surface, route, id, keys, category, scroll, sameSlot, slot)

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

    // Returning to an item whose controls changed while away (e.g. a Replay label) is still a revisit,
    // while a different item that merely shares author and audio strings counts as new.
    val changes = EncounterTracker()
    val watched = setOf(100L, 101L, 102L, 103L, 104L)
    changes.observe(frame(0, 100, watched)); changes.observe(frame(300, 100, watched))
    changes.observe(frame(1_000, 200, setOf(100L, 101L, 900L, 901L, 902L), scroll = 1))
    expect(changes.observe(frame(1_300, 200, setOf(100L, 101L, 900L, 901L, 902L), scroll = 1))?.revisit == false,
        "same author and audio is a new item")
    changes.observe(frame(2_000, 300, watched + 105L, scroll = 2))
    expect(changes.observe(frame(2_300, 300, watched + 105L, scroll = 2))?.revisit == true,
        "returning item with one changed control is a revisit")

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
    val recycled = EncounterTracker()
    recycled.observe(frame(0, 10, route = ReelRoute.HOME))
    recycled.observe(frame(450, 10, route = ReelRoute.HOME))
    expect(recycled.observe(frame(1_000, 20, scroll = 1, route = ReelRoute.HOME,
        category = PageCategory.PHOTO)) == null, "new recycled feed post starts dwell")
    expect(recycled.observe(frame(1_300, 20, scroll = 1, route = ReelRoute.HOME,
        category = PageCategory.PHOTO, sameSlot = true)) == null, "same slot cannot end pending dwell early")
    val settledPhoto = recycled.observe(frame(1_500, 20, scroll = 1, route = ReelRoute.HOME,
        category = PageCategory.PHOTO, sameSlot = true))
    expect(settledPhoto?.ordinal == 2L && settledPhoto.category == PageCategory.PHOTO,
        "recycled slot counts settled new photo instead of adopting it silently")
    expect(recycled.observe(frame(1_900, 21, scroll = 1, route = ReelRoute.HOME,
        category = PageCategory.PHOTO, sameSlot = true)) == null, "settled post control change stays uncounted")
    expect(recycled.focused == 21L, "same-slot control change is still adopted")
    val clipping = EncounterTracker()
    clipping.observe(frame(0, 10, keys = setOf(1, 2, 3), route = ReelRoute.HOME, slot = 7))
    clipping.observe(frame(450, 10, keys = setOf(1, 2, 3), route = ReelRoute.HOME, slot = 7))
    expect(clipping.observe(frame(1_000, 11, keys = setOf(2, 3), scroll = 1,
        route = ReelRoute.HOME, slot = 7)) == null, "clipped header on same post isn't a new encounter")
    expect(clipping.focused == 11L, "same post adopts clipped metadata")
    clipping.observe(frame(2_000, 20, keys = setOf(3, 4, 5), scroll = 2, route = ReelRoute.HOME, slot = 7))
    expect(clipping.observe(frame(2_500, 20, keys = setOf(3, 4, 5), scroll = 2,
        route = ReelRoute.HOME, sameSlot = true, slot = 7))?.ordinal == 2L,
        "recycled slot with distinct post metadata still counts")
    clipping.leave()
    clipping.observe(frame(3_000, 11, keys = setOf(2, 3), scroll = 3, route = ReelRoute.HOME, slot = 7))
    expect(clipping.observe(frame(3_500, 11, keys = setOf(2, 3), scroll = 3,
        route = ReelRoute.HOME, slot = 7))?.revisit == true, "return to a clipped-metadata alias remains a revisit")
    val fadingHeader = EncounterTracker()
    fadingHeader.observe(frame(0, 100, keys = setOf(101, 102, 103), route = ReelRoute.HOME, slot = 7, category = PageCategory.AD))
    fadingHeader.observe(frame(450, 100, keys = setOf(101, 102, 103), route = ReelRoute.HOME, slot = 7, category = PageCategory.AD))
    expect(fadingHeader.observe(frame(1_000, 101, keys = setOf(102, 103), scroll = 1, route = ReelRoute.HOME,
        slot = 7, category = PageCategory.PHOTO)) == null, "an ad header clipping away cannot recount its image as a photo")
    expect(fadingHeader.observe(frame(1_500, 102, keys = emptySet(), scroll = 2, route = ReelRoute.HOME,
        slot = 7)) == null, "loss of all distinctive labels in the same host cannot invent an item")
    fadingHeader.observe(frame(2_000, 200, keys = setOf(201, 202), scroll = 3, route = ReelRoute.HOME, slot = 7))
    expect(fadingHeader.observe(frame(2_500, 200, keys = setOf(201, 202), scroll = 3, route = ReelRoute.HOME,
        slot = 7, sameSlot = true))?.ordinal == 2L, "distinct metadata after a clipped label gap counts a new item")
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
    val playback = PlaybackEvidence()
    expect(org.attentionstats.detection.shared.Fingerprint.normalize("Spotlight") == null, "navigation is not item identity")
    expect(org.attentionstats.detection.shared.Fingerprint.normalize("Home, tab 1 of 6") == null, "tab metadata is not content")
    expect(org.attentionstats.detection.shared.Fingerprint.normalize("Photo") == null, "generic photo role is not item identity")
    expect(org.attentionstats.detection.shared.Fingerprint.normalize("Ad") == null, "generic ad role is not item identity")
    expect(org.attentionstats.detection.shared.Fingerprint.normalize("Photo by synthetic author with a distinct caption") != null,
        "a distinctive photo description remains identity evidence")
    expect(playback.sample(0, null, null) == null, "no signal is unknown")
    playback.sample(0, 0, 4_000)
    expect(playback.sample(2_000, 0, 4_000)?.qualified == false, "stationary position isn't watching")
    playback.reset()
    playback.sample(0, 0, 4_000)
    expect(playback.sample(1_000, 1_000, 4_000)?.confirmedMs == 1_000L, "advancing numeric player")
    playback.sample(2_000, 2_000, 4_000)
    expect(playback.sample(3_000, 3_000, 4_000)?.qualified == true, "three second threshold")
    expect(playback.sample(4_000, 4_000, 4_000)?.completed == true, "observed start-to-end completion")
    playback.reset(); playback.sample(0, 0, 30_000)
    expect(playback.sample(1_000, 20_000, 30_000)?.confirmedMs == 0L, "seeking isn't twenty seconds watched")
    expect(playback.sample(2_000, 21_000, 30_000)?.completed == false, "seek prevents completion")
    playback.reset(); playback.sample(0, 0, 10_000)
    expect(playback.sample(10_000, 1_000, 10_000)?.confirmedMs == 0L, "pause/gap adds nothing")
    playback.reset(); playback.sample(0, 8_000, 10_000)
    playback.sample(1_000, 9_000, 10_000)
    expect(playback.sample(2_000, 10_000, 10_000)?.completed == false, "entry near end isn't full watch")
    playback.reset(); playback.sample(0, 0, 10_000); playback.sample(1_000, 1_000, 10_000)
    playback.sample(2_000, null, null)
    expect(playback.sample(3_000, 3_000, 10_000)?.confirmedMs == 1_000L, "missing samples never interpolate watching")
    playback.reset(); playback.sample(0, 0, 12_000); playback.sample(1_000, 1_000, 12_000)
    playback.sample(2_000, 2_000, 12_000)
    expect(playback.sample(3_000, null, null)?.confirmedMs == 2_000L, "consent boundary preserves confirmed time")
    expect(playback.sample(10_000, 8_000, 12_000)?.confirmedMs == 2_000L, "same visit resumes without backfilling disabled time")
    expect(playback.sample(11_000, 9_000, 12_000)?.qualified == true, "same visit can qualify after re-enabling")
    playback.sample(12_000, 10_000, 12_000); playback.sample(13_000, 11_000, 12_000)
    expect(playback.sample(14_000, 12_000, 12_000)?.completed == false, "completion cannot bridge a consent gap")
    for ((label, expected) in listOf(
        "0 minutes 29 seconds of 1 minute 19 seconds" to (29_000L to 79_000L),
        "5 seconds of 42 seconds" to (5_000L to 42_000L),
        "0 seconds of 15 seconds" to (0L to 15_000L),
        "1 minute of 1 minute 5 seconds" to (60_000L to 65_000L),
        "0 hours, 2 minutes and 3 seconds of 0 hours, 3 minutes, and 4 seconds" to (123_000L to 184_000L),
        "75 SECONDS OF 2 MINUTES AND 0 SECONDS" to (75_000L to 120_000L),
        "0 seconds of 1 hour" to (0L to 3_600_000L),
    )) expect(PlaybackTimeParser.spoken(label) == expected, "spoken playback parses: $label")
    for (label in listOf(
        "29 seconds", "of 1 minute", "1 minute 75 seconds of 2 minutes", "2 minutes of 1 minute",
        "0 seconds of 0 seconds", "2 hours of 3 hours", "abc 1 minute of 2 minutes", "x".repeat(90),
        "1 hour 2 minutes 3 seconds of 1 hour 10 minutes", // Duration exceeds the one-hour bound.
        "1 second 2 seconds of 1 minute", "1 second 1 minute of 2 minutes",
        "0 hours 75 minutes of 1 hour", "1 minute  2 seconds of 2 minutes",
        "1000 seconds of 1 hour", "١ second of 1 minute", "1 second of 2 seconds trailing",
    )) expect(PlaybackTimeParser.spoken(label) == null, "invalid spoken playback rejected: $label")
    expect(PlaybackTimeParser.spoken(null) == null, "null spoken playback is unknown")
    val spokenPage = LayoutBounds(0, 64, 720, 1420)
    fun spokenSnapshot(cls: String) = ScreenSnapshot("synthetic", 1, LayoutBounds(0, 0, 720, 1600), listOf(
        ScreenNode(0, -1, 0, null, cls, "0 minutes 30 seconds of 1 minute 0 seconds",
            LayoutBounds(0, 1374, 720, 1464), false, false, false, 0, null),
    ), true, false)
    expect(ViewerLocator.progress(spokenSnapshot("android.widget.SeekBar"), spokenPage) == .5,
        "spoken SeekBar progress includes the bar extending below the page")
    expect(ViewerLocator.progress(spokenSnapshot("android.widget.ProgressBar"), spokenPage) == .5,
        "spoken ProgressBar progress is numeric evidence")
    fun stuckRange(label: String?) = ScreenSnapshot("synthetic", 1, LayoutBounds(0, 0, 720, 1600), listOf(
        ScreenNode(0, -1, 0, null, "android.widget.SeekBar", label,
            LayoutBounds(0, 1374, 720, 1464), false, false, false, 0, ScreenNode.Range(0f, 100f, 0f)),
    ), true, false)
    expect(ViewerLocator.progress(stuckRange("0 minutes 30 seconds of 1 minute 0 seconds"), spokenPage) == .5,
        "a range stuck at its minimum gives way to the spoken position (YouTube Shorts)")
    expect(ViewerLocator.progress(stuckRange(null), spokenPage) == 0.0,
        "a range at its minimum without a readable label still reports 0")
    expect(ViewerLocator.progress(spokenSnapshot("android.widget.TextView"), spokenPage) == null,
        "spoken TextView labels are not playback progress")
    val fixtures = java.io.File(System.getProperty("fixtures") ?: "../../native/reels-core/src/test/resources/fixtures")
    val fixtureChecks = org.attentionstats.detection.FixtureTests.run(fixtures)
    println("PASS: $checks encounter checks, $fixtureChecks fixture checks")
}
