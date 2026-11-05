package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.attentionstats.protection.ScheduleOwner
import com.attentionstats.MainActivity
import com.attentionstats.reels.InterventionOverlay
import org.attentionstats.protection.AppGroup
import org.attentionstats.protection.BlockScope
import org.attentionstats.protection.BlockWindow
import org.attentionstats.protection.SchedulePlan
import org.json.JSONObject

/** Synthetic codec and attached native-view checks; no source access or saved rule changes. */
internal object ProtectionVerification {
    fun run(instrumentation: Instrumentation): Int {
        var checks = 0
        fun expect(value: Boolean) { check(value) { "protection check ${checks + 1}" }; checks++ }
        val legacy = """{"groups":[{"id":"social","name":"Social","packages":["com.example.social"]}],"windows":[{"id":"morning","groupId":"social","days":127,"startMinute":300,"endMinute":660,"enabled":true}]}"""
        val decoded = ScheduleOwner.decode(legacy)
        expect(decoded.windows.single().scope == BlockScope.APP && !decoded.windows.single().allDay)
        expect(ScheduleOwner.decode(ScheduleOwner.encode(decoded).toString()) == decoded)
        val group = AppGroup("shorts", "Short videos", setOf("com.google.android.youtube"))
        val plan = SchedulePlan(listOf(group), listOf(BlockWindow("all-day", group.id, 127, 0, 0, true, BlockScope.SHORT_VIDEOS, true)))
        expect(ScheduleOwner.decode(ScheduleOwner.encode(plan).toString()) == plan)
        val invalid = ScheduleOwner.encode(plan).apply { getJSONArray("groups").getJSONObject(0).put("packages", org.json.JSONArray(listOf("com.example.unknown"))) }
        expect(runCatching { ScheduleOwner.decode(invalid.toString()) }.isFailure)
        val fractional = JSONObject(legacy).apply { getJSONArray("windows").getJSONObject(0).put("startMinute", 300.5) }
        expect(runCatching { ScheduleOwner.decode(fractional.toString()) }.isFailure)
        val unknown = ScheduleOwner.encode(plan).apply { getJSONArray("windows").getJSONObject(0).put("scope", "unsupported") }
        expect(runCatching { ScheduleOwner.decode(unknown.toString()) }.isFailure)

        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun texts(view: View): List<TextView> = if (view is TextView) listOf(view) else if (view is ViewGroup)
            (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList()
        try {
            // The editor's async initial render must settle before replacing its content for this view trial.
            SystemClock.sleep(1_500)
            var now = 100L
            var primary = 0
            var secondary = 0
            lateinit var view: InterventionOverlay
            instrumentation.runOnMainSync {
                view = InterventionOverlay(activity, InterventionOverlay.Mode.BREAK, "Synthetic break", "Synthetic details", 3,
                    "Leave", "Continue", { primary++ }, { secondary++ }, { now })
                activity.setContentView(view)
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                expect(view.isAttachedToWindow)
                val button = texts(view).single { it.text.startsWith("Continue") }
                expect(!button.isEnabled)
                button.performClick()
                expect(secondary == 0 && primary == 0)
                now += 15_000 // Main-loop stalls cannot extend the countdown by missed ticks.
            }
            SystemClock.sleep(400)
            instrumentation.runOnMainSync {
                val buttons = texts(view)
                val button = buttons.single { it.text == "Continue" }
                expect(button.isEnabled)
                button.performClick(); button.performClick()
                buttons.single { it.text == "Leave" }.performClick()
                expect(secondary == 1 && primary == 0)
            }
            instrumentation.runOnMainSync {
                view = InterventionOverlay(activity, InterventionOverlay.Mode.BREAK, "Synthetic break", "Synthetic details", 30,
                    "Leave", "Continue", { primary++ }, { secondary++ })
                activity.setContentView(view)
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val button = texts(view).single { it.text == "Leave" }
                button.performClick(); button.performClick()
                expect(primary == 1 && secondary == 1)
                view.finish()
            }
            instrumentation.runOnMainSync {
                view = InterventionOverlay(activity, InterventionOverlay.Mode.BREAK, "Synthetic compact reminder", "Player controls stay available", 30,
                    "Leave", "Continue", { primary++ }, { secondary++ }, compact = true)
                activity.setContentView(view)
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val button = texts(view).single { it.text == "Continue" }
                expect(button.isEnabled) // Unconfirmed playback must never wait behind a cover.
                button.performClick()
                expect(secondary == 2 && primary == 1)
                view.finish()
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
        return checks
    }
}
