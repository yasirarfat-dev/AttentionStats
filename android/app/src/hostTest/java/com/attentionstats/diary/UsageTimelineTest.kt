package com.attentionstats.diary

private const val RESUMED = 1
private const val PAUSED = 2
private const val STOPPED = 23
private const val SCREEN_OFF = 16

fun main() {
    var checks = 0
    fun expect(value: Boolean, message: String) { check(value) { message }; checks++ }
    fun at(seconds: Int) = 1_000_000L + seconds * 1_000L
    fun ev(app: String, seconds: Int, kind: Int) = UsageTimeline.Event("e", app, at(seconds), kind)
    fun total(intervals: List<UsageTimeline.Interval>, app: String) =
        intervals.filter { it.app == app }.sumOf { it.end - it.start }

    // In-app navigation: the previous Activity's STOPPED arrives after the next one resumed.
    // Foreground time must continue until the app's own pause, not end at that stale stop.
    val handoff = listOf(
        ev("social", 0, RESUMED),
        ev("social", 60, PAUSED), ev("social", 60, RESUMED), ev("social", 61, STOPPED),
        ev("social", 90, PAUSED), ev("social", 90, RESUMED), ev("social", 91, STOPPED),
        ev("social", 120, PAUSED), ev("social", 121, STOPPED),
    )
    expect(total(UsageTimeline.reduce(handoff, at(600)), "social") == 120_000L, "activity handoffs keep app time")

    // Leaving to another app still ends the interval at the pause.
    val leave = listOf(ev("social", 0, RESUMED), ev("social", 30, PAUSED), ev("launcher", 30, RESUMED),
        ev("social", 31, STOPPED), ev("launcher", 50, PAUSED))
    val intervals = UsageTimeline.reduce(leave, at(600))
    expect(total(intervals, "social") == 30_000L, "pause ends app time")
    expect(total(intervals, "launcher") == 20_000L, "next app measured")

    // Screen off ends the interval even without a pause.
    val off = listOf(ev("social", 0, RESUMED), ev("", 40, SCREEN_OFF))
    expect(total(UsageTimeline.reduce(off, at(600)), "social") == 40_000L, "screen off ends app time")

    // An unpaired tail is provisional and capped.
    val tail = UsageTimeline.reduce(listOf(ev("social", 0, RESUMED)), at(600))
    expect(tail.single().provisional && tail.single().end - tail.single().start == 60_000L, "open tail provisional")

    println("PASS: $checks usage timeline checks")
}
