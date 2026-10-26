package org.attentionstats.analytics.network

import org.attentionstats.analytics.time.TimeRange
import org.attentionstats.analytics.time.totalMs
import org.attentionstats.analytics.time.union

enum class Transport { WIFI, MOBILE }

/** One canonical OS bucket dimension. No subscriber IDs, payloads or endpoints. */
data class NetworkBucketKey(
    val uidEpoch: String,
    val transport: Transport,
    val range: TimeRange,
    val state: Int,
    val metered: Int,
    val roaming: Int,
    val defaultNetwork: Int,
) {
    init { require(uidEpoch.isNotBlank()) }
}

data class NetworkBucket(val key: NetworkBucketKey, val rxBytes: Long, val txBytes: Long) {
    init { require(rxBytes >= 0 && txBytes >= 0) }
}

data class NetworkProjection(
    val rxBytes: Long?,
    val txBytes: Long?,
    val bucketCount: Int,
    val partial: Boolean,
    val reasons: Set<String>,
    val accountedBucketRanges: List<TimeRange> = emptyList(),
)

/** Full canonical buckets only. Never prorate bytes into Reels or partial hours. */
fun networkUsage(
    uidEpoch: String,
    transport: Transport,
    query: TimeRange,
    accessGranted: Boolean,
    querySucceeded: Boolean,
    uniqueAppAttribution: Boolean,
    buckets: List<NetworkBucket>,
): NetworkProjection {
    if (!accessGranted) return NetworkProjection(null, null, 0, true, setOf("PERMISSION_REQUIRED"))
    if (!querySucceeded) return NetworkProjection(null, null, 0, true, setOf("SOURCE_UNAVAILABLE"))
    if (!uniqueAppAttribution) return NetworkProjection(null, null, 0, true, setOf("SHARED_OR_UNKNOWN_UID"))
    val reasons = mutableSetOf<String>()
    val relevant = buckets.filter { it.key.uidEpoch == uidEpoch && it.key.transport == transport }
        .filter { it.key.range.intersect(query) != null }
    val keys = relevant.map { it.key }.distinct().sortedBy { it.range.startMs }
    // Never mix an aggregate ALL dimension with its detailed partitions, or
    // overlapping historical snapshots that happen to have different bounds.
    for ((index, key) in keys.withIndex()) {
        for (nextIndex in index + 1 until keys.size) {
            val next = keys[nextIndex]
            if (next.range.startMs >= key.range.endMs) break
            val dimensions = listOf(key.state to next.state, key.metered to next.metered,
                key.roaming to next.roaming, key.defaultNetwork to next.defaultNetwork)
            if (dimensions.all { (a, b) -> a == b || a == -1 || b == -1 }) {
                return NetworkProjection(null, null, 0, true, setOf("OVERLAPPING_BUCKET_DIMENSIONS"))
            }
        }
    }
    var rx = 0L
    var tx = 0L
    var included = 0
    val accounted = mutableListOf<TimeRange>()
    for (group in relevant.groupBy { it.key }.values) {
        val versions = group.distinct()
        val key = versions.first().key
        if (key.range.intersect(query) == null) continue
        if (versions.size != 1) {
            reasons.add("CONFLICTING_BUCKET")
            continue
        }
        if (key.range.startMs < query.startMs || key.range.endMs > query.endMs) {
            reasons.add("PARTIAL_BUCKET_EXCLUDED")
            continue
        }
        val bucket = versions.single()
        rx = Math.addExact(rx, bucket.rxBytes)
        tx = Math.addExact(tx, bucket.txBytes)
        included++
        accounted.add(key.range)
    }
    // No returned buckets cannot distinguish absent OS history from genuine zero.
    if (included == 0) return NetworkProjection(null, null, 0, true, reasons + "NO_RETAINED_BUCKETS")
    val represented = union(accounted)
    if (totalMs(represented) < query.durationMs) reasons.add("REQUEST_NOT_FULLY_REPRESENTED")
    return NetworkProjection(rx, tx, included, reasons.isNotEmpty(), reasons, represented)
}
