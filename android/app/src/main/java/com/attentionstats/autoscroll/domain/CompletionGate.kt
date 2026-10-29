package com.attentionstats.autoscroll.domain

/** Memory-only decision. Time bounds freshness; elapsed time never causes scrolling. */
internal class CompletionGate {
    data class Context(val app: String, val window: Int, val page: Long, val epoch: Long)
    private var context: Context? = null
    private var lastAt = -1L
    private var lastPosition = 0.0
    private var advances = 0
    private var firstAt = -1L
    private var armed = false
    private var fired = false

    fun reset() {
        context = null; lastAt = -1; advances = 0; firstAt = -1; armed = false; fired = false
    }

    /** Only a separately qualified playback adapter may supply normalized positions. */
    fun sample(key: Context, atMs: Long, position: Double, eligible: Boolean): Boolean {
        if (!eligible || atMs < 0 || !position.isFinite() || position !in 0.0..1.0) {
            reset(); return false
        }
        if (context != key) { reset(); context = key }
        if (fired) return false
        if (lastAt >= 0 && (atMs <= lastAt || atMs - lastAt > 1_500)) {
            reset(); context = key
        }
        val previous = lastPosition
        val hadPrevious = lastAt >= 0
        val loop = hadPrevious && armed && previous >= 0.98 && position <= 0.05 &&
            advances >= 8 && atMs - firstAt >= 2_000
        if (loop) { fired = true; return true }
        // Discontinuities, seeking and restarts withhold an action. A fresh beginning
        // is required; a near-end sample or a paused value cannot arm the gate.
        if (hadPrevious && (position < previous || position - previous > 0.15)) {
            armed = false; advances = 0; firstAt = -1
        }
        if (!armed && position <= 0.05) { armed = true; firstAt = atMs; advances = 0 }
        if (armed && hadPrevious && position > previous && position - previous <= 0.15) advances++
        lastPosition = position; lastAt = atMs
        return false
    }
}
