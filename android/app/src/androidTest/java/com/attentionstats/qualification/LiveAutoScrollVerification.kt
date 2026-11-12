package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.attentionstats.autoscroll.AutoScrollSession
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.detection.AdapterRegistry
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.ReelRoute
import org.attentionstats.reels.Surface
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Operator-authorized source trial: bounded setup, real HUD controls, at most one auto transition. */
internal object LiveAutoScrollVerification {
    fun run(instrumentation: Instrumentation, key: String, useCurrent: Boolean = false): String {
        val context = instrumentation.targetContext
        val owner = ReelsCoordinator.get(context)
        val app = checkNotNull(ReelApp.byKey(key))
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun main(action: () -> Unit) {
            var failure: Throwable? = null
            instrumentation.runOnMainSync { try { action() } catch (caught: Throwable) { failure = caught } }
            failure?.let { throw it }
        }
        fun await(call: ((String) -> Unit, (String) -> Unit) -> Unit): JSONObject {
            val done = CountDownLatch(1); var raw: String? = null
            call({ raw = it; done.countDown() }, { done.countDown() })
            check(done.await(15, TimeUnit.SECONDS)) { "native operation timeout" }
            return JSONObject(checkNotNull(raw) { "native operation unavailable" })
        }
        val original = await { success, failure -> owner.diarySnapshot(1, success, failure) }
        fun reminder(enabled: Boolean) {
            val revision = await { success, failure -> owner.diarySnapshot(1, success, failure) }.getString("revision").toLong()
            await { success, failure -> owner.configureDiary("reminder", enabled, original.getInt("reminderCount"),
                original.getInt("reminderMinutes"), revision, success, failure) }
        }
        fun frame(): org.attentionstats.detection.AdapterFrame {
            fun read(root: android.view.accessibility.AccessibilityNodeInfo): org.attentionstats.detection.AdapterFrame {
                try {
                    check(app.owns(root.packageName?.toString())) { "selected source left foreground" }
                    val snapshot = SnapshotReader.read(root, app.packages)
                    check(snapshot.complete && !snapshot.typing) { "incomplete/editable context" }
                    return AdapterRegistry.of(app).read(snapshot)
                } finally { SnapshotReader.recycle(root) }
            }
            val until = SystemClock.elapsedRealtime() + 3_000
            do {
                for (window in automation.windows) {
                    if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isFocused) continue
                    val root = window.root ?: continue
                    return read(root)
                }
                val active = automation.rootInActiveWindow
                if (active != null) {
                    if (app.owns(active.packageName?.toString())) return read(active)
                    SnapshotReader.recycle(active)
                }
                SystemClock.sleep(100)
            } while (SystemClock.elapsedRealtime() < until)
            error("source window unavailable")
        }
        val serviceUntil = SystemClock.elapsedRealtime() + 10_000
        while (!owner.serviceConnected && SystemClock.elapsedRealtime() < serviceUntil) SystemClock.sleep(100)
        check(owner.serviceConnected) { "accessibility service not connected" }
        check(owner.allowed(app)) { "source consent unavailable" }
        check(owner.overlayEnabled) { "HUD disabled" }
        check(app !in owner.blockChoices()) { "existing source block preserved" }
        val schedules = await { success, failure -> owner.scheduleSnapshot(success, failure) }
        val plan = com.attentionstats.protection.ScheduleOwner.decode(JSONObject().put("groups", schedules.getJSONArray("groups"))
            .put("windows", schedules.getJSONArray("windows")).toString())
        check(plan.windows.none { window -> window.enabled && plan.groups.any {
            it.id == window.groupId && it.packages.any(app::owns)
        } }) { "existing source schedules preserved" }
        val first = frame()
        check(first.surface == Surface.REELS && first.route == ReelRoute.VIEWER) { "dedicated viewer required" }
        var wasEnabled = false
        var hadNotice = false
        main {
            check(!AutoScrollSession.running) { "existing active run preserved" }
            wasEnabled = AutoScrollSession.enabled; hadNotice = AutoScrollSession.noticeAccepted
        }
        val reasons = mutableMapOf<String, Int>()
        val dispatchStages = java.util.concurrent.ConcurrentHashMap<String, Int>()
        var originalDiagnostic: ((String) -> Unit)? = null
        var observedAttempts = 0
        var observedTransitions = 0
        var minimum: Double? = null
        var maximum: Double? = null
        var setupSwipes = 0
        var restoreCollapsed = false
        try {
            main {
                originalDiagnostic = AutoScrollSession.onDispatchDiagnostic
                AutoScrollSession.onDispatchDiagnostic = { stage -> dispatchStages.merge(stage, 1, Int::plus) }
            }
            reminder(false)
            main { AutoScrollSession.configure(true, 1) }
            var started = first
            for (attempt in 1..if (useCurrent) 0 else 3) {
                val setup = frame()
                check(setup.surface == Surface.REELS && setup.route == ReelRoute.VIEWER) { "setup left dedicated viewer" }
                val box = checkNotNull(setup.page)
                val down = SystemClock.uptimeMillis()
                for (step in 0..12) {
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(),
                        when (step) { 0 -> MotionEvent.ACTION_DOWN; 12 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE },
                        box.left + box.width * .4f, box.top + box.height * (.78f - .53f * step / 12), 0)
                    try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
                    SystemClock.sleep(25)
                }
                setupSwipes++
                val readyUntil = SystemClock.elapsedRealtime() + 5_000
                do { SystemClock.sleep(300); started = frame() }
                while ((started.surface != Surface.REELS || started.route != ReelRoute.VIEWER ||
                    started.progress?.let { it < .9 } != true) && SystemClock.elapsedRealtime() < readyUntil)
                if (started.surface == Surface.REELS && started.route == ReelRoute.VIEWER && started.progress?.let { it < .9 } == true) break
            }
            check(started.surface == Surface.REELS && started.route == ReelRoute.VIEWER && started.progress?.let { it < .9 } == true) {
                "setup did not reach an advancing candidate; swipes=$setupSwipes; progress=${started.progress}; route=${started.route}"
            }
            val hudUntil = SystemClock.elapsedRealtime() + 5_000
            var hud = LiveHudVerification.state(instrumentation, automation)
            while (hud.header == null && SystemClock.elapsedRealtime() < hudUntil) {
                SystemClock.sleep(100); hud = LiveHudVerification.state(instrumentation, automation)
            }
            check(hud.header != null) { "visible HUD required before Start" }
            check(!hud.paused) { "existing counting pause preserved" }
            restoreCollapsed = !hud.expanded
            if (restoreCollapsed) LiveHudVerification.choose(instrumentation, automation, "header")
            LiveHudVerification.choose(instrumentation, automation, "off")
            check(AutoScrollSession.running) { "HUD Start did not enable run" }
            val began = SystemClock.elapsedRealtime()
            var nextStatus = began + 5_000
            var missingProgressSince = 0L
            var current = started
            var nextIndependentScan = 0L
            while (SystemClock.elapsedRealtime() - began < 150_000) {
                val sampleAt = SystemClock.elapsedRealtime()
                if (sampleAt >= nextIndependentScan && AutoScrollSession.attempts == 0) {
                    current = frame()
                    current.progress?.let { minimum = minOf(minimum ?: it, it); maximum = maxOf(maximum ?: it, it) }
                    nextIndependentScan = sampleAt + 1_000
                }
                val reason = AutoScrollSession.reason
                reasons[reason] = (reasons[reason] ?: 0) + 1
                observedAttempts = AutoScrollSession.attempts
                observedTransitions = AutoScrollSession.confirmedTransitions
                if (observedTransitions >= 1) {
                    LiveHudVerification.choose(instrumentation, automation, "on")
                    check(!AutoScrollSession.running) { "HUD Stop did not disable run" }
                    break
                }
                check(AutoScrollSession.running) { "operator stopped the run" }
                check(observedAttempts <= 1) { "more than one automatic attempt" }
                if (reason in setOf("gesture_cancelled", "gesture_unavailable", "next_page_unconfirmed"))
                    error("automatic transition failed: $reason; stages=$dispatchStages")
                if (observedAttempts > 0 && reason != "advancing")
                    error("completion proposal interrupted: $reason; stages=$dispatchStages")
                val now = SystemClock.elapsedRealtime()
                if (current.progress == null && observedAttempts == 0) {
                    if (missingProgressSince == 0L) missingProgressSince = now
                    check(now - missingProgressSince < 5_000) { "playback signal lost; attempts=$observedAttempts; reasons=$reasons" }
                } else missingProgressSince = 0L
                if (now >= nextStatus) {
                    instrumentation.sendStatus(1, android.os.Bundle().apply {
                        putString("stream", "Live $key auto-scroll elapsedSeconds=${(now - began) / 1_000}; attempts=$observedAttempts; confirmed=$observedTransitions; reason=$reason; progress=$minimum..$maximum; stages=$dispatchStages")
                    })
                    nextStatus = now + 5_000
                }
                SystemClock.sleep(200)
            }
            check(observedAttempts == 1 && observedTransitions == 1) {
                "no confirmed auto transition; attempts=$observedAttempts; reasons=$reasons; progress=$minimum..$maximum; stages=$dispatchStages"
            }
            SystemClock.sleep(2_000)
            check(!AutoScrollSession.running && AutoScrollSession.attempts == 1) { "Stop did not fence later actions" }
            var next = frame()
            fun overlap() = org.attentionstats.reels.EncounterTracker.overlap(started.keys, next.keys)
            fun settled() = next.surface == Surface.REELS && next.route == ReelRoute.VIEWER && next.identity != null &&
                next.identity != started.identity && (next.mediaKind == "photo" || next.progress?.let { it < .9 } == true ||
                    (next.progress == null && started.keys.isNotEmpty() && next.keys.isNotEmpty() && overlap() < .25))
            val settleUntil = SystemClock.elapsedRealtime() + 5_000
            while (!settled() && SystemClock.elapsedRealtime() < settleUntil) { SystemClock.sleep(200); next = frame() }
            check(settled()) {
                "next item not settled: ${next.surface}/${next.route}/${next.mediaKind}; progress=${next.progress}; sameIdentity=${next.identity == started.identity}; overlap=${overlap()}; attempts=$observedAttempts; confirmed=$observedTransitions"
            }
            return "PASS: $key HUD Start, numeric video end, one automatic attempt and one confirmed distinct next item; HUD Stop prevents further attempts; setupSwipes=$setupSwipes; nextPlaybackAvailable=${next.progress != null}; progress=$minimum..$maximum; reasons=$reasons; stages=$dispatchStages"
        } finally {
            try {
                main { AutoScrollSession.stop() }
                if (restoreCollapsed) {
                    val hud = LiveHudVerification.state(instrumentation, automation)
                    if (hud.header != null && hud.controls.singleOrNull()?.first == "off")
                        LiveHudVerification.choose(instrumentation, automation, "header")
                }
            } finally {
                main {
                    AutoScrollSession.onDispatchDiagnostic = originalDiagnostic
                    AutoScrollSession.clear()
                    if (hadNotice || wasEnabled) {
                        AutoScrollSession.configure(true, 1)
                        if (!wasEnabled) AutoScrollSession.configure(false, 1)
                    }
                }
                reminder(original.getBoolean("reminderEnabled"))
            }
        }
    }
}
