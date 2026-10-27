package com.attentionstats.qualification

import android.app.Activity
import android.app.Instrumentation
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
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

    fun verify(): String {
        if (Build.VERSION.SDK_INT < 28) error("Self queries require API 28")
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
        stage = "query"
        while (SystemClock.elapsedRealtime() < deadline) {
            val endAt = System.currentTimeMillis()
            // Abort on clock discontinuity instead of reading an unintended historical range.
            val elapsed = SystemClock.elapsedRealtime() - startedElapsed
            check(endAt > startedAt && endAt - startedAt <= 20_000)
            check(kotlin.math.abs((endAt - startedAt) - elapsed) < 1_000)
            val events = checkNotNull(usage.queryEventsForSelf(startedAt, endAt))
            val event = UsageEvents.Event()
            val copied = mutableListOf<FixtureEvent>()
            var scanned = 0
            while (events.hasNextEvent()) {
                check(++scanned <= 1_000)
                check(SystemClock.elapsedRealtime() < deadline)
                check(events.getNextEvent(event))
                check(event.packageName == context.packageName)
                check(event.timeStamp >= startedAt && event.timeStamp < endAt)
                if (event.className in fixtures) {
                    // Copy permitted scalars; the iterator overwrites event on its next call.
                    copied += FixtureEvent(event.className, event.eventType)
                }
            }
            // Values 1/2 are the legacy foreground/background aliases on API 28 as well.
            val resumed = copied.filter { it.kind == UsageEvents.Event.ACTIVITY_RESUMED }
                .map { it.activity }.toSet()
            val paused = copied.filter { it.kind == UsageEvents.Event.ACTIVITY_PAUSED }
                .map { it.activity }.toSet()
            resumedFixtures = resumed.size
            pausedFixtures = paused.size
            if (resumed == fixtures && paused == fixtures) {
                return "PASS: self-package usage probe; 2 synthetic activities resumed/paused; " +
                    "bounded iterator and copied scalar snapshots verified; no grant changes\n"
            }
            SystemClock.sleep(250)
        }
        stage = "missing_transitions"
        error("Synthetic self events unavailable within deadline")
    }

    private data class FixtureEvent(val activity: String?, val kind: Int)
}
