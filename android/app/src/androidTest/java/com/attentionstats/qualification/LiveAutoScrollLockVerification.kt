package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.KeyguardManager
import android.app.UiAutomation
import android.os.PowerManager
import android.os.SystemClock
import com.attentionstats.autoscroll.AutoScrollSession
import com.attentionstats.reels.ReelsCoordinator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real screen-off/keyguard fence. Never enters credentials or changes lock/display settings. */
internal object LiveAutoScrollLockVerification {
    fun run(instrumentation: Instrumentation): Int {
        val context = instrumentation.targetContext
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        check(power.isInteractive && !keyguard.isKeyguardLocked) { "unlocked phone required" }
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val coordinator = ReelsCoordinator.get(context)
        val loaded = CountDownLatch(1)
        var failed = false
        coordinator.snapshot({ loaded.countDown() }, { failed = true; loaded.countDown() })
        check(loaded.await(15, TimeUnit.SECONDS) && !failed) { "configuration unavailable" }
        fun main(action: () -> Unit) {
            var failure: Throwable? = null
            instrumentation.runOnMainSync { try { action() } catch (caught: Throwable) { failure = caught } }
            failure?.let { throw it }
        }
        fun waitFor(predicate: () -> Boolean): Boolean {
            val until = SystemClock.elapsedRealtime() + 10_000
            do { if (predicate()) return true; SystemClock.sleep(100) }
            while (SystemClock.elapsedRealtime() < until)
            return false
        }
        fun shell(command: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
        fun state() = LiveHudVerification.state(instrumentation, automation)
        check(waitFor { coordinator.serviceConnected && state().header != null }) { "existing source HUD required" }
        check(coordinator.reminder == null && !state().paused) { "existing reminder/counting pause preserved" }
        val expanded = state().expanded
        var wasEnabled = false
        var hadNotice = false
        main {
            check(!AutoScrollSession.running) { "existing run preserved" }
            wasEnabled = AutoScrollSession.enabled; hadNotice = AutoScrollSession.noticeAccepted
        }
        var checks = 0
        fun expect(value: Boolean) { check(value) { "live lock ${checks + 1}" }; checks++ }
        var slept = false
        try {
            main { AutoScrollSession.configure(true, 2) }
            check(waitFor { AutoScrollSession.canStart() }) { "fresh playable item required" }
            if (!state().expanded) LiveHudVerification.choose(instrumentation, automation, "header")
            LiveHudVerification.choose(instrumentation, automation, "off")
            var token = 0L
            main {
                token = AutoScrollSession.revision
                expect(AutoScrollSession.running && AutoScrollSession.current(token))
                expect(AutoScrollSession.attempts == 0)
            }
            slept = true; shell("input keyevent 223") // KEYCODE_SLEEP, never a toggle.
            expect(waitFor { !power.isInteractive })
            expect(waitFor { state().header == null })
            expect(waitFor { !AutoScrollSession.current(token) })
            SystemClock.sleep(2_000)
            main {
                expect(AutoScrollSession.attempts == 0 && AutoScrollSession.confirmedTransitions == 0)
                // End before wake/source reacquisition, which is a separate qualification case.
                AutoScrollSession.stop("operator_lock_trial_complete")
                expect(!AutoScrollSession.running)
            }
            shell("input keyevent 224"); slept = false // KEYCODE_WAKEUP; no credential entry.
            expect(waitFor { power.isInteractive })
            if (keyguard.isKeyguardLocked) {
                SystemClock.sleep(2_000)
                expect(state().header == null)
                expect(!AutoScrollSession.current(token))
            }
            main { expect(!AutoScrollSession.running && AutoScrollSession.attempts == 0) }
            expect(coordinator.serviceConnected)
            instrumentation.sendStatus(0, android.os.Bundle().apply {
                putString("lockWakeState", if (keyguard.isKeyguardLocked) "keyguard_locked" else "unlocked")
            })
        } finally {
            main {
                AutoScrollSession.clear()
                if (hadNotice || wasEnabled) {
                    AutoScrollSession.configure(true, 2)
                    if (!wasEnabled) AutoScrollSession.configure(false, 2)
                }
            }
            if (slept) shell("input keyevent 224")
            if (!keyguard.isKeyguardLocked) {
                val current = state()
                if (current.header != null && !current.paused && current.expanded != expanded)
                    LiveHudVerification.choose(instrumentation, automation, "header")
            }
        }
        return checks
    }
}
