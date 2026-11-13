package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import com.attentionstats.autoscroll.AutoScrollSession
import com.attentionstats.reels.ReelsCoordinator

/** Operator-only revocation of this app's service. Never reads source content or changes source grants. */
internal object LiveServiceRevocationVerification {
    fun run(instrumentation: Instrumentation): Int {
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val resolver = instrumentation.targetContext.contentResolver
        val packageName = instrumentation.targetContext.packageName
        val coordinator = ReelsCoordinator.get(instrumentation.targetContext)
        fun selected(): String = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        fun components(value: String) = value.split(':').filter { it.isNotEmpty() && it != "null" }
        fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
        fun setSelected(value: List<String>) {
            check(value.all { it.matches(Regex("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+")) })
            val setting = value.joinToString(":").ifEmpty { "null" }
            shell("settings put secure enabled_accessibility_services $setting")
        }
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
        fun state() = LiveHudVerification.state(instrumentation, automation)
        // Fresh instrumentation restarts the target process. Let the operator rebind only our service
        // before taking the baseline; never snapshot the temporary removed-entry state as original.
        SystemClock.sleep(4_000)
        check(waitFor { coordinator.serviceConnected && components(selected()).count { it.startsWith("$packageName/") } == 1 }) {
            "selected connected app service required after instrumentation startup"
        }
        val original = components(selected())
        val own = original.filter { it.startsWith("$packageName/") }
        val others = original - own.toSet()
        check(own.size == 1) { "one selected app service required" }
        val master = Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        check(master == 1 && waitFor { coordinator.serviceConnected }) { "enabled connected service required" }
        check(coordinator.reminder == null) { "existing reminder preserved" }
        check(waitFor { state().header != null }) { "existing source HUD required" }
        val initial = state()
        check(!initial.paused) { "existing counting pause preserved" }
        var wasEnabled = false
        var hadNotice = false
        main {
            check(!AutoScrollSession.running) { "existing active run preserved" }
            wasEnabled = AutoScrollSession.enabled; hadNotice = AutoScrollSession.noticeAccepted
        }
        var checks = 0
        fun expect(value: Boolean) { check(value) { "live service revocation ${checks + 1}" }; checks++ }
        try {
            repeat(2) {
                check(components(selected()) == original) { "concurrent accessibility change preserved" }
                main { AutoScrollSession.configure(true, 2) }
                check(waitFor { state().header != null })
                check(waitFor { AutoScrollSession.canStart() }) { "fresh playable item required" }
                if (!state().expanded) LiveHudVerification.choose(instrumentation, automation, "header")
                LiveHudVerification.choose(instrumentation, automation, "off")
                var token = 0L
                main {
                    expect(AutoScrollSession.running)
                    token = AutoScrollSession.revision
                    expect(AutoScrollSession.current(token))
                }
                // Revoke immediately; no playback loop or source gesture is part of this trial.
                setSelected(others)
                expect(waitFor { !coordinator.serviceConnected })
                expect(waitFor { state().header == null })
                main {
                    expect(!AutoScrollSession.running && !AutoScrollSession.enabled && !AutoScrollSession.noticeAccepted)
                    expect(!AutoScrollSession.current(token))
                    expect(AutoScrollSession.attempts == 0 && AutoScrollSession.confirmedTransitions == 0)
                }
                expect(components(selected()) == others)
                setSelected(original)
                shell("settings put secure accessibility_enabled $master")
                expect(waitFor { coordinator.serviceConnected })
                expect(waitFor { state().header != null })
                main {
                    expect(!AutoScrollSession.running && !AutoScrollSession.enabled && !AutoScrollSession.noticeAccepted)
                    expect(!AutoScrollSession.current(token))
                }
                expect(components(selected()) == original)
            }
        } finally {
            // Preserve any concurrent changes to unrelated services, restoring only our original entry.
            val currentOthers = components(selected()).filterNot { it.startsWith("$packageName/") }
            setSelected(currentOthers + own)
            shell("settings put secure accessibility_enabled $master")
            check(waitFor { coordinator.serviceConnected }) { "service restoration failed" }
            main {
                AutoScrollSession.clear()
                if (hadNotice || wasEnabled) {
                    AutoScrollSession.configure(true, 2)
                    if (!wasEnabled) AutoScrollSession.configure(false, 2)
                }
            }
            if (waitFor { state().header != null } && state().expanded != initial.expanded)
                LiveHudVerification.choose(instrumentation, automation, "header")
        }
        return checks
    }
}
