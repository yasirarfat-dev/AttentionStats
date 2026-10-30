package org.attentionstats.detection

import org.attentionstats.reels.PageCategory
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.ReelRoute
import org.attentionstats.reels.Surface

/**
 * What an adapter sees on one scan.
 * - [surface] REELS means a short video (or feed video) is on screen right now.
 * - [identity]/[keys] fingerprint the focused item (memory only; see [Fingerprint]).
 * - [slotKey] identifies the on-screen view hosting a feed item, for layout continuity.
 * - [progress] is normalized playback position (0..1) when the app exposes it.
 */
data class AdapterFrame(
    val surface: Surface,
    val route: ReelRoute = ReelRoute.VIEWER,
    val identity: Long? = null,
    val keys: Set<Long> = emptySet(),
    val category: PageCategory = PageCategory.REEL,
    val page: LayoutBounds? = null,
    val slotKey: Int? = null,
    val progress: Double? = null,
    val method: String = "",
) {
    companion object {
        val OUTSIDE = AdapterFrame(Surface.OUTSIDE)
        val TYPING = AdapterFrame(Surface.EXCLUDED, method = "typing")
    }
}

/**
 * One adapter per source app. Adapters are version-independent: they prefer known view IDs
 * when an app exposes them, and fall back to layout geometry (a full-screen vertical pager
 * with a right-hand action rail), which every short-video viewer shares.
 */
interface ShortFormAdapter {
    val app: ReelApp
    fun read(snapshot: ScreenSnapshot): AdapterFrame
}

object AdapterRegistry {
    private val adapters: Map<ReelApp, ShortFormAdapter> = listOf(
        org.attentionstats.detection.social.instagram.InstagramAdapter,
        org.attentionstats.detection.social.facebook.FacebookAdapter,
        org.attentionstats.detection.social.youtube.YouTubeAdapter,
        org.attentionstats.detection.social.tiktok.TikTokAdapter,
        org.attentionstats.detection.social.snapchat.SnapchatAdapter,
    ).associateBy { it.app }

    init { check(adapters.keys == ReelApp.entries.toSet()) { "every source needs an adapter" } }

    fun of(app: ReelApp): ShortFormAdapter = adapters.getValue(app)
}
