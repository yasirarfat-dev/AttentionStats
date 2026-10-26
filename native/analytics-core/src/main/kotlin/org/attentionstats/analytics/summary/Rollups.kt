package org.attentionstats.analytics.summary

import org.attentionstats.analytics.time.TimeRange

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

enum class Granularity { DAY, WEEK, MONTH, YEAR }
enum class MetricUnit { COUNT, DURATION_MS, BYTES }
enum class EvidenceKind { OBSERVED, ESTIMATE, CLASSIFICATION, PREDICTION }

/** Facts are additive in an explicitly disjoint UTC partition. Null is unknown. */
data class SummaryFact(
    val id: String,
    val metric: String,
    val dimension: String,
    val unit: MetricUnit,
    val kind: EvidenceKind,
    val semanticsRevision: String,
    val range: TimeRange,
    val value: Long?,
    val knownMs: Long,
    val eligibleMs: Long,
) {
    init {
        require(listOf(id, metric, dimension, semanticsRevision).all { it.isNotBlank() })
        require(value == null || value >= 0)
        require(knownMs >= 0 && eligibleMs >= knownMs && eligibleMs <= range.durationMs)
    }
}

data class RollupKey(
    val metric: String,
    val dimension: String,
    val unit: MetricUnit,
    val kind: EvidenceKind,
    val semanticsRevision: String,
    val zoneId: String,
    val startMs: Long,
    val endMs: Long,
)

data class Rollup(
    val key: RollupKey,
    val coveredValue: Long?,
    val knownMs: Long,
    val eligibleMs: Long,
    val unknownFactCount: Long,
    val contributors: Long,
)

fun calendarBucket(atMs: Long, zone: ZoneId, granularity: Granularity, weekStart: DayOfWeek): TimeRange {
    val date = Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate()
    val startDate = when (granularity) {
        Granularity.DAY -> date
        Granularity.WEEK -> date.with(TemporalAdjusters.previousOrSame(weekStart))
        Granularity.MONTH -> date.withDayOfMonth(1)
        Granularity.YEAR -> date.withDayOfYear(1)
    }
    val endDate = when (granularity) {
        Granularity.DAY -> startDate.plusDays(1)
        Granularity.WEEK -> startDate.plusWeeks(1)
        Granularity.MONTH -> startDate.plusMonths(1)
        Granularity.YEAR -> startDate.plusYears(1)
    }
    return TimeRange(startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
        endDate.atStartOfDay(zone).toInstant().toEpochMilli())
}

/** Pure preview; not pruning, persistence, migration, encryption or restore. */
fun rollupFacts(
    facts: List<SummaryFact>,
    zone: ZoneId,
    granularity: Granularity,
    weekStart: DayOfWeek = DayOfWeek.MONDAY,
): List<Rollup> {
    val byIdentity = facts.groupBy { it.id }
    require(byIdentity.values.all { it.distinct().size == 1 }) { "Conflicting fact identity" }
    val unique = byIdentity.values.map { it.first() }
    val keyed = unique.groupBy { fact ->
        val bucket = calendarBucket(fact.range.startMs, zone, granularity, weekStart)
        require(fact.range.endMs <= bucket.endMs) { "Fact crosses target boundary; split at ingestion, never prorate" }
        RollupKey(fact.metric, fact.dimension, fact.unit, fact.kind, fact.semanticsRevision,
            zone.id, bucket.startMs, bucket.endMs)
    }
    return keyed.map { (key, group) ->
        val ordered = group.sortedBy { it.range.startMs }
        require(ordered.zipWithNext().all { (a, b) -> a.range.endMs <= b.range.startMs }) {
            "Overlapping fact partitions cannot be added"
        }
        val knownValues = group.mapNotNull { it.value }
        Rollup(key,
            if (knownValues.isEmpty()) null else knownValues.fold(0L, Math::addExact),
            group.fold(0L) { total, fact -> Math.addExact(total, fact.knownMs) },
            group.fold(0L) { total, fact -> Math.addExact(total, fact.eligibleMs) },
            group.count { it.value == null }.toLong(), group.size.toLong())
    }.sortedWith(compareBy<Rollup> { it.key.startMs }.thenBy { it.key.metric }
        .thenBy { it.key.dimension }.thenBy { it.key.semanticsRevision }.thenBy { it.key.kind.ordinal })
}

data class TimeEquivalent(val completeSessions: Long, val remainderMs: Long, val sessionMs: Long)

/** User-defined arithmetic, with no estimate of learning, health or productivity. */
fun timeEquivalent(coveredDurationMs: Long, chosenSessionMs: Long): TimeEquivalent {
    require(coveredDurationMs >= 0 && chosenSessionMs > 0)
    return TimeEquivalent(coveredDurationMs / chosenSessionMs, coveredDurationMs % chosenSessionMs, chosenSessionMs)
}
