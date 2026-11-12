package com.attentionstats.autoscroll

import android.os.SystemClock
import com.attentionstats.autoscroll.domain.CompletionGate
import org.attentionstats.reels.ReelApp
import org.json.JSONArray
import org.json.JSONObject

/** Ephemeral convenience session. Never changes observation consent, counts or history. Main thread only. */
internal object AutoScrollSession {
    data class Frame(val app: ReelApp, val window: Int, val page: Long?, val epoch: Long,
        val atMs: Long, val position: Double?, val eligible: Boolean, val mediaKind: String = "unknown") {
        fun key() = page?.let { CompletionGate.Context(app.key, window, it, epoch) }
    }
    private val gate = CompletionGate()
    @Volatile var enabled = false
        private set
    var noticeAccepted = false
        private set
    @Volatile var running = false
        private set
    var revision = 0L
        private set
    @Volatile var reason = "off"
        private set
    private var latest: Frame? = null
    private var target: CompletionGate.Context? = null
    private var waitingUntil = 0L
    private var scrollSettlingUntil = 0L
    /** Arrival of an explicitly classified photo without playback progress. Never a video timer. */
    private var stillSince = 0L
    private const val STILL_ITEM_MS = 5_000L
    @Volatile var attempts = 0
        private set
    @Volatile var confirmedTransitions = 0
        private set
    var onChanged: () -> Unit = {}
    /** Operator test hook: fixed stage codes only, never item identity or source content. */
    var onDispatchDiagnostic: ((String) -> Unit)? = null

    /** Source support is evidence-dependent; numeric playback or an explicit photo is required. */
    @Suppress("UNUSED_PARAMETER")
    fun candidate(app: ReelApp, version: String): Boolean = true
    fun configure(value: Boolean, notice: Int) {
        require(!value || notice == 1)
        stop(if (value) "ready" else "off")
        latest = null
        enabled = value
        if (value) noticeAccepted = true
        onChanged()
    }
    fun start(): Boolean {
        if (!enabled) return false
        if (waitingUntil > 0) onDispatchDiagnostic?.invoke("pending_reset_start")
        gate.reset(); target = null; waitingUntil = 0; scrollSettlingUntil = 0; stillSince = 0; running = true; revision++
        attempts = 0; confirmedTransitions = 0
        latest?.takeIf(::ready)?.let {
            target = it.key()
            if (still(it)) stillSince = it.atMs
        }
        reason = if (target == null) "waiting_for_playback" else if (stillSince > 0) "still_item" else "waiting_for_cycle"
        onChanged(); return true
    }
    /** Suspend dispatch and invalidate queued gestures without changing the user's On choice. */
    fun suspendActions(why: String) {
        if (waitingUntil > 0) onDispatchDiagnostic?.invoke("pending_suspend_$why")
        val changed = target != null || waitingUntil != 0L || reason != why
        target = null; waitingUntil = 0; scrollSettlingUntil = 0; stillSince = 0; gate.reset()
        if (changed) { revision++; reason = why; onChanged() }
    }
    fun stop(why: String = "stopped") {
        if (waitingUntil > 0) onDispatchDiagnostic?.invoke("pending_stop_$why")
        val changed = running || reason != why
        running = false; target = null; waitingUntil = 0; scrollSettlingUntil = 0; stillSince = 0; gate.reset(); revision++; reason = why
        if (changed) onChanged()
    }
    fun clear() { stop("off"); enabled = false; noticeAccepted = false; latest = null; onChanged() }
    fun unavailable(reason: String) { latest = null; if (running) suspendActions(reason) }
    /** Missing transition data cannot confirm arrival, but need not cancel our already sent swipe. */
    fun transientUnavailable(why: String, app: ReelApp, epoch: Long) {
        latest = null
        if (enabled && running && target?.app == app.key && target?.epoch == epoch &&
            waitingUntil > 0 && SystemClock.elapsedRealtime() < waitingUntil) {
            onDispatchDiagnostic?.invoke("arrival_acquisition_gap")
            return
        }
        if (running) suspendActions(why)
    }
    fun expire(token: Long, now: Long) {
        if (current(token) && waitingUntil > 0 && now >= waitingUntil) suspendActions("next_page_unconfirmed")
    }
    private fun fresh(frame: Frame): Boolean = SystemClock.elapsedRealtime() - frame.atMs in 0..750
    private fun usable(frame: Frame): Boolean = frame.eligible && frame.position?.let {
        it.isFinite() && it in 0.0..1.0
    } == true && fresh(frame)
    private fun still(frame: Frame): Boolean = frame.mediaKind == "photo" && frame.position == null
    private fun ready(frame: Frame): Boolean = usable(frame) || (frame.eligible && still(frame) && fresh(frame))
    fun manualInteraction(reason: String) {
        // Our swipe can deliver pager callbacks after the new page is first visible.
        // Suppress only that bounded scroll tail. Clicks, context changes and Stop
        // remain immediate; an actual different page still fails the frame fence.
        if (running && (reason != "manual_scroll" ||
            (waitingUntil == 0L && SystemClock.elapsedRealtime() >= scrollSettlingUntil))) suspendActions(reason)
    }
    fun frame(value: Frame, next: (Frame, Long) -> Unit) {
        latest = value
        if (!running) return
        val key = value.key()
        val previous = target
        if (previous == null) {
            if (!enabled || key == null || !ready(value)) { suspendActions("waiting_for_playback"); return }
            target = key; gate.reset(); stillSince = if (still(value)) value.atMs else 0
            reason = if (still(value)) "still_item" else "waiting_for_cycle"; revision++; onChanged()
            value.position?.let { gate.sample(key, value.atMs, it, true) }
            return
        }
        if (!enabled || !value.eligible || !fresh(value) || value.app.key != previous.app ||
            value.window != previous.window || value.epoch != previous.epoch) {
            onDispatchDiagnostic?.invoke(when {
                !enabled -> "frame_disabled"
                !value.eligible -> "frame_ineligible"
                !fresh(value) -> "frame_stale"
                value.app.key != previous.app -> "frame_source_changed"
                value.window != previous.window -> "frame_window_changed"
                else -> "frame_epoch_changed"
            })
            suspendActions("context_changed"); return
        }
        if (waitingUntil > 0) {
            if (value.atMs >= waitingUntil || SystemClock.elapsedRealtime() >= waitingUntil) { suspendActions("next_page_unconfirmed"); return }
            if (value.position != null && (!value.position.isFinite() || value.position !in 0.0..1.0)) {
                suspendActions("playback_unavailable"); return
            }
            if (key != null && key != previous) {
                confirmedTransitions++
                onDispatchDiagnostic?.invoke("arrival_confirmed")
                if (!ready(value)) { suspendActions("playback_unavailable"); return }
                target = key; waitingUntil = 0; scrollSettlingUntil = value.atMs + 750
                // The page transition and readiness to scroll again are separate facts.
                // A photo or image ad has no progress: show it briefly, then move on.
                gate.reset()
                if (still(value)) { stillSince = value.atMs; reason = "still_item" } else { stillSince = 0; reason = "waiting_for_cycle" }
                onChanged()
            }
            return
        }
        if (key != null && key != previous) { suspendActions("manual_page_change"); return }
        if (key != null && still(value)) {
            if (stillSince == 0L) stillSince = value.atMs
            if (value.atMs - stillSince >= STILL_ITEM_MS) {
                attempts++; stillSince = 0
                waitingUntil = value.atMs + 2_500; reason = "advancing"; onChanged()
                next(value, revision)
            }
            return
        }
        stillSince = 0
        if (key == null || value.position == null || !usable(value)) { suspendActions("playback_unavailable"); return }
        if (gate.sample(key, value.atMs, value.position, true)) {
            attempts++
            waitingUntil = value.atMs + 2_500; reason = "advancing"; onChanged()
            next(value, revision)
        }
    }
    fun current(token: Long): Boolean = enabled && running && target != null && revision == token
    fun canStart(): Boolean = enabled && latest?.let {
        it.page != null && ready(it)
    } == true
    fun snapshot(versions: (ReelApp) -> String): String = JSONObject().put("contract", 1)
        .put("enabled", enabled).put("running", running).put("reason", reason)
        .put("sessionOnly", true).put("sources", JSONArray(ReelApp.entries.map { app ->
            JSONObject().put("app", app.key).put("candidate", candidate(app, versions(app)))
        })).toString()
}
