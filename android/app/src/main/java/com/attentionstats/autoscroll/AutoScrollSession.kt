package com.attentionstats.autoscroll

import android.os.SystemClock
import com.attentionstats.autoscroll.domain.CompletionGate
import org.attentionstats.reels.ReelApp
import org.json.JSONArray
import org.json.JSONObject

/** Ephemeral convenience session. Never changes observation consent, counts or history. Main thread only. */
internal object AutoScrollSession {
    data class Frame(val app: ReelApp, val window: Int, val page: Long?, val epoch: Long,
        val atMs: Long, val position: Double?, val eligible: Boolean) {
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
    @Volatile var attempts = 0
        private set
    @Volatile var confirmedTransitions = 0
        private set
    var onChanged: () -> Unit = {}

    fun candidate(app: ReelApp, version: String): Boolean = when (app) {
        ReelApp.INSTAGRAM -> version == "452.1.0.54.64"
        ReelApp.FACEBOOK -> version == "584.0.0.51.56"
        ReelApp.TIKTOK -> version == "47.5.3"
        else -> false
    }
    fun configure(value: Boolean, notice: Int) {
        require(!value || notice == 1)
        stop(if (value) "ready" else "off")
        latest = null
        enabled = value
        if (value) noticeAccepted = true
        onChanged()
    }
    fun start(): Boolean {
        val frame = latest ?: return false
        val key = frame.key() ?: return false
        if (!enabled || !usable(frame)) return false
        gate.reset(); target = key; waitingUntil = 0; scrollSettlingUntil = 0; running = true; revision++
        attempts = 0; confirmedTransitions = 0
        reason = "waiting_for_cycle"; onChanged(); return true
    }
    fun stop(why: String = "stopped") {
        val changed = running || reason != why
        running = false; target = null; waitingUntil = 0; scrollSettlingUntil = 0; gate.reset(); revision++; reason = why
        if (changed) onChanged()
    }
    fun clear() { stop("off"); enabled = false; noticeAccepted = false; latest = null; onChanged() }
    fun unavailable(reason: String) { latest = null; if (running) stop(reason) }
    fun expire(token: Long, now: Long) {
        if (current(token) && waitingUntil > 0 && now >= waitingUntil) stop("next_page_unconfirmed")
    }
    private fun fresh(frame: Frame): Boolean = SystemClock.elapsedRealtime() - frame.atMs in 0..750
    private fun usable(frame: Frame): Boolean = frame.eligible && frame.position?.let {
        it.isFinite() && it in 0.0..1.0
    } == true && fresh(frame)
    fun manualInteraction(reason: String) {
        // Our swipe can deliver pager callbacks after the new page is first visible.
        // Suppress only that bounded scroll tail. Clicks, context changes and Stop
        // remain immediate; an actual different page still fails the frame fence.
        if (running && (reason != "manual_scroll" ||
            (waitingUntil == 0L && SystemClock.elapsedRealtime() >= scrollSettlingUntil))) stop(reason)
    }
    fun frame(value: Frame, next: (Frame, Long) -> Unit) {
        latest = value
        if (!running) return
        val key = value.key()
        val previous = target
        if (!enabled || !value.eligible || !fresh(value) || previous == null || value.app.key != previous.app ||
            value.window != previous.window || value.epoch != previous.epoch) { stop("context_changed"); return }
        if (waitingUntil > 0) {
            if (value.atMs >= waitingUntil) { stop("next_page_unconfirmed"); return }
            if (value.position != null && (!value.position.isFinite() || value.position !in 0.0..1.0)) {
                stop("playback_unavailable"); return
            }
            if (key != null && key != previous) {
                confirmedTransitions++
                target = key; waitingUntil = 0; scrollSettlingUntil = value.atMs + 750
                // The page transition and readiness to scroll again are separate facts.
                // A photo or a player without progress can confirm arrival, then stop.
                if (value.position == null) { stop("playback_unavailable"); return }
                gate.reset(); reason = "waiting_for_cycle"; onChanged()
            }
            return
        }
        if (key == null || value.position == null || !usable(value)) { stop("playback_unavailable"); return }
        if (key != previous) { stop("manual_page_change"); return }
        if (gate.sample(key, value.atMs, value.position, true)) {
            attempts++
            waitingUntil = value.atMs + 2_500; reason = "advancing"; onChanged()
            next(value, revision)
        }
    }
    fun current(token: Long): Boolean = enabled && running && revision == token
    fun canStart(): Boolean = enabled && latest?.let {
        it.page != null && usable(it)
    } == true
    fun snapshot(versions: (ReelApp) -> String): String = JSONObject().put("contract", 1)
        .put("enabled", enabled).put("running", running).put("reason", reason)
        .put("sessionOnly", true).put("sources", JSONArray(ReelApp.entries.map { app ->
            JSONObject().put("app", app.key).put("candidate", candidate(app, versions(app)))
        })).toString()
}
