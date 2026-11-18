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
    val mediaKind: String = if (category == PageCategory.PHOTO) "photo" else if (category == PageCategory.REEL) "video" else "unknown",
    val positionMs: Long? = null,
    val durationMs: Long? = null,
    /** Separate full-page advertisement, positively qualified by a source-specific UI marker. */
    val standaloneAd: Boolean = false,
    val standaloneAdKeys: Set<Long> = emptySet(),
    /** A viewer page that is itself a paid ad, shown by a small exact "Sponsored"/"Ad" badge on the page. */
    val skippableAd: Boolean = false,
) {
    /** Marks a located viewer ad page whose own badge qualifies it for an optional auto-scroll skip. */
    fun withSkippableAd(snapshot: ScreenSnapshot): AdapterFrame {
        val area = page ?: return this
        if (route != ReelRoute.VIEWER || category != PageCategory.AD) return this
        return copy(skippableAd = snapshot.within(area).any {
            org.attentionstats.detection.shared.Sponsored.badge(it, snapshot.screen.width)
        })
    }

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
