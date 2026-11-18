package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.attentionstats.protection.ProtectionCredentials
import com.attentionstats.protection.ProtectionLock
import com.attentionstats.protection.ProtectionLockActivity
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking

/** Framework departures with private credentials and a harmless action; no live rules or history. */
internal object ProtectionLifecycleVerification {
    fun run(instrumentation: Instrumentation): String {
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val target = instrumentation.targetContext
        val original = ProtectionCredentials(target).state()
        val root = File(target.noBackupFilesDir, "protection-lifecycle-${UUID.randomUUID()}")
        check(root.mkdir())
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = root }
        val alias = "attentionstats.protection.lifecycle.${UUID.randomUUID()}"
        val owner = ProtectionCredentials(context, alias)
        var activity: ProtectionLockActivity? = null
        var gate: CountDownLatch? = null
        var checks = 0
        val actions = AtomicInteger()
        fun expect(value: Boolean, message: String) { check(value) { message }; checks++ }
        fun main(action: () -> Unit) {
            var failure: Throwable? = null
            instrumentation.runOnMainSync { try { action() } catch (problem: Throwable) { failure = problem } }
            failure?.let { throw it }
        }
        fun views(): List<View> {
            val result = mutableListOf<View>()
            fun visit(view: View) { result.add(view); if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) } }
            visit(checkNotNull(activity).window.decorView); return result
        }
        fun waitFor(label: String, predicate: () -> Boolean) {
            val until = SystemClock.elapsedRealtime() + 15_000
            var ready = false
            while (!ready && SystemClock.elapsedRealtime() < until) {
                main { ready = predicate() }
                if (!ready) SystemClock.sleep(100)
            }
            expect(ready, label)
        }
        try {
            val phrases = listOf("synthetic guardian orchard", "synthetic guardian river", "synthetic guardian lantern")
            owner.setup("583194".toCharArray(), "583194".toCharArray(), phrases.map(String::toCharArray), phrases.map(String::toCharArray))
            val revision = owner.state().revision
            for (departure in listOf("Back", "Home", "recreation", "queued verification")) {
                val done = CountDownLatch(1)
                val cancelled = AtomicInteger()
                val monitor = instrumentation.addMonitor(ProtectionLockActivity::class.java.name, null, false)
                try {
                    ProtectionLock.request(target, "Synthetic lifecycle $departure", {
                        check(it == "AUTH_CANCELLED"); cancelled.incrementAndGet(); done.countDown()
                    }, owner) { approved, expires ->
                        runBlocking { ProtectionLock.execute(owner, approved, expires) { actions.incrementAndGet() } }
                        done.countDown()
                    }
                    activity = instrumentation.waitForMonitorWithTimeout(monitor, 15_000) as? ProtectionLockActivity
                    expect(activity != null, "$departure missing challenge")
                    waitFor("$departure missing PIN field") { views().filterIsInstance<EditText>().size == 1 }
                    val token = checkNotNull(activity).intent.getStringExtra("request")!!
                    val input = views().filterIsInstance<EditText>().single()
                    main { input.setText("583194") }
                    if (departure == "queued verification") {
                        val blocked = CountDownLatch(1)
                        val release = CountDownLatch(1).also { gate = it }
                        ProtectionLock.worker.execute { blocked.countDown(); check(release.await(25, TimeUnit.SECONDS)) }
                        expect(blocked.await(5, TimeUnit.SECONDS), "Worker did not enter isolated gate")
                        main { check(views().filterIsInstance<TextView>().single { it.isClickable && it.text.toString().equals("Approve", true) }.performClick()) }
                    }
                    when (departure) {
                        "Back" -> instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                        "recreation" -> main { checkNotNull(activity).recreate() }
                        else -> instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_HOME)
                    }
                    expect(done.await(15, TimeUnit.SECONDS) && cancelled.get() == 1, "$departure did not cancel exactly once")
                    waitFor("$departure left secret or old activity alive") { input.text.isEmpty() && checkNotNull(activity).isDestroyed }
                    gate?.countDown(); gate = null
                    val drained = CountDownLatch(1)
                    ProtectionLock.worker.execute { drained.countDown() }
                    expect(drained.await(10, TimeUnit.SECONDS), "$departure worker did not drain")
                    main {
                        expect(ProtectionLock.label(token) == null && ProtectionLock.credentials(token) == null, "$departure retained authorization")
                        ProtectionLock.finish(token, revision)
                    }
                    expect(actions.get() == 0 && cancelled.get() == 1, "$departure or replay ran the action")
                    expect(owner.state().revision == revision && owner.verify("583194".toCharArray()) == revision, "$departure changed credentials")
                    expect(ProtectionCredentials(target).state() == original, "$departure changed live protection")
                } finally { instrumentation.removeMonitor(monitor) }
            }
            return "PASS: $checks isolated native protection lifecycle checks; Back, Home, recreation and queued PIN departure cancel once, clear secrets and reject replay; live protection unchanged"
        } finally {
            gate?.countDown()
            main { activity?.finish() }
            val drained = CountDownLatch(1)
            ProtectionLock.worker.execute { drained.countDown() }
            check(drained.await(15, TimeUnit.SECONDS))
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keys.containsAlias(alias)) keys.deleteEntry(alias)
            check(!keys.containsAlias(alias))
            check(root.parentFile?.canonicalFile == target.noBackupFilesDir.canonicalFile)
            check(root.deleteRecursively() && !root.exists())
        }
    }
}
