package com.attentionstats.autoscroll.domain

/** Memory-only decision. Time bounds freshness; elapsed time never causes scrolling. */
internal class CompletionGate {
    data class Context(val app: String, val window: Int, val page: Long, val epoch: Long)
    private var context: Context? = null
    private var lastAt = -1L
    private var lastPosition = 0.0
    private var advances = 0
    private var firstAt = -1L
    private var firstPosition = 0.0
    /** Smallest observed forward step: the label/bar quantization (1 s on YouTube's spoken seek bar). */
    private var quantum = 0.0
    /** Equal consecutive samples between advances: evidence that the signal is quantized, not continuous. */
    private var plateaus = 0
    private var armed = false
    private var fired = false
    private var terminalSamples = 0

    fun reset() {
        context = null; lastAt = -1; advances = 0; firstAt = -1; armed = false; fired = false; terminalSamples = 0; quantum = 0.0; plateaus = 0
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
        if (hadPrevious && armed && advances >= 8 && atMs - firstAt >= 2_000 &&
            previous >= 0.85 && position <= 0.10 && atMs - lastAt <= 1_500) {
            val rate = (previous - firstPosition) / (lastAt - firstAt)
            if (rate > 0 && rate.isFinite() && previous + rate * (atMs - lastAt) + (if (plateaus >= 2 && quantum <= 0.1) quantum else 0.0) >= 0.97) {
                fired = true; return true
            }
        }
        // Discontinuities and seeking require fresh observed advancement;
        // an end-only sample or a paused value cannot establish completion.
        if (hadPrevious && (position < previous || position - previous > 0.15)) {
            armed = false; advances = 0; firstAt = -1; terminalSamples = 0; quantum = 0.0; plateaus = 0
        }
        // A non-looping player can stop at its quantized last step (Facebook: 999/1000).
        // Require observed playback advances before two terminal samples; a frozen/end-only
        // first frame, seek jump or paused partial position cannot establish completion.
        if (!armed && position < 0.999) { armed = true; firstAt = atMs; firstPosition = position; advances = 0 }
        if (armed && hadPrevious && position > previous && position - previous <= 0.15) {
            advances++
            quantum = if (quantum == 0.0) position - previous else minOf(quantum, position - previous)
        } else if (armed && hadPrevious && position == previous && advances > 0 && quantum > 0) plateaus++
        terminalSamples = if (armed && position >= 0.999 && advances >= 8 && atMs - firstAt >= 2_000)
            terminalSamples + 1 else 0
        if (terminalSamples >= 2) { fired = true; return true }
        lastPosition = position; lastAt = atMs
        return false
    }
}
