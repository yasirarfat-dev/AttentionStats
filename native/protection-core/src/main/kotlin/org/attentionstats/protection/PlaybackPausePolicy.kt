package org.attentionstats.protection

enum class PauseControlState { PLAYING, PAUSED, UNKNOWN }

/** Narrow candidate: a known player control, never captions, generic center taps or guessed IDs. */
object PlaybackPausePolicy {
    fun classify(source: String, controlId: String?, label: String?): PauseControlState {
        if (source != "instagram" || controlId != "clips_pause_button") return PauseControlState.UNKNOWN
        return when (label) {
            "Pause" -> PauseControlState.PLAYING
            "Play" -> PauseControlState.PAUSED
            else -> PauseControlState.UNKNOWN
        }
    }
}
