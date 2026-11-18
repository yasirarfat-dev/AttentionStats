package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.attentionstats.protection.ProtectionCredentials
import com.attentionstats.protection.ProtectionBiometric
import com.attentionstats.protection.ProtectionLock
import com.attentionstats.protection.ProtectionLockActivity
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking

/** Real native challenge and continuation, with an isolated credential owner and harmless action. */
internal object ProtectionAuthorizationVerification {
    fun run(instrumentation: Instrumentation, biometric: Boolean = false): String {
        val target = instrumentation.targetContext
        fun main(action: () -> Unit) {
            var failure: Throwable? = null
            instrumentation.runOnMainSync { try { action() } catch (problem: Throwable) { failure = problem } }
            failure?.let { throw it }
        }
        val original = ProtectionCredentials(target).state()
        val root = File(target.noBackupFilesDir, "protection-authorization-${UUID.randomUUID()}")
        check(root.mkdir())
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = root }
        val alias = "attentionstats.protection.test.${UUID.randomUUID()}"
        val owner = ProtectionCredentials(context, alias)
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        var checks = 0
        fun expect(value: Boolean) { check(value) { "protection authorization check ${checks + 1}" }; checks++ }
        val actions = AtomicInteger()
        var activity: ProtectionLockActivity? = null
        fun views(): List<View> {
            val result = mutableListOf<View>()
            fun visit(view: View) { result.add(view); if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) } }
            visit(checkNotNull(activity).window.decorView); return result
        }
        fun click(label: String) = main {
            expect(views().filterIsInstance<TextView>().single { it.isClickable && it.text.toString().equals(label, true) }.performClick())
        }
        fun waitFor(predicate: () -> Boolean) {
            var ready = false; val until = SystemClock.elapsedRealtime() + 15_000
            while (!ready && SystemClock.elapsedRealtime() < until) {
                main { ready = predicate() }
                if (!ready) SystemClock.sleep(100)
            }
            expect(ready)
        }
        fun request(done: CountDownLatch, failed: AtomicInteger) {
            val monitor = instrumentation.addMonitor(ProtectionLockActivity::class.java.name, null, false)
            try {
                ProtectionLock.request(target, "Synthetic authorization trial", { failed.incrementAndGet(); done.countDown() }, owner) { revision, expires ->
                    runBlocking { ProtectionLock.execute(owner, revision, expires) { actions.incrementAndGet() } }
                    done.countDown()
                }
                activity = instrumentation.waitForMonitorWithTimeout(monitor, 15_000) as? ProtectionLockActivity
                expect(activity != null)
                waitFor { views().filterIsInstance<EditText>().size == 1 }
            } finally { instrumentation.removeMonitor(monitor) }
        }
        try {
            val offDone = CountDownLatch(1)
            val offActions = AtomicInteger()
            ProtectionLock.request(target, "Synthetic off-state trial", { offDone.countDown() }, owner) { revision, expires ->
                runBlocking { ProtectionLock.execute(owner, revision, expires) { offActions.incrementAndGet() } }
                offDone.countDown()
            }
            expect(offDone.await(10, TimeUnit.SECONDS) && offActions.get() == 1 && !owner.state().enabled)
            val phrases = listOf("synthetic guardian orchard", "synthetic guardian river", "synthetic guardian lantern")
            owner.setup("583194".toCharArray(), "583194".toCharArray(), phrases.map(String::toCharArray), phrases.map(String::toCharArray))
            if (biometric) {
                owner.change("biometric-on", "583194".toCharArray(), CharArray(0), CharArray(0), CharArray(0))
                ProtectionBiometric(target, owner.biometricAlias).provision()
            }
            val cancelled = CountDownLatch(1); val cancellations = AtomicInteger()
            request(cancelled, cancellations)
            click("Cancel")
            expect(cancelled.await(5, TimeUnit.SECONDS) && cancellations.get() == 1 && actions.get() == 0)
            val authorized = CountDownLatch(1); val failures = AtomicInteger()
            request(authorized, failures)
            val token = checkNotNull(activity).intent.getStringExtra("request")!!
            if (biometric) {
                click("Use parent biometrics")
                instrumentation.sendStatus(1, android.os.Bundle().apply {
                    putString("stream", "READY: fingerprint for the full one-use authorization path; isolated harmless action only")
                })
                expect(authorized.await(90, TimeUnit.SECONDS) && actions.get() == 1 && failures.get() == 0)
                main { ProtectionLock.finish(token, owner.state().revision) }
                expect(actions.get() == 1 && ProtectionCredentials(target).state() == original)
                return "PASS: $checks isolated actual native biometric authorization checks; real touch authorizes one harmless action, replay rejected; live protection unchanged"
            }
            main { views().filterIsInstance<EditText>().single().setText("111111") }
            click("Authorize this action")
            waitFor { views().filterIsInstance<TextView>().any { it.text.toString().contains("did not match") } }
            expect(actions.get() == 0 && failures.get() == 0 && authorized.count == 1L)
            SystemClock.sleep(1100)
            main { views().filterIsInstance<EditText>().single().setText("583194") }
            click("Authorize this action")
            expect(authorized.await(15, TimeUnit.SECONDS) && actions.get() == 1 && failures.get() == 0)
            main {
                ProtectionLock.finish(token, owner.state().revision)
                expect(ProtectionLock.label(token) == null)
            }
            expect(actions.get() == 1) // Replayed completion cannot run the action twice.
            expect(runCatching { runBlocking {
                ProtectionLock.execute(owner, owner.state().revision, SystemClock.elapsedRealtime() - 1) { actions.incrementAndGet() }
            } }.exceptionOrNull() is ProtectionCredentials.Denied)
            expect(actions.get() == 1)
            val stale = owner.state().revision
            owner.change("pin", "583194".toCharArray(), CharArray(0), "714296".toCharArray(), "714296".toCharArray())
            expect(runCatching { runBlocking { ProtectionLock.execute(owner, stale, SystemClock.elapsedRealtime() + 120_000) { actions.incrementAndGet() } } }
                .exceptionOrNull() is ProtectionCredentials.Denied)
            expect(actions.get() == 1 && ProtectionCredentials(target).state() == original)
            return "PASS: $checks isolated actual native authorization checks; cancel, wrong PIN, correct PIN, single use, execution expiry and stale revision; live protection unchanged"
        } finally {
            main { activity?.finish() }
            if (keys.containsAlias(alias)) keys.deleteEntry(alias)
            if (keys.containsAlias(owner.biometricAlias)) keys.deleteEntry(owner.biometricAlias)
            check(!keys.containsAlias(alias))
            check(!keys.containsAlias(owner.biometricAlias))
            check(root.parentFile?.canonicalFile == target.noBackupFilesDir.canonicalFile)
            check(root.deleteRecursively() && !root.exists())
        }
    }
}
