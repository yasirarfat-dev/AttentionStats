package com.attentionstats.qualification

import android.app.Activity
import android.app.Instrumentation
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.os.UserManager

/** Reads only this run's own-package synthetic activity events. Never calls queryEvents. */
class SelfUsageProbe(private val instrumentation: Instrumentation) {
    var stage = "preflight"
        private set
    var resumedFixtures = 0
        private set
    var pausedFixtures = 0
        private set
    val measurements = linkedMapOf<String, Long>(
        "query_calls" to 0, "query_total_ns" to 0, "query_max_ns" to 0,
        "iteration_total_ns" to 0, "iteration_max_ns" to 0,
        "completed_queries" to 0, "events_scanned_max" to 0,
        "cancel_before_calls" to 0, "cancel_after_scanned" to 0,
        "cancel_during_scanned" to 0, "cancelled_batches" to 0,
        "discarded_snapshots" to 0,
    )

    fun verify(): String {
        if (Build.VERSION.SDK_INT < 28) error("Self queries require API 28")
        check(Looper.myLooper() != Looper.getMainLooper())
        val context = instrumentation.targetContext
        check(context.packageName == "com.attentionstats.debug")
        check(context.getSystemService(UserManager::class.java).isUserUnlocked)
        val startedAt = System.currentTimeMillis()
        val startedElapsed = SystemClock.elapsedRealtime()
        val activities = mutableListOf<Activity>()
        stage = "fixtures"
        try {
            for (fixture in listOf(FirstFixtureActivity::class.java, SecondFixtureActivity::class.java)) {
                activities += instrumentation.startActivitySync(
                    Intent(context, fixture).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                instrumentation.waitForIdleSync()
                SystemClock.sleep(150)
            }
        } finally {
            // Finish only activities created by this probe; do not alter settings or other tasks.
            instrumentation.runOnMainSync { activities.asReversed().forEach { it.finish() } }
            instrumentation.waitForIdleSync()
        }
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val fixtures = setOf(FirstFixtureActivity::class.java.name, SecondFixtureActivity::class.java.name)
        val deadline = startedElapsed + 15_000
        fun checkClock() {
            val wall = System.currentTimeMillis() - startedAt
            val elapsed = SystemClock.elapsedRealtime() - startedElapsed
            check(wall in 1..20_000 && kotlin.math.abs(wall - elapsed) < 1_000)
            check(SystemClock.elapsedRealtime() < deadline)
        }

        // Cancellation is injected at deterministic boundaries on this worker thread.
        // queryEventsForSelf has no cancellation parameter; no in-flight interruption claim.
        fun read(endAt: Long, cancellation: String? = null): List<FixtureEvent>? {
            var cancelled = cancellation == "before"
            val copied = mutableListOf<FixtureEvent>()
            var scanned = 0
            fun stopped(): Boolean {
                if (!cancelled) {
                    checkClock()
                    return false
                }
                measurements["cancelled_batches"] = measurements.getValue("cancelled_batches") + 1
                measurements["discarded_snapshots"] =
                    measurements.getValue("discarded_snapshots") + copied.size
                when (cancellation) {
                    "before" -> check(scanned == 0)
                    "after" -> measurements["cancel_after_scanned"] = scanned.toLong()
                    "during" -> measurements["cancel_during_scanned"] = scanned.toLong()
                }
                copied.clear()
                return true
            }
            if (stopped()) return null
            check(endAt > startedAt && endAt <= System.currentTimeMillis())
            val queryStart = SystemClock.elapsedRealtimeNanos()
            val events = usage.queryEventsForSelf(startedAt, endAt)
            val queryNs = SystemClock.elapsedRealtimeNanos() - queryStart
            measurements["query_calls"] = measurements.getValue("query_calls") + 1
            measurements["query_total_ns"] = measurements.getValue("query_total_ns") + queryNs
            measurements["query_max_ns"] = maxOf(measurements.getValue("query_max_ns"), queryNs)
            checkNotNull(events)
            if (cancellation == "after") cancelled = true
            if (stopped()) return null
            val iterationStart = SystemClock.elapsedRealtimeNanos()
            val event = UsageEvents.Event()
            while (true) {
                if (stopped()) return null
                if (!events.hasNextEvent()) break
                check(++scanned <= 1_000)
                check(events.getNextEvent(event))
                check(event.packageName == context.packageName)
                check(event.timeStamp >= startedAt && event.timeStamp < endAt)
                if (event.className in fixtures) {
                    // Copy permitted scalars; the iterator overwrites event on its next call.
                    copied += FixtureEvent(event.className, event.eventType)
                    if (cancellation == "during") cancelled = true
                }
            }
            if (stopped()) return null
            val iterationNs = SystemClock.elapsedRealtimeNanos() - iterationStart
            measurements["iteration_total_ns"] = measurements.getValue("iteration_total_ns") + iterationNs
            measurements["iteration_max_ns"] = maxOf(measurements.getValue("iteration_max_ns"), iterationNs)
            measurements["completed_queries"] = measurements.getValue("completed_queries") + 1
            measurements["events_scanned_max"] = maxOf(measurements.getValue("events_scanned_max"), scanned.toLong())
            return copied
        }
        stage = "query"
        while (SystemClock.elapsedRealtime() < deadline) {
            val endAt = System.currentTimeMillis()
            val copied = checkNotNull(read(endAt))
            // Values 1/2 are the legacy foreground/background aliases on API 28 as well.
            val resumed = copied.filter { it.kind == UsageEvents.Event.ACTIVITY_RESUMED }
                .map { it.activity }.toSet()
            val paused = copied.filter { it.kind == UsageEvents.Event.ACTIVITY_PAUSED }
                .map { it.activity }.toSet()
            resumedFixtures = resumed.size
            pausedFixtures = paused.size
            if (resumed == fixtures && paused == fixtures) {
                stage = "cancellation"
                val callsBefore = measurements.getValue("query_calls")
                check(read(endAt, "before") == null)
                measurements["cancel_before_calls"] = measurements.getValue("query_calls") - callsBefore
                check(measurements.getValue("cancel_before_calls") == 0L)
                check(read(endAt, "after") == null)
                check(measurements.getValue("cancel_after_scanned") == 0L)
                check(read(endAt, "during") == null)
                check(measurements.getValue("cancel_during_scanned") in 1..1_000)
                check(measurements.getValue("cancelled_batches") == 3L)
                check(measurements.getValue("discarded_snapshots") == 1L)
                return "PASS: self-package usage probe; 2 synthetic activities resumed/paused; " +
                    "bounded iterator, copied snapshots and 3 cancellation boundaries verified; no grant changes\n"
            }
            SystemClock.sleep(250)
        }
        stage = "missing_transitions"
        error("Synthetic self events unavailable within deadline")
    }

    private data class FixtureEvent(val activity: String?, val kind: Int)
}
