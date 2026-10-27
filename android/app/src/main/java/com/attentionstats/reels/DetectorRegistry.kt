package com.attentionstats.reels

import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.PageCategory

/** Only source-reviewed, live-qualified per-version definitions may enable counting. */
internal data class DetectorDefinition(
    val app: ReelApp,
    val versionName: String,
    val revision: Int,
    val requiredIds: Set<String>,
    val excludedIds: Set<String>,
    val scrollSourceIds: Set<String>,
)

internal object DetectorRegistry {
    // Narrow installed-version viewer only. Normal-feed posts are not admitted.
    val definitions: List<DetectorDefinition> = listOf(DetectorDefinition(
        ReelApp.INSTAGRAM, "452.1.0.54.64", 2,
        setOf("clips_viewer_view_pager", "clips_media_component", "clips_ufi_component"),
        setOf("bottom_sheet_container", "comment_composer_parent_updated", "main_list_view"),
        setOf("clips_viewer_view_pager"),
    ))
    fun definition(app: ReelApp, versionName: String): DetectorDefinition? =
        definitions.firstOrNull { it.app == app && it.versionName == versionName }

    fun category(app: ReelApp, ids: Set<String>): PageCategory {
        if (app != ReelApp.INSTAGRAM) return PageCategory.UNCLASSIFIED
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
