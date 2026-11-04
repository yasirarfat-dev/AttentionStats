package org.attentionstats.reels

/** Conservative playback evidence. No position/duration signal means unknown, never wall-time watching. */
class PlaybackEvidence {
    data class Result(val confirmedMs: Long, val qualified: Boolean, val completed: Boolean)
    private var position: Long? = null
    private var duration: Long? = null
    private var elapsed = 0L
    private var confirmed = 0L
    private var continuousFromStart = false
    private var complete = false
    private var observed = false
    fun reset() {
        position = null; duration = null; elapsed = 0; confirmed = 0
        continuousFromStart = false; complete = false; observed = false
    }
    fun sample(atMs: Long, positionMs: Long?, durationMs: Long?): Result? {
        if (positionMs == null || durationMs == null || durationMs !in 1_000..3_600_000 || positionMs !in 0..durationMs) {
            position = null; continuousFromStart = false
            return if (observed) Result(confirmed, confirmed >= 3_000, complete) else null
        }
        observed = true
        val before = position
        if (before == null || duration != durationMs || atMs < elapsed) {
            position = positionMs; duration = durationMs; elapsed = atMs
            continuousFromStart = confirmed == 0L && positionMs <= 1_000
            return Result(confirmed, confirmed >= 3_000, complete)
        }
        val wall = atMs - elapsed
        val advance = positionMs - before
        if (advance == 0L && wall <= 2_500) return Result(confirmed, confirmed >= 3_000, complete)
        if (wall in 1..2_500 && advance in 1..(wall + 500)) {
            confirmed = (confirmed + minOf(advance, wall)).coerceAtMost(86_400_000)
            if (continuousFromStart && positionMs >= durationMs - 500 && confirmed >= durationMs - 1_000) complete = true
        } else {
            // Pause/buffering, seek, missing callbacks, loop or clock discontinuity: don't fill the gap.
            continuousFromStart = false
        }
        position = positionMs; duration = durationMs; elapsed = atMs
        return Result(confirmed, confirmed >= 3_000, complete)
    }
}
