package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.os.SystemClock
import com.attentionstats.autoscroll.AutoScrollSession
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.reels.ReelApp
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Operator-only source withdrawal through the production owner; retains history and other grants. */
internal object LiveSourceRevocationVerification {
    fun run(instrumentation: Instrumentation): Int {
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val coordinator = ReelsCoordinator.get(instrumentation.targetContext)
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
        fun request(operation: ((String) -> Unit, (String) -> Unit) -> Unit): JSONObject {
            val done = CountDownLatch(1)
            var response: String? = null
            var failed = false
            operation({ response = it; done.countDown() }, { failed = true; done.countDown() })
            check(done.await(15, TimeUnit.SECONDS) && !failed) { "source configuration operation failed" }
            return JSONObject(checkNotNull(response))
        }
        fun snapshot() = request { success, failure -> coordinator.snapshot(success, failure) }
        fun choices(value: JSONObject): Map<String, List<Boolean>> {
            val sources = value.getJSONArray("sources")
            return (0 until sources.length()).associate { index ->
                val row = sources.getJSONObject(index)
                row.getString("app") to listOf(row.getBoolean("enabled"), row.getBoolean("paused"), row.getBoolean("consentCurrent"))
            }
        }
        fun state() = LiveHudVerification.state(instrumentation, automation)
        val initialSnapshot = snapshot()
        check(waitFor { coordinator.serviceConnected }) { "connected service required" }
        val root = checkNotNull(automation.rootInActiveWindow) { "selected viewer required" }
        val source = try { checkNotNull(ReelApp.byPackage(root.packageName?.toString().orEmpty())) }
            finally { SnapshotReader.recycle(root) }
        check(coordinator.allowed(source) && coordinator.reminder == null) { "unpaused allowed source without reminder required" }
        check(waitFor { state().header != null }) { "existing source HUD required" }
        val initial = state()
        check(!initial.paused) { "existing pause preserved" }
        val originalChoices = choices(initialSnapshot)
        var wasEnabled = false
        var hadNotice = false
        main {
            check(!AutoScrollSession.running) { "existing active run preserved" }
            wasEnabled = AutoScrollSession.enabled; hadNotice = AutoScrollSession.noticeAccepted
        }
        fun configure(enabled: Boolean) {
            val current = snapshot()
            request { success, failure -> coordinator.configure(source.key, enabled, current.getString("revision"), success, failure) }
        }
        var withdrawn = false
        var checks = 0
        fun expect(value: Boolean) { check(value) { "live source revocation ${checks + 1}" }; checks++ }
        try {
            repeat(2) {
                main { AutoScrollSession.configure(true, 2) }
                check(waitFor { AutoScrollSession.canStart() }) { "fresh playable item required" }
                if (!state().expanded) LiveHudVerification.choose(instrumentation, automation, "header")
                LiveHudVerification.choose(instrumentation, automation, "off")
                var token = 0L
                main {
                    expect(AutoScrollSession.running)
                    token = AutoScrollSession.revision
                    expect(AutoScrollSession.current(token))
                }
                val epoch = coordinator.epoch()
                // Mark before requesting: cleanup also covers a successful write with a lost callback.
                withdrawn = true
                configure(false)
                expect(!coordinator.allowed(source))
                expect(coordinator.epoch() != epoch)
                expect(waitFor { state().header == null })
                expect(waitFor { !AutoScrollSession.current(token) })
                SystemClock.sleep(2_000)
                main { expect(AutoScrollSession.attempts == 0 && AutoScrollSession.confirmedTransitions == 0) }
                val disabled = choices(snapshot())
                expect(disabled.filterKeys { it != source.key } == originalChoices.filterKeys { it != source.key })
                expect(disabled.getValue(source.key)[0] == false)
                expect(coordinator.serviceConnected && coordinator.grantEnabled())
                // End the run before regranting; this trial does not qualify automatic resumption.
                main { AutoScrollSession.stop("operator_trial_complete") }
                configure(true); withdrawn = false
                expect(waitFor { coordinator.allowed(source) && state().header != null })
                expect(choices(snapshot()) == originalChoices)
                main { expect(!AutoScrollSession.running && !AutoScrollSession.current(token)) }
            }
        } finally {
            main { AutoScrollSession.stop("operator_trial_complete") }
            try {
                if (withdrawn) configure(true)
            } finally {
                main {
                    AutoScrollSession.clear()
                    if (hadNotice || wasEnabled) {
                        AutoScrollSession.configure(true, 2)
                        if (!wasEnabled) AutoScrollSession.configure(false, 2)
                    }
                }
            }
            check(choices(snapshot()) == originalChoices) { "source choice restoration failed" }
            if (waitFor { state().header != null } && state().expanded != initial.expanded)
                LiveHudVerification.choose(instrumentation, automation, "header")
        }
        return checks
    }
}
