package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.attentionstats.protection.ProtectionCredentials
import com.attentionstats.protection.ProtectionLock
import com.attentionstats.protection.ProtectionLockActivity
import com.attentionstats.reels.ReelsCoordinator
import com.attentionstats.storage.HistoryStore
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.attentionstats.reels.ReelApp
import org.json.JSONObject

/** Actual coordinator entry points, private credentials and private history; no live singleton replacement. */
internal object ProtectionRoutesVerification {
    fun run(instrumentation: Instrumentation): String = runBlocking {
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val target = instrumentation.targetContext
        check(target.getSharedPreferences("detection", Context.MODE_PRIVATE).contains("item_secret"))
        val original = ProtectionCredentials(target).state()
        val root = File(target.noBackupFilesDir, "protection-routes-${UUID.randomUUID()}")
        check(root.mkdir())
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = root }
        val alias = "attentionstats.protection.routes.${UUID.randomUUID()}"
        val historyAlias = "$alias.history"
        val credentials = ProtectionCredentials(context, alias)
        var history: HistoryStore? = null
        var coordinator: ReelsCoordinator? = null
        var activity: ProtectionLockActivity? = null
        var checks = 0
        fun expect(value: Boolean, message: String) { check(value) { message }; checks++ }
        fun field(name: String) = ReelsCoordinator::class.java.getDeclaredField(name).apply { isAccessible = true }
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
        fun click(label: String) = main {
            check(views().filterIsInstance<TextView>().single { it.isClickable && it.text.toString().equals(label, true) }.performClick())
        }
        fun snapshot(owner: ReelsCoordinator): JSONObject {
            val done = CountDownLatch(1)
            var result: String? = null
            var error: String? = null
            owner.snapshot({ result = it; done.countDown() }, { error = it; done.countDown() })
            check(done.await(20, TimeUnit.SECONDS) && error == null) { "Isolated snapshot failed" }
            return JSONObject(checkNotNull(result))
        }
        try {
            val store = HistoryStore.open(context, historyAlias).also { history = it }
            val owner = ReelsCoordinator::class.java.getDeclaredConstructor(Context::class.java)
                .apply { isAccessible = true }.newInstance(context).also { coordinator = it }
            field("store").set(owner, store)
            field("health").set(owner, "busy")
            field("protectionCredentials").set(owner, credentials)
            val before = snapshot(owner)
            val phrases = listOf("synthetic orchard guardian", "synthetic river guardian", "synthetic lantern guardian")
            credentials.setup("583194".toCharArray(), "583194".toCharArray(), phrases.map(String::toCharArray), phrases.map(String::toCharArray))
            val dao = store.database.data()
            val metadata = dao.metadata()
            val configurations = dao.configurations()
            val diary = dao.diary()
            val protection = dao.protection()
            val epoch = owner.epoch()
            val successes = AtomicInteger()
            val failures = AtomicInteger()
            val revision = before.getString("revision")
            val ok: (String) -> Unit = { successes.incrementAndGet() }
            val bad: (String) -> Unit = { check(it == "AUTH_CANCELLED"); failures.incrementAndGet() }
            fun challenge(label: String, authorize: Boolean = false, operation: () -> Unit) {
                val monitor = instrumentation.addMonitor(ProtectionLockActivity::class.java.name, null, false)
                try {
                    operation()
                    activity = instrumentation.waitForMonitorWithTimeout(monitor, 8_000) as? ProtectionLockActivity
                    expect(activity != null, "$label bypassed the native authorization challenge")
                    val until = SystemClock.elapsedRealtime() + 10_000
                    var ready = false
                    while (!ready && SystemClock.elapsedRealtime() < until) {
                        main { ready = views().filterIsInstance<EditText>().size == 1 }
                        if (!ready) SystemClock.sleep(100)
                    }
                    expect(ready, "$label challenge not ready")
                    val token = checkNotNull(activity).intent.getStringExtra("request")!!
                    if (authorize) {
                        main { views().filterIsInstance<EditText>().single().setText("583194") }
                        click("Approve")
                    } else click("Cancel")
                    val deadline = SystemClock.elapsedRealtime() + 15_000
                    var retired = false
                    while (!retired && SystemClock.elapsedRealtime() < deadline) {
                        main { retired = ProtectionLock.label(token) == null }
                        if (!retired) SystemClock.sleep(100)
                    }
                    expect(retired, "$label authorization did not retire")
                    if (authorize) while (successes.get() == 0 && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
                    snapshot(owner)
                } finally { instrumentation.removeMonitor(monitor) }
            }
            val routes = listOf<Pair<String, () -> Unit>>(
                "individual source" to { owner.configure(ReelApp.INSTAGRAM.key, true, revision, ok, bad) },
                "all sources" to { owner.configureAll(true, revision, ok, bad) },
                "pause" to { owner.setPaused(true, revision, ok, bad) },
                "diary limits" to { owner.configureDiary("reminder", true, 30, 15, checkNotNull(diary).revision, ok, bad) },
                "groups and slots" to { owner.saveSchedules("{}", 0, { successes.incrementAndGet() }, bad) },
                "early focus end" to { owner.changeFocus(null, 0, 0, "synthetic", { successes.incrementAndGet() }, bad) },
                "history erase" to { owner.deleteHistory(revision, ok, bad) },
                "restore replacement" to { owner.restoreArchive("synthetic", java.util.concurrent.atomic.AtomicBoolean(false), { successes.incrementAndGet() }, bad) },
                "short-video allowance" to { owner.allowFor(ReelApp.INSTAGRAM, 5) { successes.incrementAndGet() } },
                "whole-app allowance" to { owner.allowScheduled("org.example.synthetic") { successes.incrementAndGet() } }
            )
            routes.forEach { (label, operation) ->
                challenge(label, operation = operation)
                expect(successes.get() == 0, "$label ran after cancellation")
                expect(dao.metadata() == metadata && dao.configurations() == configurations && dao.diary() == diary && dao.protection() == protection,
                    "$label changed isolated persisted state without authorization")
                expect(owner.epoch() == epoch, "$label invalidated observations before authorization")
            }
            expect(failures.get() == 8, "Missing explicit cancellation callbacks")
            challenge("authorized source", authorize = true) { owner.configure(ReelApp.INSTAGRAM.key, true, revision, ok, bad) }
            expect(successes.get() == 1 && failures.get() == 8, "Authorized source did not complete once")
            expect(dao.configurations().single { it.sourceId == ReelApp.INSTAGRAM.sourceId }.enabled, "Authorized source was not persisted")
            expect(owner.epoch() == epoch + 1 && snapshot(owner).getString("revision") != revision, "Authorized source revision/fence did not advance")
            expect(ProtectionCredentials(target).state() == original, "Live credential state changed")
            "PASS: $checks isolated actual coordinator protection-route checks; ten cancellations preserve state and fences, PIN authorizes individual source once; live history and credentials unchanged"
        } finally {
            main { activity?.finish() }
            coordinator?.let { owner ->
                val scope = field("scope").get(owner) as CoroutineScope
                scope.coroutineContext[Job]?.cancelAndJoin()
            }
            history?.close()
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            listOf(alias, historyAlias).forEach { if (keys.containsAlias(it)) keys.deleteEntry(it); check(!keys.containsAlias(it)) }
            check(root.parentFile?.canonicalFile == target.noBackupFilesDir.canonicalFile)
            check(root.deleteRecursively() && !root.exists())
        }
    }
}
