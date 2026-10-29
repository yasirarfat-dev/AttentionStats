package com.attentionstats.qualification

import com.attentionstats.detectors.DetectorRegistry
import com.attentionstats.detectors.InlineAnchor
import com.attentionstats.detectors.InlineEncounterTracker
import com.attentionstats.detectors.InlineSelection
import com.attentionstats.detectors.InterfaceLabel
import com.attentionstats.detectors.LayoutBounds
import com.attentionstats.detectors.NodeRole
import com.attentionstats.detectors.SurfaceEvidence
import org.attentionstats.reels.ReelApp

/** Mixed-feed layout/continuity boundaries, independent of Android input and real history. */
internal object InlineEncounterProbe {
    fun verify(): Int {
        var checks = 0
        fun expect(value: Boolean) { check(value); checks++ }
        val viewport = LayoutBounds(0, 0, 720, 1600)
        val media = InlineAnchor("media_group", 100, LayoutBounds(0, 300, 720, 1300), false)
        val audio = InlineAnchor("indicator_background_view", 101, LayoutBounds(26, 1200, 77, 1251), false)
        val home = InlineAnchor("feed_tab", 1, LayoutBounds(0, 1450, 120, 1550), true)
        val instagram = checkNotNull(DetectorRegistry.detector(ReelApp.INSTAGRAM, "452.1.0.54.64"))
        val ig = SurfaceEvidence(setOf("feed_tab", "row_feed_profile_header", "media_group"), true, false, null,
            viewport, inlineAnchors = listOf(home, media, audio))
        expect(instagram.inlineSelection(ig)?.indicator == audio)
        expect(instagram.inlineSelection(ig.copy(inlineAnchors = listOf(home, media)))?.indicator == null)
        expect(instagram.inlineSelection(ig.copy(inlineAnchors = listOf(home.copy(selected = false), media, audio))) == null)
        expect(instagram.inlineSelection(ig.copy(inlineAnchors = listOf(home.copy(selected = false), media, audio), inlineRootPage = 0))?.indicator == audio)
        expect(instagram.inlineSelection(ig.copy(inlineRootPage = 4)) == null)
        expect(instagram.inlineSelection(ig.copy(editable = true)) == null)
        expect(instagram.inlineSelection(ig.copy(bounded = false)) == null)
        expect(instagram.inlineSelection(ig.copy(inlineComplete = false)) == null)
        expect(instagram.inlineSelection(ig.copy(ids = ig.ids + "clips_viewer_view_pager")) == null)
        expect(instagram.inlineSelection(ig.copy(inlineAnchors = listOf(home, media,
            audio.copy(bounds = LayoutBounds(26, 100, 77, 151)))))?.indicator == null)
        val facebook = checkNotNull(DetectorRegistry.detector(ReelApp.FACEBOOK, "584.0.0.51.56"))
        val tab = InterfaceLabel("home_tab", NodeRole.VIEW, home.bounds, true, true)
        val reel = InterfaceLabel("reel", NodeRole.BUTTON, LayoutBounds(0, 600, 720, 1000), false, true, 200)
        val fb = SurfaceEvidence(emptySet(), true, false, null, viewport, listOf(tab, reel))
        expect(facebook.inlineSelection(fb)?.item?.key == 200L)
        expect(facebook.inlineSelection(fb.copy(interfaceLabels = listOf(reel))) == null)
        expect(facebook.inlineSelection(fb.copy(interfaceLabels = listOf(reel), inlineContext = true))?.item != null)
        expect(facebook.inlineSelection(fb.copy(interfaceLabels = listOf(tab)))?.item == null)
        expect(facebook.inlineSelection(fb.copy(interfaceLabels = listOf(tab, reel.copy(token = "photo"))))?.item == null)
        expect(facebook.inlineSelection(fb.copy(interfaceLabels = listOf(tab, reel, tab.copy(token = "back")))) == null)
        expect(facebook.inlineSelection(fb.copy(editable = true)) == null)
        expect(facebook.inlineSelection(fb.copy(bounded = false)) == null)
        expect(facebook.inlineSelection(fb.copy(interfaceLabels = listOf(tab, reel.copy(bounds = LayoutBounds(0, 600, 200, 1000)))))?.item == null)

        val tracker = InlineEncounterTracker(7, 3, true)
        expect(tracker.matches(7, 3) && !tracker.matches(8, 3) && !tracker.matches(7, 4))
        val selected = InlineSelection(media, audio)
        expect(InlineEncounterTracker(7, 3).sample(selected, 0, viewport).let { it.page == 0L && !it.freshEntry })
        expect(tracker.sample(selected, 0, viewport).let { it.page == 0L && it.freshEntry })
        repeat(10) { expect(tracker.sample(selected, 0, viewport).let { it.page == 0L && !it.freshEntry }) }
        val moved = selected.copy(item = media.copy(bounds = LayoutBounds(0, 200, 720, 1200)),
            indicator = audio.copy(bounds = LayoutBounds(26, 1100, 77, 1151)))
        expect(tracker.sample(moved, 100, viewport).page == 0L)
        // Recreated virtual nodes at the same post geometry do not invent an encounter.
        expect(tracker.sample(moved.copy(item = checkNotNull(moved.item).copy(key = 999)), 100, viewport).page == 0L)
        // Playback controls may disappear while the same layout remains visible.
        expect(tracker.sample(moved.copy(indicator = null), 100, viewport).page == 0L)
        val clipped = InlineSelection(media.copy(key = 999, bounds = LayoutBounds(0, 0, 720, 500)),
            audio.copy(bounds = LayoutBounds(26, 400, 77, 451)))
        expect(tracker.sample(clipped, 800, viewport).page == 0L) // Large within-item movement retains an unclipped edge.
        val next = InlineSelection(media.copy(key = 999), audio.copy(key = 301))
        expect(tracker.sample(next, 2000, viewport).let { it.page == 1L && !it.freshEntry }) // Recycled virtual key, different post.
        expect(tracker.sample(next.copy(indicator = null, photo = true), 2000, viewport).page == null)
        expect(tracker.sample(next, 2000, viewport).page == 1L)
        tracker.sample(InlineSelection(null, null), 4000, viewport)
        expect(tracker.sample(next, 2000, viewport).page == 2L) // Return after a confirmed full departure from view.
        return checks
    }
}
