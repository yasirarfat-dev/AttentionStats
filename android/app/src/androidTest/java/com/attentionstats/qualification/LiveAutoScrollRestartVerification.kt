package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.os.SystemClock
import com.attentionstats.autoscroll.AutoScrollSession
import com.attentionstats.reels.ReelsCoordinator

/** Paired operator trial. The external operator stops only this app on the prepare readiness status. */
internal object LiveAutoScrollRestartVerification {
    fun run(instrumentation: Instrumentation, phase: String): Int {
        var failure: Throwable? = null
        var checks = 0
        fun expect(value: Boolean) { check(value) { "live process restart ${checks + 1}" }; checks++ }
        fun main(action: () -> Unit) {
            instrumentation.runOnMainSync { try { action() } catch (caught: Throwable) { failure = caught } }
            failure?.let { throw it }
        }
        if (phase == "verify") {
            // Inspect before configuring anything: a fresh process must not inherit session consent.
            main {
                expect(!AutoScrollSession.running)
                expect(!AutoScrollSession.enabled)
                expect(!AutoScrollSession.noticeAccepted)
                expect(!AutoScrollSession.canStart())
                expect(AutoScrollSession.attempts == 0 && AutoScrollSession.confirmedTransitions == 0)
            }
            SystemClock.sleep(2_000)
            main { expect(!AutoScrollSession.running && !AutoScrollSession.enabled && !AutoScrollSession.noticeAccepted) }
            return checks
        }
        require(phase == "prepare")
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val coordinator = ReelsCoordinator.get(instrumentation.targetContext)
        fun waitFor(predicate: () -> Boolean): Boolean {
            val until = SystemClock.elapsedRealtime() + 10_000
            do { if (predicate()) return true; SystemClock.sleep(100) }
            while (SystemClock.elapsedRealtime() < until)
            return false
        }
        check(waitFor { coordinator.serviceConnected }) { "connected service required" }
        check(coordinator.reminder == null) { "existing reminder preserved" }
        fun state() = LiveHudVerification.state(instrumentation, automation)
        check(waitFor { state().header != null }) { "existing source HUD required" }
        check(!state().paused) { "existing counting pause preserved" }
        main { check(!AutoScrollSession.running) { "existing run preserved" } }
        var prepared = false
        try {
            main { AutoScrollSession.configure(true, 2) }
            check(waitFor { AutoScrollSession.canStart() }) { "fresh playable item required" }
            if (!state().expanded) LiveHudVerification.choose(instrumentation, automation, "header")
            LiveHudVerification.choose(instrumentation, automation, "off")
            main {
                expect(AutoScrollSession.running && AutoScrollSession.enabled && AutoScrollSession.noticeAccepted)
                expect(AutoScrollSession.current(AutoScrollSession.revision))
                expect(AutoScrollSession.attempts == 0 && AutoScrollSession.confirmedTransitions == 0)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ AutoScrollSession.clear() }, 5_000)
            }
            prepared = true
            instrumentation.sendStatus(0, android.os.Bundle().apply {
                putString("restartReady", "actual_hud_active")
                putInt("restartPrepareChecks", checks)
            })
            // The operator terminates this owned instrumentation/target during this bounded window.
            SystemClock.sleep(4_000)
        } finally {
            if (!prepared) main { AutoScrollSession.clear() }
        }
        // Remains active for immediate external force-stop, with a five-second test-only safety stop.
        return checks
    }
}
