package org.attentionstats.analytics.activity

import org.attentionstats.analytics.time.TimeRange
import org.attentionstats.analytics.time.totalMs
import org.attentionstats.analytics.time.union

data class AppInterval(val appId: String, val range: TimeRange) {
    init { require(appId.isNotBlank()) }
}

/** Primary surface only. Secondary labels, such as a comment panel, are separate facets. */
data class SurfaceInterval(val appId: String, val surfaceId: String, val range: TimeRange) {
    init { require(appId.isNotBlank() && surfaceId.isNotBlank()) }
}

data class SurfaceBreakdown(
    val appMs: Long,
    val bySurfaceMs: Map<String, Long>,
    val unclassifiedMs: Long,
    val conflictedMs: Long,
) {
    init {
        require(appMs >= 0 && unclassifiedMs >= 0 && conflictedMs in 0..unclassifiedMs)
        require(bySurfaceMs.values.all { it >= 0 })
        require(Math.addExact(bySurfaceMs.values.fold(0L, Math::addExact), unclassifiedMs) == appMs)
    }
}

/** Pure bounded-window projection. Inputs must already pass native consent and grant checks. */
fun surfaceBreakdown(
    appId: String,
    query: TimeRange,
    appIntervals: List<AppInterval>,
    surfaceIntervals: List<SurfaceInterval>,
): SurfaceBreakdown {
    val apps = union(appIntervals.filter { it.appId == appId }.mapNotNull { it.range.intersect(query) })
    val surfaces = surfaceIntervals.filter { it.appId == appId }.mapNotNull {
        it.range.intersect(query)?.let { clipped -> it.copy(range = clipped) }
    }
    val totals = sortedMapOf<String, Long>()
    var unknown = 0L
    var conflict = 0L
    // Sweep each disjoint app segment. Multiple intervals for the same surface
    // count once; simultaneous *different* primary surfaces fail to unclassified.
    for (app in apps) {
        val changes = sortedMapOf<Long, MutableMap<String, Int>>()
        changes[app.startMs] = mutableMapOf()
        changes[app.endMs] = mutableMapOf()
        for (surface in surfaces) {
            val clipped = surface.range.intersect(app) ?: continue
            val starts = changes.getOrPut(clipped.startMs) { mutableMapOf() }
            val ends = changes.getOrPut(clipped.endMs) { mutableMapOf() }
            starts[surface.surfaceId] = (starts[surface.surfaceId] ?: 0) + 1
            ends[surface.surfaceId] = (ends[surface.surfaceId] ?: 0) - 1
        }
        val active = mutableMapOf<String, Int>()
        var previous = app.startMs
        for ((at, deltas) in changes) {
            val duration = at - previous
            when (active.size) {
                1 -> {
                    val surface = active.keys.single()
                    totals[surface] = Math.addExact(totals[surface] ?: 0L, duration)
                }
                0 -> unknown = Math.addExact(unknown, duration)
                else -> {
                    unknown = Math.addExact(unknown, duration)
                    conflict = Math.addExact(conflict, duration)
                }
            }
            for ((surface, delta) in deltas) {
                val count = (active[surface] ?: 0) + delta
                check(count >= 0)
                if (count == 0) active.remove(surface) else active[surface] = count
            }
            previous = at
        }
        check(active.isEmpty())
    }
    return SurfaceBreakdown(totalMs(apps), totals.filterValues { it > 0 }, unknown, conflict)
}
