package com.attentionstats.qualification

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.attentionstats.detectors.DetectorRegistry
import com.attentionstats.detectors.SurfaceEvidence
import com.attentionstats.detectors.accessibility.StructuralSnapshotReader
import com.attentionstats.detectors.accessibility.DiagnosticScrollTrace
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

        val node = AccessibilityNodeInfo.obtain()
        try {
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
