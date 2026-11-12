package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.os.SystemClock
import com.attentionstats.autoscroll.AutoScrollSession
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.reels.ReelApp

/** Own HUD and package ownership only. No notifications, source content or screenshots. */
internal object LiveAutoScrollInterruptionVerification {
    fun run(instrumentation: Instrumentation): Int {
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val coordinator = ReelsCoordinator.get(instrumentation.targetContext)
        val loaded = java.util.concurrent.CountDownLatch(1)
        var loadFailure = false
        coordinator.snapshot({ loaded.countDown() }, { loadFailure = true; loaded.countDown() })
        check(loaded.await(15, java.util.concurrent.TimeUnit.SECONDS) && !loadFailure) { "source configuration unavailable" }
        fun main(action: () -> Unit) {
            var failure: Throwable? = null
            instrumentation.runOnMainSync { try { action() } catch (caught: Throwable) { failure = caught } }
            failure?.let { throw it }
        }
        fun owner(): String? {
            val root = automation.rootInActiveWindow ?: return null
            return try { root.packageName?.toString() } finally { SnapshotReader.recycle(root) }
        }
        fun waitFor(predicate: () -> Boolean): Boolean {
            val until = SystemClock.elapsedRealtime() + 5_000
            do { if (predicate()) return true; SystemClock.sleep(100) }
            while (SystemClock.elapsedRealtime() < until)
            return false
        }
        fun state() = LiveHudVerification.state(instrumentation, automation)
        fun shade(expand: Boolean) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
                "cmd statusbar ${if (expand) "expand-notifications" else "collapse"}")).use { it.readBytes() }
        }
        check(waitFor { coordinator.serviceConnected }) { "service unavailable" }
        check(coordinator.reminder == null) { "existing reminder preserved" }
        val source = checkNotNull(ReelApp.byPackage(owner().orEmpty())) { "selected source required" }
        check(coordinator.allowed(source)) { "source choice unavailable" }
        check(waitFor { state().header != null }) { "HUD unavailable" }
        val initial = state()
        check(!initial.paused) { "existing counting pause preserved" }
        var wasEnabled = false
        var hadNotice = false
        main {
            check(!AutoScrollSession.running) { "existing active run preserved" }
            wasEnabled = AutoScrollSession.enabled; hadNotice = AutoScrollSession.noticeAccepted
        }
        var checks = 0
        fun expect(value: Boolean) { check(value) { "live active interruption ${checks + 1}" }; checks++ }
        var shadeOpen = false
        try {
            repeat(3) {
                main { AutoScrollSession.configure(true, 1) }
                check(waitFor { state().header != null })
                check(waitFor { AutoScrollSession.canStart() }) { "fresh playable item required before interruption trial" }
                if (!state().expanded) LiveHudVerification.choose(instrumentation, automation, "header")
                LiveHudVerification.choose(instrumentation, automation, "off")
                expect(AutoScrollSession.running)
                var token = 0L
                main { token = AutoScrollSession.revision; expect(AutoScrollSession.current(token)) }
                shadeOpen = true; shade(true)
                expect(waitFor { owner() == "com.android.systemui" })
                expect(waitFor { state().header == null })
                expect(waitFor { !AutoScrollSession.current(token) })
                SystemClock.sleep(2_000)
                expect(AutoScrollSession.running) // User's On choice survives the interruption.
                expect(AutoScrollSession.attempts == 0 && AutoScrollSession.confirmedTransitions == 0)
                // End this trial before source reacquisition can start another playback cycle.
                main { AutoScrollSession.stop("operator_trial_complete") }
                shade(false); shadeOpen = false
                expect(waitFor { source.owns(owner()) })
                expect(waitFor { state().header != null })
                expect(!AutoScrollSession.running && coordinator.serviceConnected && coordinator.allowed(source))
            }
        } finally {
            main {
                AutoScrollSession.clear()
                if (hadNotice || wasEnabled) {
                    AutoScrollSession.configure(true, 1)
                    if (!wasEnabled) AutoScrollSession.configure(false, 1)
                }
            }
            if (shadeOpen) shade(false)
            val current = state()
            if (current.header != null && !current.paused && current.expanded != initial.expanded)
                LiveHudVerification.choose(instrumentation, automation, "header")
        }
        return checks
    }
}
