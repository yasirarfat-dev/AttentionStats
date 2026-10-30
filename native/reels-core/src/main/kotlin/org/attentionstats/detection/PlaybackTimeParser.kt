package org.attentionstats.detection

/** Exact numeric player formats only; unmatched values are discarded without logging. */
object PlaybackTimeParser {
    private val clock = "([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)"
    private val pair = Regex("(?:Video progress[, :]+|Playback position[, :]+)?$clock\\s*(?:/|of|out of)\\s*$clock", RegexOption.IGNORE_CASE)
    private val single = Regex("(?:Video progress[, :]+|Playback position[, :]+)?$clock", RegexOption.IGNORE_CASE)
    fun elapsed(value: CharSequence?): Long? {
        if (value == null || value.length !in 4..80) return null
        val text = single.matchEntire(value)?.groupValues?.get(1) ?: return null
        val parts = text.split(':').map { it.toLong() }
        if (parts.drop(1).any { it > 59 }) return null
        return parts.fold(0L) { sum, part -> sum * 60 + part }.times(1_000).takeIf { it <= 3_600_000 }
    }
    fun parse(value: CharSequence?): Pair<Long, Long>? {
        if (value == null || value.length !in 7..80) return null
        val match = pair.matchEntire(value) ?: return null
        fun millis(text: String): Long? {
            val parts = text.split(':').map { it.toLong() }
            if (parts.drop(1).any { it > 59 }) return null
            return parts.fold(0L) { sum, part -> sum * 60 + part } * 1_000
        }
        val elapsed = millis(match.groupValues[1]) ?: return null
        val duration = millis(match.groupValues[2]) ?: return null
        return if (duration in 1_000..3_600_000 && elapsed in 0..duration) elapsed to duration else null
    }
}
