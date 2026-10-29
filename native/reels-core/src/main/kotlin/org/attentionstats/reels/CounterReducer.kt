package org.attentionstats.reels

enum class ReelApp(val key: String, val packageName: String, val displayName: String, val observationLabel: String) {
    INSTAGRAM("instagram", "com.instagram.android", "Instagram", "Instagram Reels"),
    FACEBOOK("facebook", "com.facebook.katana", "Facebook", "Facebook Reels"),
    YOUTUBE("youtube", "com.google.android.youtube", "YouTube Shorts", "YouTube Shorts"),
    TIKTOK("tiktok", "com.zhiliaoapp.musically", "TikTok", "TikTok videos");
    val sourceId: String get() = "reels.$key"
    companion object {
        fun byKey(key: String): ReelApp? = entries.firstOrNull { it.key == key }
        fun byPackage(name: String): ReelApp? = entries.firstOrNull { it.packageName == name }
    }
}

enum class Surface { REELS, EXCLUDED, OUTSIDE, UNKNOWN }
enum class ReelRoute(val key: String) { VIEWER("reels_viewer"), HOME("home_video") }
enum class PageCategory(val key: String) {
    REEL("reel"), PHOTO("photo"), POST("post"), AD("ad"), UNCLASSIFIED("unclassified");
}
data class CounterSignal(val elapsedMs: Long, val surface: Surface, val page: Long?, val windowId: Int,
    val category: PageCategory = PageCategory.UNCLASSIFIED,
    val route: ReelRoute = ReelRoute.VIEWER, val freshEntry: Boolean = false)
data class ExposureProposal(val sequence: Long, val page: Long, val elapsedMs: Long,
    val category: PageCategory = PageCategory.UNCLASSIFIED, val scrollBack: Boolean = false)

/** A validated adapter supplies page identity; this reducer never converts arbitrary swipes to reels. */
class CounterReducer(private var suppressInitial: Boolean = true) {
    private var hasCommittedInScope = suppressInitial
    private var lastTime = -1L
    private var candidatePage: Long? = null
    private var candidateCategory = PageCategory.UNCLASSIFIED
    private var candidateSince = 0L
    private var lastCommitted: Long? = null
    private var window: Int? = null
    private var pending: ExposureProposal? = null
    private var sequence = 0L
    private var initialSuppressed: Long? = null
    var visible = false
        private set

    fun reset(uncertain: Boolean = true) {
        candidatePage = null
        lastCommitted = null
        pending = null
        window = null
        initialSuppressed = null
        suppressInitial = uncertain
        visible = false
    }

    fun observe(signal: CounterSignal): ExposureProposal? {
        require(signal.elapsedMs >= 0)
        if (signal.elapsedMs < lastTime) reset()
        lastTime = signal.elapsedMs
        visible = false
        if (signal.surface == Surface.OUTSIDE || signal.surface == Surface.UNKNOWN) {
            reset(uncertain = hasCommittedInScope || suppressInitial)
            return null
        }
        if (signal.surface == Surface.EXCLUDED) {
            candidatePage = null
            pending = null
            return null // Preserve this episode's transient identity through a known exclusion.
        }
        val page = signal.page ?: run { candidatePage = null; return null }
        if (window != null && window != signal.windowId) reset()
        window = signal.windowId
        if (candidatePage != page || candidateCategory != signal.category) {
            candidatePage = page
            candidateCategory = signal.category
            candidateSince = signal.elapsedMs
            pending = null
            return null
        }
        if (signal.elapsedMs - candidateSince < 500) return null
        visible = true
        if (page == lastCommitted || pending != null) return null
        if (suppressInitial && initialSuppressed == null) {
            initialSuppressed = page
            return null
        }
        if (page == initialSuppressed && lastCommitted == null) return null
        return ExposureProposal(Math.addExact(sequence, 1), page, signal.elapsedMs, signal.category,
            lastCommitted?.let { page < it } ?: false).also { pending = it }
    }

    fun acknowledge(proposal: ExposureProposal) {
        check(pending == proposal)
        sequence = proposal.sequence
        hasCommittedInScope = true
        lastCommitted = proposal.page
        initialSuppressed = null
        suppressInitial = false
        pending = null
    }
}
