package com.attentionstats.detectors.social.instagram

import com.attentionstats.detectors.DetectorDefinition
import com.attentionstats.detectors.SurfaceDetector
import com.attentionstats.detectors.SurfaceEvidence
import com.attentionstats.detectors.SurfaceObservation
import com.attentionstats.detectors.InlineSelection
import com.attentionstats.detectors.NodeRole
import com.attentionstats.detectors.ScrollEvidence
import org.attentionstats.reels.PageCategory
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface

/** Narrow installed-version viewer. Home-feed anchors are diagnostic candidates, never admission. */
internal object InstagramDetector : SurfaceDetector {
    override val definition = DetectorDefinition(
        ReelApp.INSTAGRAM, "452.1.0.54.64", 3, setOf("clips_viewer_view_pager"),
    )
    private val requiredIds = setOf("clips_viewer_view_pager", "clips_media_component", "clips_ufi_component")
    private val excludedIds = setOf("bottom_sheet_container", "comment_composer_parent_updated", "main_list_view")
    override val inlineAnchorIds = setOf("feed_tab", "media_group", "indicator_background_view", "row_feed_photo_imageview")
    override val inlineRootPagerIds = setOf("swipeable_tab_view_pager")
    override fun acceptsInlineScroll(evidence: ScrollEvidence): Boolean =
        evidence.role == NodeRole.RECYCLER && evidence.bounds.width > 0 && evidence.bounds.height > evidence.bounds.width

    override fun inlineSelection(evidence: SurfaceEvidence): InlineSelection? {
        if (!evidence.bounded || evidence.editable || !evidence.inlineComplete ||
            requiredIds.any { it in evidence.ids } || excludedIds.any { it in evidence.ids } ||
            "row_feed_profile_header" !in evidence.ids ||
            (evidence.inlineRootPage != null && evidence.inlineRootPage != 0) ||
            (evidence.inlineRootPage != 0 && evidence.inlineAnchors.none { it.id == "feed_tab" && it.selected })) return null
        val screen = evidence.viewport
        if (screen.width <= 0 || screen.height <= 0) return null
        val center = (screen.top + screen.bottom) / 2
        val items = evidence.inlineAnchors.filter { it.id == "media_group" &&
            it.bounds.width >= screen.width * 0.8 && it.bounds.height >= screen.height / 4 &&
            it.bounds.top <= center && it.bounds.bottom >= center }
        val item = items.singleOrNull() ?: return InlineSelection(null, null)
        fun inside(bounds: com.attentionstats.detectors.LayoutBounds): Boolean =
            bounds.width > 0 && bounds.height > 0 && bounds.left >= item.bounds.left && bounds.right <= item.bounds.right &&
                bounds.top >= item.bounds.top && bounds.bottom <= item.bounds.bottom
        val indicators = evidence.inlineAnchors.filter { it.id == "indicator_background_view" && inside(it.bounds) &&
            it.bounds.width in 12..screen.width / 4 && it.bounds.height >= 12 }
        val photo = evidence.inlineAnchors.any { it.id == "row_feed_photo_imageview" && inside(it.bounds) }
        return InlineSelection(item, indicators.singleOrNull(), photo)
    }
    override val diagnosticAnchorIds = setOf(
        "row_feed_photo_imageview", "row_feed_view_group_buttons", "row_feed_profile_header",
        "video_container", "media_group", "carousel_video_media_group", "row_feed_button_save",
        "row_feed_button_like", "row_feed_button_comment", "row_feed_button_share",
        "clips_container", "preview_container", "clips_items_view", "clips_viewer_view_pager",
        "clips_media_component", "clips_ufi_component",
        "indicator_background_view", "indicator_icon_view", "feed_preview_keep_watching_button",
        "feed_preview_watch_again_text", "feed_preview_keep_watching_text",
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
