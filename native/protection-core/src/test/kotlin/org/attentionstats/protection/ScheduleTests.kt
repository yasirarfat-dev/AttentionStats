package org.attentionstats.protection

import java.time.ZonedDateTime

fun main() {
    var checks = 0
    fun expect(value: Boolean, label: String) { check(value) { label }; checks++ }
    val monday = 1
    val window = BlockWindow("evening", "social", monday, 19 * 60, 23 * 60, true)
    fun at(value: String) = ZonedDateTime.parse(value)
    expect(!window.active(at("2026-11-02T18:59:59+06:00")), "before start")
    expect(window.active(at("2026-11-02T19:00:00+06:00")), "inclusive start")
    expect(window.active(at("2026-11-02T22:59:59+06:00")), "inside")
    expect(!window.active(at("2026-11-02T23:00:00+06:00")), "exclusive end")
    expect(!window.active(at("2026-11-03T20:00:00+06:00")), "unselected day")
    expect(!window.copy(enabled = false).active(at("2026-11-02T20:00:00+06:00")), "disabled")
    val night = window.copy(startMinute = 23 * 60, endMinute = 7 * 60)
    expect(night.active(at("2026-11-02T23:00:00+06:00")), "overnight start")
    expect(night.active(at("2026-11-03T06:59:00+06:00")), "previous start weekday")
    expect(!night.active(at("2026-11-02T06:59:00+06:00")), "no unselected Sunday carry")
    expect(!night.active(at("2026-11-03T07:00:00+06:00")), "overnight end")
    val sunday = night.copy(days = 64)
    expect(sunday.active(at("2026-11-02T06:00:00+06:00")), "Sunday to Monday wrap")
    val dst = night.copy(days = 64, startMinute = 60, endMinute = 180)
    expect(dst.active(at("2026-11-01T01:30:00-04:00[America/New_York]")), "first repeated hour")
    expect(dst.active(at("2026-11-01T01:30:00-05:00[America/New_York]")), "second repeated hour")
    val excluded = setOf("com.example.recovery")
    val group = AppGroup("social", "Social", setOf("com.example.social", "com.example.media"))
    val plan = SchedulePlan(listOf(group), listOf(window, window.copy(id = "lunch", startMinute = 720, endMinute = 780)))
    plan.validate(excluded)
    expect(plan.matching("com.example.social", at("2026-11-02T20:00:00+06:00"), excluded).size == 1, "group match")
    expect(plan.matching("com.example.media", at("2026-11-02T12:30:00+06:00"), excluded).size == 1, "second app and window")
    expect(plan.matching("com.example.other", at("2026-11-02T20:00:00+06:00"), excluded).isEmpty(), "unselected app")
    expect(plan.matching("com.example.social", at("2026-11-02T20:00:00+06:00"), setOf("com.example.social")).isEmpty(), "invariant exclusion")
    expect(plan.targets() == group.packages, "event targets only enabled groups")
    fun rejects(value: SchedulePlan, label: String) { expect(runCatching { value.validate(excluded) }.isFailure, label) }
    rejects(plan.copy(windows = listOf(window.copy(startMinute = 1380, endMinute = 1380))), "ambiguous full day")
    rejects(plan.copy(windows = listOf(window.copy(days = 0))), "empty weekdays")
    rejects(plan.copy(windows = listOf(window.copy(groupId = "missing"))), "orphan window")
    rejects(plan.copy(groups = listOf(group, group)), "duplicate group")
    rejects(plan.copy(windows = listOf(window, window)), "duplicate window")
    rejects(plan.copy(groups = listOf(group.copy(packages = excluded))), "recovery cannot be targeted")
    rejects(plan.copy(windows = listOf(window.copy(endMinute = 1440))), "invalid minute")
    rejects(plan.copy(groups = listOf(group.copy(packages = emptySet()))), "empty group")
    expect(plan.copy(windows = listOf(window.copy(enabled = false))).targets().isEmpty(), "disabled no events")
    println("PASS: $checks schedule boundary, overlap, validation and recovery checks")
}
