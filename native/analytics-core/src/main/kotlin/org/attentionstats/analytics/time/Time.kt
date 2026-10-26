package org.attentionstats.analytics.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Closed-open UTC interval. Empty or inverted intervals cannot enter calculations. */
data class TimeRange(val startMs: Long, val endMs: Long) {
    init {
        require(startMs >= 0 && endMs > startMs) { "Invalid half-open interval" }
    }
    val durationMs: Long get() = endMs - startMs
    fun contains(atMs: Long): Boolean = atMs >= startMs && atMs < endMs
    fun intersect(other: TimeRange): TimeRange? {
        val start = maxOf(startMs, other.startMs)
        val end = minOf(endMs, other.endMs)
        return if (start < end) TimeRange(start, end) else null
    }
}

/** Union intervals before computing durations, never sum overlapping sessions. */
fun union(ranges: List<TimeRange>): List<TimeRange> {
    if (ranges.isEmpty()) return emptyList()
    val sorted = ranges.sortedWith(compareBy<TimeRange> { it.startMs }.thenBy { it.endMs })
    val result = mutableListOf<TimeRange>()
    var current = sorted.first()
    for (next in sorted.drop(1)) {
        if (next.startMs <= current.endMs) {
            current = TimeRange(current.startMs, maxOf(current.endMs, next.endMs))
        } else {
            result.add(current)
            current = next
        }
    }
    result.add(current)
    return result
}

fun totalMs(ranges: List<TimeRange>): Long =
    ranges.fold(0L) { total, range -> Math.addExact(total, range.durationMs) }

data class DaySlice(val date: LocalDate, val zoneId: String, val range: TimeRange)

/** Actual civil boundaries handle 23/25-hour days and skipped dates. */
fun splitByDay(range: TimeRange, zone: ZoneId): List<DaySlice> {
    val result = mutableListOf<DaySlice>()
    var cursor = range.startMs
    while (cursor < range.endMs) {
        val date = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
        val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        check(nextMidnight > cursor) { "Non-progressing calendar boundary" }
        val end = minOf(nextMidnight, range.endMs)
        result.add(DaySlice(date, zone.id, TimeRange(cursor, end)))
        cursor = end
    }
    return result
}
