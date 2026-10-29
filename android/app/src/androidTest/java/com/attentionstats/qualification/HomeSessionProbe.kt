package com.attentionstats.qualification

import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import com.attentionstats.reels.ReelsAccessibilityService
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.ReelRoute
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** One continuous Home test: process/service/feed state survives every phase. Host supplies gestures. */
internal object HomeSessionProbe {
    fun verify(instrumentation: Instrumentation, appKey: String): Bundle {
        val app = checkNotNull(ReelApp.byKey(appKey))
        check(app in setOf(ReelApp.INSTAGRAM, ReelApp.FACEBOOK))
        val coordinator = ReelsCoordinator.get(instrumentation.targetContext)
        val phaseFile = File(instrumentation.targetContext.noBackupFilesDir, "reels-home-phase")
        val result = Bundle()
        val phases = linkedMapOf<String, JSONObject>()
        fun snapshot(): JSONObject {
            val done = CountDownLatch(1)
            var value: JSONObject? = null
            coordinator.snapshot({ value = JSONObject(it); done.countDown() }, { done.countDown() })
            check(done.await(15, TimeUnit.SECONDS))
            return checkNotNull(value)
        }
        fun source(value: JSONObject): JSONObject {
            val rows = value.getJSONArray("sources")
            return (0 until rows.length()).map { rows.getJSONObject(it) }.single { it.getString("app") == appKey }
        }
        fun home(): Boolean = coordinator.liveOverlay?.let { it.app == app && it.route == ReelRoute.HOME } == true
        fun committed(): Boolean = coordinator.liveOverlay?.let { it.app == app && it.route == ReelRoute.HOME && it.visit > 0 } == true
        fun begin(phase: String): Pair<JSONObject, Boolean> {
            var value = snapshot()
            val readyUntil = SystemClock.elapsedRealtime() + 15_000
            while ((!value.getBoolean("serviceConnected") || value.getString("storage") != "ready") &&
                SystemClock.elapsedRealtime() < readyUntil) {
                SystemClock.sleep(500)
                value = snapshot()
            }
            val selected = source(value)
            check(value.getBoolean("accessibilityEnabled") && value.getBoolean("serviceConnected") &&
                value.getString("storage") == "ready" && selected.getBoolean("enabled") &&
                selected.getBoolean("consentCurrent") && selected.getBoolean("supported") && !selected.getBoolean("paused"))
            val wasHome = home() && committed()
            instrumentation.sendStatus(0, Bundle().apply { putString("home_phase_ready", phase) })
            return value to wasHome
        }
        fun end(phase: String, baseline: Pair<JSONObject, Boolean>) {
            val after = snapshot()
            check(after.getString("revision") == baseline.first.getString("revision") &&
                after.getString("day") == baseline.first.getString("day"))
            val selected = source(after)
            val delta = selected.getString("today").toLong() - source(baseline.first).getString("today").toLong()
            check(delta in 0..20)
            val categories = selected.getJSONObject("counts")
            check(listOf("reel", "photo", "ad", "unclassified").sumOf { categories.getString(it).toLong() } == selected.getString("today").toLong())
            val value = JSONObject().put("phase", phase).put("added", delta).put("committedAtStart", baseline.second)
                .put("homeAtEnd", home()).put("committedAtEnd", committed()).put("categoriesConserved", true)
            phases[phase] = value
            instrumentation.sendStatus(0, Bundle().apply { putString("home_phase_result", value.toString()) })
            when (phase) {
                "find", "return" -> check(home() && committed())
                "idle", "small" -> check(baseline.second && home() && delta == 0L)
                "outside" -> check(baseline.second && !home() && delta == 0L)
            }
        }
        try {
            var phase = "find"
            var baseline = begin(phase)
            val until = SystemClock.elapsedRealtime() + 210_000
            var finished = false
            while (SystemClock.elapsedRealtime() < until) {
                val next = if (phaseFile.isFile && phaseFile.length() <= 16) phaseFile.readText().trim() else phase
                check(next in setOf("find", "idle", "small", "outside", "return", "finish"))
                if (next != phase) {
                    end(phase, baseline)
                    if (next == "finish") { finished = true; break }
                    phase = next
                    baseline = begin(phase)
                }
                instrumentation.sendStatus(0, Bundle().apply { putString("home_scan", ReelsAccessibilityService.inspectHomeForOperator().toString()) })
                SystemClock.sleep(750)
            }
            check(finished && phases.keys == setOf("find", "idle", "small", "outside", "return"))
            result.putString("home_results", JSONObject(phases.toMap()).toString())
            result.putString("stream", "PASS: continuous Home encounter checks\n")
            return result
        } finally { phaseFile.delete() }
    }
}
