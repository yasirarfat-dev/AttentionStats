package com.attentionstats.diary

/** Foreground activity estimates. Gaps, locks, source epochs and unpaired tails never bridge. */
internal object UsageTimeline {
    data class Event(val epoch: String, val app: String, val at: Long, val kind: Int)
    data class Interval(val app: String, val start: Long, val end: Long, val provisional: Boolean = false)
    data class Entry(val app: String, val at: Long)

    /** Observed app entries, not process launches. Activity handoffs within one app stay one entry. */
    fun entries(events: List<Event>, through: Long): List<Entry> {
        val output = mutableListOf<Entry>()
        var app: String? = null
        var epoch: String? = null
        var interactive = true
        var unlocked = true
        val ordered = events.sortedWith(compareBy<Event> { it.at }.thenBy {
            when (it.kind) { -1, 16, 17, 26, 27 -> 0; 15, 18 -> 1; else -> 2 }
        })
        for (event in ordered) {
            if (event.at >= through) break
            if (epoch != event.epoch) { app = null; epoch = event.epoch; interactive = true; unlocked = true }
            when (event.kind) {
                1 -> if (interactive && unlocked && event.app.isNotBlank() && app != event.app) {
                    output.add(Entry(event.app, event.at)); app = event.app
                }
                // Pausing/stopping one Activity does not mean departure from its app.
                15 -> interactive = true
                16 -> { app = null; interactive = false }
                17 -> { app = null; unlocked = false }
                18 -> unlocked = true
                -1, 26, 27 -> { app = null; interactive = false }
            }
        }
        return output
    }

    fun reduce(events: List<Event>, through: Long): List<Interval> {
        val output = mutableListOf<Interval>()
        var active: Event? = null
        var epoch: String? = null
        var interactive = true
        var unlocked = true
        fun close(at: Long, provisional: Boolean = false) {
            active?.let {
                val end = minOf(at, it.at + 21_600_000L)
                if (end > it.at) output.add(Interval(it.app, it.at, end, provisional))
            }
            active = null
        }
        val ordered = events.sortedWith(compareBy<Event> { it.at }.thenBy {
            // Apply power/lock boundaries before activity events at the same millisecond.
            // A resume delivered alongside screen-on/unlock must remain measurable.
            when (it.kind) { -1, 16, 17, 26, 27 -> 0; 15, 18 -> 1; 2, 23 -> 2; else -> 3 }
        })
        for (event in ordered) {
            if (event.at >= through) break
            if (epoch != event.epoch) { active = null; epoch = event.epoch; interactive = true; unlocked = true }
            when (event.kind) {
                1 -> if (interactive && unlocked && event.app.isNotBlank()) {
                    if (active?.app != event.app) { close(event.at); active = event }
                }
                2, 23 -> if (active?.app == event.app) close(event.at)
                15 -> interactive = true
                16 -> { close(event.at); interactive = false }
                17 -> { close(event.at); unlocked = false }
                18 -> unlocked = true
                -1, 26, 27 -> { close(event.at); interactive = false }
            }
        }
        active?.let { close(minOf(through, it.at + 60_000L), true) }
        return output
    }
}
