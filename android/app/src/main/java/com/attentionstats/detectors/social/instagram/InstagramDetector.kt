package com.attentionstats.detectors.social.instagram

import com.attentionstats.detectors.DetectorDefinition
import com.attentionstats.detectors.SurfaceDetector
import com.attentionstats.detectors.SurfaceEvidence
import com.attentionstats.detectors.SurfaceObservation
import org.attentionstats.reels.PageCategory
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface

/** Narrow installed-version viewer. Home-feed anchors are diagnostic candidates, never admission. */
internal object InstagramDetector : SurfaceDetector {
    override val definition = DetectorDefinition(
        ReelApp.INSTAGRAM, "452.1.0.54.64", 2, setOf("clips_viewer_view_pager"),
    )
    private val requiredIds = setOf("clips_viewer_view_pager", "clips_media_component", "clips_ufi_component")
    private val excludedIds = setOf("bottom_sheet_container", "comment_composer_parent_updated", "main_list_view")
    override val diagnosticAnchorIds = setOf(
        "row_feed_photo_imageview", "row_feed_view_group_buttons", "row_feed_profile_header",
        "video_container", "media_group", "carousel_video_media_group", "row_feed_button_save",
        "row_feed_button_like", "row_feed_button_comment", "row_feed_button_share",
        "clips_container", "preview_container", "clips_items_view", "clips_viewer_view_pager",
        "clips_media_component", "clips_ufi_component",
    )

    override fun detect(evidence: SurfaceEvidence): SurfaceObservation {
        val ids = evidence.ids
        val surface = when {
            evidence.editable || excludedIds.any { it in ids } -> Surface.EXCLUDED
            !evidence.bounded -> Surface.UNKNOWN
            requiredIds.all { it in ids } -> Surface.REELS
            definition.scrollSourceIds.any { it in ids } -> Surface.REELS // Viewer settling; withhold page.
            else -> Surface.OUTSIDE
        }
        if (surface != Surface.REELS || !requiredIds.all { it in ids }) return SurfaceObservation(surface)
        return SurfaceObservation(surface, evidence.settledPage, category(ids))
    }

    private fun category(ids: Set<String>): PageCategory {
        if ("clips_single_image_ads_media_content" in ids) return PageCategory.AD
        // Layout estimates do not establish universal sponsorship status.
        if (!ids.containsAll(setOf("save_button", "repost_count"))) return PageCategory.UNCLASSIFIED
        if (ids.containsAll(setOf("clips_carousel_viewpager", "clips_carousel_image_media_content")))
            return PageCategory.PHOTO
        if (ids.containsAll(setOf("clips_single_media_component", "clips_video_container")))
            return PageCategory.REEL
        return PageCategory.UNCLASSIFIED
    }
}
