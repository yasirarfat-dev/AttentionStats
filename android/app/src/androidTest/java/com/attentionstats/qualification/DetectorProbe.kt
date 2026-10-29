package com.attentionstats.qualification

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.attentionstats.detectors.DetectorRegistry
import com.attentionstats.detectors.SurfaceEvidence
import com.attentionstats.detectors.NodeRole
import com.attentionstats.detectors.InterfaceLabel
import com.attentionstats.detectors.LayoutBounds
import com.attentionstats.detectors.ScrollEvidence
import com.attentionstats.detectors.accessibility.StructuralSnapshotReader
import com.attentionstats.detectors.accessibility.DiagnosticScrollTrace
import com.attentionstats.detectors.accessibility.InterfaceLabelMatcher
import org.json.JSONObject
import org.attentionstats.reels.PageCategory
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface

/** Artificial structural values/nodes only. No target app access, consent or live-store mutations. */
internal object DetectorProbe {
    @Suppress("DEPRECATION") // Detached synthetic nodes; obtain/recycle support the preview's API range.
    fun verify(): Int {
        var checks = 0
        fun expect(value: Boolean) { check(value); checks++ }
        checks += ContentIdentityProbe.verify()
        checks += InlineEncounterProbe.verify()
        val tiktok = checkNotNull(DetectorRegistry.detector(ReelApp.TIKTOK, "47.5.3"))
        val tiktokEvidence = SurfaceEvidence(setOf("viewpager", "long_press_layout", "user_avatar", "desc"), true, false, 2)
        expect(tiktok.detect(tiktokEvidence).let { it.surface == Surface.REELS && it.page == 2L &&
            it.category == PageCategory.UNCLASSIFIED })
        for (id in tiktokEvidence.ids) expect(tiktok.detect(tiktokEvidence.copy(ids = tiktokEvidence.ids - id)).surface == Surface.OUTSIDE)
        expect(tiktok.detect(tiktokEvidence.copy(editable = true)).surface == Surface.EXCLUDED)
        expect(tiktok.detect(tiktokEvidence.copy(bounded = false)).surface == Surface.UNKNOWN)
        expect(tiktok.detect(tiktokEvidence.copy(settledPage = null)).page == null)
        expect(com.attentionstats.reels.ReelsCoordinator.requiredNotice(ReelApp.TIKTOK) == 4)
        expect(com.attentionstats.reels.ReelsCoordinator.requiredNotice(ReelApp.YOUTUBE) == 3)
        val youtube = checkNotNull(DetectorRegistry.detector(ReelApp.YOUTUBE, "21.40.165"))
        val youtubeIds = setOf("reel_recycler", "reel_player_page_container", "reel_time_bar")
        val youtubeEvidence = SurfaceEvidence(youtubeIds, true, false, 1)
        expect(youtube.detect(youtubeEvidence).let { it.surface == Surface.REELS && it.page == 1L &&
            it.category == PageCategory.UNCLASSIFIED })
        for (id in youtubeIds) expect(youtube.detect(youtubeEvidence.copy(ids = youtubeIds - id)).surface == Surface.OUTSIDE)
        expect(youtube.detect(youtubeEvidence.copy(editable = true)).surface == Surface.EXCLUDED)
        expect(youtube.detect(youtubeEvidence.copy(bounded = false)).surface == Surface.UNKNOWN)
        expect(youtube.detect(youtubeEvidence.copy(settledPage = null)).page == null)
        expect(youtube.detect(youtubeEvidence.copy(ids = youtubeIds + "reel_playback_loading_spinner")).page == null)
        expect(DetectorRegistry.detector(ReelApp.YOUTUBE, "synthetic.unknown") == null)
        expect(DetectorRegistry.detector(ReelApp.TIKTOK, "21.40.165") == null)
        val detector = checkNotNull(DetectorRegistry.detector(ReelApp.INSTAGRAM, "452.1.0.54.64"))
        expect(DetectorRegistry.detector(ReelApp.INSTAGRAM, "synthetic.unknown") == null)
        expect(DetectorRegistry.detector(ReelApp.FACEBOOK, "452.1.0.54.64") == null)
        val viewer = setOf("clips_viewer_view_pager", "clips_media_component", "clips_ufi_component")
        fun detect(ids: Set<String> = viewer, bounded: Boolean = true, editable: Boolean = false, page: Long? = 7) =
            detector.detect(SurfaceEvidence(ids, bounded, editable, page))
        expect(detect().surface == Surface.REELS)
        expect(detect().page == 7L)
        expect(detect(page = null).page == null)
        expect(detect(bounded = false).surface == Surface.UNKNOWN)
        expect(detect(editable = true).surface == Surface.EXCLUDED)
        for (excluded in listOf("bottom_sheet_container", "comment_composer_parent_updated", "main_list_view")) {
            expect(detect(viewer + excluded).surface == Surface.EXCLUDED)
            expect(detect(viewer + excluded).page == null)
        }
        expect(detect(setOf("clips_viewer_view_pager")).surface == Surface.REELS)
        expect(detect(setOf("clips_viewer_view_pager")).page == null)
        expect(detect(setOf("row_feed_photo_imageview", "video_container", "media_group")).surface == Surface.OUTSIDE)
        expect(detect(setOf("clips_container", "preview_container", "clips_items_view")).surface == Surface.OUTSIDE)
        expect(detect(emptySet()).surface == Surface.OUTSIDE)
        expect(detect().category == PageCategory.UNCLASSIFIED)
        val controls = setOf("save_button", "repost_count")
        val video = setOf("clips_single_media_component", "clips_video_container")
        val photo = setOf("clips_carousel_viewpager", "clips_carousel_image_media_content")
        expect(detect(viewer + controls + video).category == PageCategory.REEL)
        expect(detect(viewer + controls + photo).category == PageCategory.PHOTO)
        expect(detect(viewer + controls + video + "clips_single_image_ads_media_content").category == PageCategory.AD)
        expect(detect(viewer + video).category == PageCategory.UNCLASSIFIED)
        expect(detect(viewer + photo).category == PageCategory.UNCLASSIFIED)

        val facebook = checkNotNull(DetectorRegistry.detector(ReelApp.FACEBOOK, "584.0.0.51.56"))
        val screen = LayoutBounds(0, 0, 720, 1600)
        val heading = InterfaceLabel("reels", NodeRole.VIEW, LayoutBounds(107, 112, 219, 144), false, false)
        val search = InterfaceLabel("search", NodeRole.BUTTON, LayoutBounds(558, 94, 626, 162), false, true)
        val scroll = ScrollEvidence(null, NodeRole.RECYCLER, LayoutBounds(0, 64, 720, 1405), 5, 5, 9)
        val fb = SurfaceEvidence(emptySet(), true, false, 5, screen, listOf(heading, search), scroll)
        expect(facebook.detect(fb).surface == Surface.REELS && facebook.detect(fb).page == 5L)
        expect(facebook.detect(fb).category == PageCategory.UNCLASSIFIED)
        expect(facebook.detect(fb.copy(interfaceLabels = listOf(search))).surface == Surface.OUTSIDE)
        expect(facebook.detect(fb.copy(interfaceLabels = emptyList())).surface == Surface.OUTSIDE)
        expect(facebook.detect(fb.copy(interfaceLabels = listOf(heading))).surface == Surface.UNKNOWN)
        expect(facebook.detect(fb.copy(interfaceLabels = listOf(heading.copy(clickable = true), search))).surface == Surface.OUTSIDE)
        expect(facebook.detect(fb.copy(interfaceLabels = listOf(heading, heading, search))).surface == Surface.OUTSIDE)
        expect(facebook.detect(fb.copy(bounded = false)).surface == Surface.UNKNOWN)
        expect(facebook.detect(fb.copy(editable = true)).surface == Surface.EXCLUDED)
        expect(facebook.detect(fb.copy(interfaceLabels = listOf(heading, search, search.copy(token = "comments")))).surface == Surface.EXCLUDED)
        expect(facebook.detect(fb.copy(scroll = null)).page == null)
        expect(facebook.detect(fb.copy(scroll = scroll.copy(to = 6))).page == null)
        expect(facebook.detect(fb.copy(scroll = scroll.copy(items = 5))).page == null)
        expect(facebook.detect(fb.copy(scroll = scroll.copy(bounds = LayoutBounds(0, 500, 720, 900)))).page == null)
        expect(facebook.detect(fb.copy(scroll = scroll.copy(role = NodeRole.OTHER))).page == null)
        expect(facebook.detect(fb.copy(viewport = LayoutBounds(0, 0, 1600, 720))).surface == Surface.UNKNOWN)

        val back = InterfaceLabel("back", NodeRole.BUTTON, LayoutBounds(15, 89, 60, 134), false, true)
        val linked = fb.copy(interfaceLabels = listOf(back, search),
            scroll = scroll.copy(bounds = LayoutBounds(0, 0, 720, 1397)))
        expect(facebook.detect(linked).surface == Surface.REELS && facebook.detect(linked).page == 5L)
        expect(facebook.detect(linked.copy(scroll = null)).surface == Surface.UNKNOWN)
        val movingLinked = linked.copy(scroll = checkNotNull(linked.scroll).copy(to = 6))
        expect(facebook.detect(movingLinked).surface == Surface.REELS)
        expect(facebook.detect(movingLinked).page == null)
        expect(facebook.detect(linked.copy(settledPage = 4)).page == null)
        expect(facebook.detect(linked.copy(scroll = scroll)).surface == Surface.UNKNOWN) // Ordinary list below header.
        expect(facebook.detect(linked.copy(scroll = scroll.copy(bounds = LayoutBounds(30, 0, 690, 1397)))).surface == Surface.UNKNOWN)
        expect(facebook.detect(linked.copy(interfaceLabels = listOf(back))).surface == Surface.UNKNOWN)
        expect(facebook.detect(linked.copy(interfaceLabels = listOf(back, back, search))).surface == Surface.OUTSIDE)
        expect(facebook.detect(linked.copy(interfaceLabels = listOf(back.copy(clickable = false), search))).surface == Surface.OUTSIDE)
        expect(facebook.detect(linked.copy(editable = true)).surface == Surface.EXCLUDED)
        expect(facebook.detect(linked.copy(bounded = false)).surface == Surface.UNKNOWN)
        expect(facebook.detect(linked.copy(interfaceLabels = linked.interfaceLabels + back.copy(token = "comments"))).surface == Surface.EXCLUDED)
        expect(!facebook.acceptsScroll(scroll.copy(from = -1, to = -1, items = -1)))

        val node = AccessibilityNodeInfo.obtain()
        try {
            node.packageName = ReelApp.FACEBOOK.packageName
            node.isVisibleToUser = true
            for ((label, token) in listOf("Reels" to "reels", "Home" to "home", "Video" to "video",
                "Close" to "close", "Comments" to "comments", "Search" to "search", "Back" to "back")) {
                node.contentDescription = label
                expect(InterfaceLabelMatcher.match(ReelApp.FACEBOOK, node, true) == token)
            }
            node.contentDescription = "Reels"
            expect(InterfaceLabelMatcher.match(ReelApp.FACEBOOK, node, false) == null)
            expect(InterfaceLabelMatcher.match(ReelApp.INSTAGRAM, node, true) == null)
            node.isEditable = true
            expect(InterfaceLabelMatcher.match(ReelApp.FACEBOOK, node, true) == null)
            node.isEditable = false; node.isPassword = true
            expect(InterfaceLabelMatcher.match(ReelApp.FACEBOOK, node, true) == null)
            node.isPassword = false; node.isVisibleToUser = false
            expect(InterfaceLabelMatcher.match(ReelApp.FACEBOOK, node, true) == null)
            node.isVisibleToUser = true
            for (label in listOf("PRIVATE_SYNTHETIC_MARKER", "Reels by PRIVATE_SYNTHETIC_MARKER", "Reels ", "reels", "x".repeat(65))) {
                node.contentDescription = label
                expect(InterfaceLabelMatcher.match(ReelApp.FACEBOOK, node, true) == null)
            }
            for ((label, token) in listOf("Like" to "like", "Comment" to "comment_button", "Share" to "share",
                "Reels, tab 2 of 6" to "reels_tab", "Home, tab 1 of 6" to "home_tab")) {
                node.contentDescription = label
                expect(InterfaceLabelMatcher.match(ReelApp.FACEBOOK, node, true, true) == token)
                expect(InterfaceLabelMatcher.match(ReelApp.FACEBOOK, node, true) == if (token == "home_tab") token else null)
            }
            node.contentDescription = null
            node.packageName = ReelApp.INSTAGRAM.packageName
            node.isVisibleToUser = true
            node.viewIdResourceName = ReelApp.INSTAGRAM.packageName + ":id/video_container"
            node.className = "android.view.View"
            node.isClickable = true
            node.setBoundsInScreen(Rect(0, 100, 400, 600))
            val quiet = StructuralSnapshotReader.inspect(ReelApp.INSTAGRAM, node, detector, false)
            expect(quiet.ids == setOf("video_container"))
            expect(quiet.anchors.length() == 0 && quiet.controls.length() == 0 && quiet.positions.length() == 0)
            val diagnostic = StructuralSnapshotReader.inspect(ReelApp.INSTAGRAM, node, detector, true)
            expect(diagnostic.bounded && diagnostic.nodes == 1)
            expect(diagnostic.anchors.length() == 1)
            expect(diagnostic.anchors.getJSONObject(0).getInt("top") == 100)
            expect(diagnostic.anchors.getJSONObject(0).getJSONArray("ancestors").length() == 0)
            expect(diagnostic.controls.length() == 1)
            node.isEditable = true
            val editable = StructuralSnapshotReader.inspect(ReelApp.INSTAGRAM, node, detector, true)
            expect(editable.editable && editable.ids.isEmpty() && editable.anchors.length() == 0)
            node.isEditable = false
            node.packageName = "org.example.synthetic.foreign"
            expect(StructuralSnapshotReader.inspect(ReelApp.INSTAGRAM, node, detector, true).ids.isEmpty())
            expect(StructuralSnapshotReader.resourceId(ReelApp.INSTAGRAM, "org.example.synthetic:id/video_container") == null)
        } finally { node.recycle() }
        val trace = DiagnosticScrollTrace()
        val scope = DiagnosticScrollTrace.Scope(ReelApp.INSTAGRAM, 4, 20)
        fun row() = JSONObject().put("source", "synthetic_pager")
        trace.record(scope, 1_000, 980, row())
        val first = trace.begin(scope, 1_080)
        val paired = checkNotNull(trace.finish(first, 1_100))
        expect(paired.getBoolean("stable"))
        expect(paired.getInt("durationMs") == 20)
        val event = paired.getJSONArray("events").getJSONObject(0)
        expect(event.getInt("ageMs") == 80 && event.getInt("deliveryDelayMs") == 20)
        expect(event.getInt("sequence") == 1)
        trace.record(scope, 1_090, 1_080, row())
        val raced = checkNotNull(trace.finish(first, 1_110))
        expect(!raced.getBoolean("stable"))
        expect(raced.getInt("startSequence") == 1 && raced.getInt("endSequence") == 2)
        expect(raced.getJSONArray("events").length() == 1) // Mid-scan event is not paired backwards.
        expect(trace.finish(first, 1_079) == null)
        expect(trace.finish(first, 3_081) == null)
        expect(checkNotNull(trace.finish(trace.begin(scope, 4_000), 4_020)).getJSONArray("events").length() == 0)
        for (changed in listOf(scope.copy(window = 21), scope.copy(epoch = 5), scope.copy(app = ReelApp.FACEBOOK))) {
            val before = trace.begin(scope, 5_000)
            trace.record(changed, 5_001, 5_000, row())
            expect(trace.finish(before, 5_010) == null)
            expect(checkNotNull(trace.finish(trace.begin(scope, 5_020), 5_030)).getJSONArray("events").length() == 0)
        }
        val stopped = trace.begin(scope, 6_000)
        trace.clear()
        trace.begin(scope, 6_001)
        expect(trace.finish(stopped, 6_010) == null) // Same scope after stop is a new generation.
        for (index in 0..20) trace.record(scope, 7_000L + index, 7_000L + index, row())
        val capped = checkNotNull(trace.finish(trace.begin(scope, 7_100), 7_110)).getJSONArray("events")
        expect(capped.length() == 16)
        expect(capped.getJSONObject(0).getInt("sequence") == 6)
        val invalidTime = trace.begin(scope, 8_000)
        trace.record(scope, 8_001, 8_002, row())
        trace.record(scope, 8_001, 5_000, row())
        val invalidated = checkNotNull(trace.finish(invalidTime, 8_010))
        expect(!invalidated.getBoolean("stable"))
        expect(invalidated.getJSONArray("events").length() == 16) // Invalid events add no records.
        val detached = checkNotNull(trace.finish(trace.begin(scope, 8_020), 8_030))
        detached.getJSONArray("events").getJSONObject(0).put("source", "mutated_fixture")
        expect(checkNotNull(trace.finish(trace.begin(scope, 8_020), 8_030))
            .getJSONArray("events").getJSONObject(0).getString("source") == "synthetic_pager")
        return checks
    }
}
