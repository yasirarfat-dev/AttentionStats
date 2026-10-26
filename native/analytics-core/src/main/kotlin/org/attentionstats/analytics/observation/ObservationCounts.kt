package org.attentionstats.analytics.observation

import org.attentionstats.analytics.time.TimeRange
import org.attentionstats.analytics.time.totalMs
import org.attentionstats.analytics.time.union

enum class Eligibility { QUALIFIED, EXCLUDED, AMBIGUOUS }

/** Already-normalized evidence, not a raw callback or a content fingerprint. */
data class ExposureObservation(
    val appId: String,
    val surfaceId: String,
    val episodeId: String,
    val transitionOrdinal: Long,
    val occurredAtMs: Long,
    val eligibility: Eligibility,
) {
    init {
        require(listOf(appId, surfaceId, episodeId).all { it.isNotBlank() })
        require(transitionOrdinal >= 0 && occurredAtMs >= 0)
    }
    internal val key get() = ExposureKey(appId, surfaceId, episodeId, transitionOrdinal)
}

internal data class ExposureKey(val appId: String, val surfaceId: String, val episodeId: String, val ordinal: Long)
data class CountProjection(val value: Long?, val partial: Boolean, val reasonCodes: Set<String>)

/** Unknown support is null. Only explicit detector qualification permits counts. */
fun exposureCount(
    appId: String,
    surfaceId: String,
    query: TimeRange,
    detectorQualified: Boolean,
    coveredRanges: List<TimeRange>,
    observations: List<ExposureObservation>,
): CountProjection {
    if (!detectorQualified) return CountProjection(null, true, setOf("DETECTOR_UNQUALIFIED"))
    val coverage = union(coveredRanges.mapNotNull { it.intersect(query) })
    val reasons = mutableSetOf<String>()
    if (totalMs(coverage) < query.durationMs) reasons.add("COVERAGE_GAP")
    if (coverage.isEmpty()) return CountProjection(null, true, setOf("COVERAGE_GAP"))
    var count = 0L
    // Group before range filtering so contradictory replay timestamps cannot put
    // one transition in two days. Persistence must enforce this identity too.
    val groups = observations.filter { it.appId == appId && it.surfaceId == surfaceId }.groupBy { it.key }
    for (group in groups.values) {
        val versions = group.distinct()
        if (versions.size != 1) {
            if (versions.any { query.contains(it.occurredAtMs) }) reasons.add("CONFLICTING_TRANSITION")
            continue
        }
        val event = versions.single()
        if (!query.contains(event.occurredAtMs)) continue
        if (coverage.none { it.contains(event.occurredAtMs) }) {
            reasons.add("EVENT_OUTSIDE_COVERAGE")
            continue
        }
        when (event.eligibility) {
            Eligibility.QUALIFIED -> count = Math.addExact(count, 1L)
            Eligibility.AMBIGUOUS -> reasons.add("AMBIGUOUS_ELIGIBILITY")
            Eligibility.EXCLUDED -> Unit
        }
    }
    return CountProjection(count, reasons.isNotEmpty(), reasons)
}

enum class InteractionAction { LIKE, UNLIKE, SAVE, UNSAVE, COMMENT_SUBMIT, SHARE_OPEN, SHARE_HANDOFF, FOLLOW, UNFOLLOW }
enum class InteractionEvidence { INTENT_ONLY, OBSERVED_UI_OUTCOME }

/** No text, account name, target post identity or message body is accepted. */
data class InteractionObservation(
    val eventId: String,
    val appId: String,
    val action: InteractionAction,
    val occurredAtMs: Long,
    val evidence: InteractionEvidence,
) {
    init { require(eventId.isNotBlank() && appId.isNotBlank() && occurredAtMs >= 0) }
}

data class InteractionProjection(
    val observedOutcomes: Map<InteractionAction, Long>,
    val intents: Map<InteractionAction, Long>,
    val conflictingIds: Int,
)

fun interactionCounts(appId: String, query: TimeRange, observations: List<InteractionObservation>): InteractionProjection {
    val outcomes = mutableMapOf<InteractionAction, Long>()
    val intents = mutableMapOf<InteractionAction, Long>()
    var conflicts = 0
    for (group in observations.groupBy { it.eventId }.values) {
        val versions = group.distinct()
        if (versions.size != 1) {
            if (versions.any { it.appId == appId && query.contains(it.occurredAtMs) }) conflicts++
            continue
        }
        val event = versions.single()
        if (event.appId != appId || !query.contains(event.occurredAtMs)) continue
        val destination = when (event.evidence) {
            InteractionEvidence.OBSERVED_UI_OUTCOME -> outcomes
            InteractionEvidence.INTENT_ONLY -> intents
        }
        destination[event.action] = Math.addExact(destination[event.action] ?: 0L, 1L)
    }
    return InteractionProjection(outcomes, intents, conflicts)
}
