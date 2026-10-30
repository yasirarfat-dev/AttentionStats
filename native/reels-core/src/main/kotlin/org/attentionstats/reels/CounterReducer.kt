package org.attentionstats.reels

/**
 * Supported short-video sources. A source may ship under several package names
 * (regional builds, lite builds); the first package is the canonical one.
 */
enum class ReelApp(val key: String, val packages: List<String>, val displayName: String, val observationLabel: String) {
    INSTAGRAM("instagram", listOf("com.instagram.android"), "Instagram", "Instagram Reels"),
    FACEBOOK("facebook", listOf("com.facebook.katana"), "Facebook", "Facebook Reels"),
    YOUTUBE("youtube", listOf("com.google.android.youtube"), "YouTube", "YouTube Shorts"),
    TIKTOK("tiktok", listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go"),
        "TikTok", "TikTok videos"),
    SNAPCHAT("snapchat", listOf("com.snapchat.android"), "Snapchat", "Snapchat Spotlight");

    val packageName: String get() = packages.first()
    val sourceId: String get() = "reels.$key"
    fun owns(name: String?): Boolean = name != null && name in packages
    companion object {
        val allPackages: List<String> = entries.flatMap { it.packages }
        fun byKey(key: String): ReelApp? = entries.firstOrNull { it.key == key }
        fun byPackage(name: String): ReelApp? = entries.firstOrNull { name in it.packages }
    }
}

enum class Surface { REELS, EXCLUDED, OUTSIDE, UNKNOWN }

/** Where an encounter happened: a full-screen short-video viewer, or an inline video in a scrolling feed. */
enum class ReelRoute(val key: String) { VIEWER("reels_viewer"), HOME("home_video") }

enum class PageCategory(val key: String) {
    REEL("reel"), PHOTO("photo"), POST("post"), AD("ad"), UNCLASSIFIED("unclassified");
    /** Categories that count as a short video in headline totals (ads are a separate user choice). */
    val video: Boolean get() = this == REEL || this == UNCLASSIFIED
    companion object { fun byKey(key: String): PageCategory = entries.firstOrNull { it.key == key } ?: UNCLASSIFIED }
}

/** Timing/diary signal: what surface is visible right now. Identity never leaves memory. */
data class CounterSignal(val elapsedMs: Long, val surface: Surface, val page: Long?, val windowId: Int,
    val category: PageCategory = PageCategory.UNCLASSIFIED,
    val route: ReelRoute = ReelRoute.VIEWER, val freshEntry: Boolean = false)

/**
 * One adapter reading. [identity] is an in-memory fingerprint of the focused item;
 * [keys] are fingerprints of its individual metadata strings, used to tell a new item
 * from the same item whose controls changed (like/follow/mute toggles).
 * [scrollSequence] increases whenever the source app reported a scroll.
 */
data class ItemFrame(
    val atMs: Long,
    val surface: Surface,
    val route: ReelRoute,
    val identity: Long?,
    val keys: Set<Long>,
    val category: PageCategory,
    val scrollSequence: Long,
    val sameSlot: Boolean = false,
)

data class Encounter(val identity: Long, val route: ReelRoute, val category: PageCategory,
    val revisit: Boolean, val atMs: Long, val ordinal: Long)

/**
 * Turns adapter frames into encounters. Pure and deterministic; the caller owns storage.
 *
 * Rules:
 *  - An item must stay focused for a short dwell before it counts (filters fling-throughs).
 *  - The first item after entering a surface counts immediately after its dwell.
 *  - A changed fingerprint counts when the app scrolled since the last item, or when the
 *    item's metadata is mostly different (handles auto-advance without scroll events).
 *    Otherwise it is the same item whose controls changed, and it is adopted silently.
 *  - Items already seen in this session are reported as revisits, never as new encounters.
 */
class EncounterTracker(
    private val viewerDwellMs: Long = 300,
    private val feedDwellMs: Long = 450,
    private val memory: Int = 600,
) {
    private var current: Long? = null
    private var currentKeys: Set<Long> = emptySet()
    private var currentScroll = Long.MIN_VALUE
    private var currentRoute: ReelRoute? = null
    private var candidate: Long? = null
    private var candidateSince = 0L
    private var lastAt = Long.MIN_VALUE
    private var ordinal = 0L
    private val seen = object : LinkedHashMap<Long, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Boolean>?) = size > memory
    }

    val focused: Long? get() = current

    /** Leaving the app or surface: the next item counts as a fresh entry. Seen items persist. */
    fun leave() { current = null; currentKeys = emptySet(); candidate = null; currentRoute = null }

    /** New unlock session: forget everything. */
    fun reset() { leave(); seen.clear(); ordinal = 0; lastAt = Long.MIN_VALUE }

    fun observe(frame: ItemFrame): Encounter? {
        if (frame.atMs < lastAt) reset()
        lastAt = frame.atMs
        when (frame.surface) {
            Surface.OUTSIDE, Surface.UNKNOWN -> { leave(); return null }
            Surface.EXCLUDED -> { candidate = null; return null } // e.g. typing a comment; keep current item.
            Surface.REELS -> Unit
        }
        val identity = frame.identity ?: run { candidate = null; return null }
        if (currentRoute != null && currentRoute != frame.route) leave()
        if (identity == current) { candidate = null; currentKeys = frame.keys; return null }
        if (frame.sameSlot && current != null) {
            // The adapter proved this is the same on-screen item (continuous layout); adopt it.
            adopt(identity, frame); return null
        }
        if (candidate != identity) { candidate = identity; candidateSince = frame.atMs; return null }
        val dwell = if (frame.route == ReelRoute.VIEWER) viewerDwellMs else feedDwellMs
        if (frame.atMs - candidateSince < dwell) return null
        val fresh = current == null
        val moved = frame.scrollSequence != currentScroll
        if (!fresh && !moved && overlap(currentKeys, frame.keys) >= 0.5) { adopt(identity, frame); return null }
        val revisit = seen.containsKey(identity)
        adopt(identity, frame)
        seen[identity] = true
        if (!revisit) ordinal++
        return Encounter(identity, frame.route, frame.category, revisit, frame.atMs, ordinal)
    }

    private fun adopt(identity: Long, frame: ItemFrame) {
        current = identity; currentKeys = frame.keys; currentScroll = frame.scrollSequence
        currentRoute = frame.route; candidate = null
        if (seen.containsKey(identity)) seen[identity] = true
    }

    companion object {
        fun overlap(a: Set<Long>, b: Set<Long>): Double {
            if (a.isEmpty() || b.isEmpty()) return 0.0
            val common = a.count { it in b }
            return common.toDouble() / minOf(a.size, b.size)
        }
    }
}
