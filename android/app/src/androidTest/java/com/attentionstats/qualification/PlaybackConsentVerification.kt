package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.os.Bundle
import com.attentionstats.reels.ReelsCoordinator
import com.attentionstats.storage.ConsentRecord
import com.attentionstats.storage.HistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.attentionstats.detection.AdapterFrame
import org.attentionstats.reels.ItemFrame
import org.attentionstats.reels.PageCategory
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.ReelRoute
import org.attentionstats.reels.Surface
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual serialized owner with a private store; never replaces the live singleton/history. */
internal object PlaybackConsentVerification {
    fun run(instrumentation: Instrumentation): String = runBlocking {
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val target = instrumentation.targetContext
        // Constructor seeds the shared in-memory fingerprint from this existing secret.
        // Refuse to create/change the real installation's secret during an isolated trial.
        check(target.getSharedPreferences("detection", Context.MODE_PRIVATE).contains("item_secret"))
        val folder = File(target.noBackupFilesDir, "playback-consent-${UUID.randomUUID()}")
        check(folder.mkdir())
        val alias = "attentionstats.playback.consent.${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = folder }
        var store: HistoryStore? = null
        var owner: ReelsCoordinator? = null
        var checks = 0
        fun expect(value: Boolean, message: String) { check(value) { message }; checks++ }
        fun field(name: String) = ReelsCoordinator::class.java.getDeclaredField(name).apply { isAccessible = true }
        fun request(operation: ((String) -> Unit, (String) -> Unit) -> Unit): JSONObject {
            val done = CountDownLatch(1)
            var result: String? = null
            var error: String? = null
            operation({ result = it; done.countDown() }, { error = it; done.countDown() })
            check(done.await(20, TimeUnit.SECONDS) && error == null) { "Isolated playback request failed: $error" }
            return JSONObject(checkNotNull(result))
        }
        try {
            val history = HistoryStore.open(context, alias).also { store = it }
            val dao = history.database.data()
            val app = ReelApp.INSTAGRAM
            val at = System.currentTimeMillis()
            dao.configure(app.sourceId, true, ReelsCoordinator.NOTICE_REVISION.toLong(), at)
            dao.insertConsent(ConsentRecord(UUID.randomUUID().toString(), app.sourceId,
                ReelsCoordinator.NOTICE_REVISION, "allow", at, 1))
            val coordinator = ReelsCoordinator::class.java.getDeclaredConstructor(Context::class.java)
                .apply { isAccessible = true }.newInstance(context).also { owner = it }
            // Test-owned reflection avoids a production setter or singleton replacement.
            field("store").set(coordinator, history)
            field("health").set(coordinator, "busy") // opened() loads only this store's choices.
            fun snapshot() = request { ok, bad -> coordinator.diarySnapshot(1, ok, bad) }
            fun row(value: JSONObject) = value.getJSONArray("days").getJSONObject(0).getJSONArray("apps")
                .let { rows -> (0 until rows.length()).map(rows::getJSONObject).first { it.optString("social") == app.key } }
            snapshot()
            expect(coordinator.allowed(app), "Isolated source not admitted")
            instrumentation.sendStatus(1, Bundle().apply {
                putString("stream", "Playback details stage: awaiting original own Accessibility binding")
            })
            // Instrumentation startup can disconnect the selected service on this phone.
            // Give the authorized host operator time to restore that same selection;
            // the isolated test never changes permissions or mocks the platform grant.
            val deadline = SystemClock.elapsedRealtime() + 30_000
            while (!coordinator.grantEnabled() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(200)
            expect(coordinator.grantEnabled(), "Actual own Accessibility grant must be available")
            fun toggle(enabled: Boolean): JSONObject {
                val epoch = coordinator.epoch()
                val revision = checkNotNull(runBlocking { dao.diary() }).revision
                val value = request { ok, bad -> coordinator.configureDiary("playback", enabled, 30, 15, revision, ok, bad) }
                expect(coordinator.epoch() == epoch + 1, "Committed playback consent must advance acquisition epoch")
                expect(value.getBoolean("measurePlayback") == enabled, "Committed playback choice missing")
                return value
            }
            val base = SystemClock.elapsedRealtime()
            fun observe(offset: Long, position: Long, identity: Long = 9_001, duration: Long = 10_000,
                        epoch: Long = coordinator.epoch()): JSONObject {
                // No synthetic widget publication: suppress the owner's optional refresh.
                field("widgetAt").setLong(coordinator, SystemClock.elapsedRealtime())
                val frame = AdapterFrame(Surface.REELS, identity = identity, keys = setOf(identity),
                    method = "synthetic.playback", positionMs = position, durationMs = duration)
                val item = ItemFrame(base + offset, Surface.REELS, ReelRoute.VIEWER, identity,
                    setOf(identity), PageCategory.REEL, if (identity == 9_001L) 0 else 1)
                coordinator.observe(app, frame, item, 1, epoch)
                return snapshot() // serialized barrier after the submitted observation
            }
            toggle(true)
            observe(0, 0)
            observe(300, 0)
            observe(1_300, 1_000)
            val before = row(observe(2_300, 2_000))
            expect(before.getString("playbackMs") == "2000", "Fresh numeric frames must persist")
            val oldEpoch = coordinator.epoch()
            val off = toggle(false)
            expect(row(off).getString("playbackMs") == "2000", "Disable must retain facts")
            val revision = checkNotNull(dao.metadata()).lastRevision
            observe(3_300, 3_000, epoch = oldEpoch)
            expect(coordinator.observationStatus == "fenced_or_not_granted", "Precommit frame was admitted")
            expect(checkNotNull(dao.metadata()).lastRevision == revision, "Rejected frame wrote history")
            expect(row(observe(4_300, 4_000)).getString("playbackMs") == "2000", "Disabled interval was measured")
            val disabledEpoch = coordinator.epoch()
            toggle(true)
            observe(6_300, 6_000, epoch = disabledEpoch)
            expect(coordinator.observationStatus == "fenced_or_not_granted", "Disabled-epoch frame entered new consent")
            expect(row(observe(7_300, 7_000)).getString("playbackMs") == "2000", "First resumed frame filled disabled gap")
            val resumed = row(observe(8_300, 8_000))
            expect(resumed.getString("playbackMs") == "3000" && resumed.getString("watchCount") == "1",
                "Same active visit did not resume retained qualification")
            expect(row(observe(10_300, 10_000)).getString("completionCount") == "0", "Completion bridged consent gap")
            val epoch = coordinator.epoch()
            val done = CountDownLatch(1)
            var failure: String? = null
            coordinator.configureDiary("playback", false, 30, 15, -1,
                { done.countDown() }, { failure = it; done.countDown() })
            expect(done.await(20, TimeUnit.SECONDS) && failure == "REVISION_CONFLICT", "Stale consent must fail")
            expect(coordinator.epoch() == epoch, "Failed consent changed acquisition epoch")
            observe(11_000, 0, 9_002, 4_000)
            observe(11_300, 0, 9_002, 4_000)
            for (i in 1..4) observe(11_300 + i * 1_000L, i * 1_000L, 9_002, 4_000)
            val complete = row(snapshot())
            expect(complete.getString("watchCount") == "2" && complete.getString("completionCount") == "1",
                "New uninterrupted visit must qualify separately")
            expect(complete.getString("playbackMs") == "9000" && complete.getString("measuredVideoCount") == "2",
                "Confirmed facts lost or disabled gap added")
            val retained = row(toggle(false))
            expect(retained.getString("playbackMs") == "9000" && retained.getString("completionCount") == "1",
                "Completed visit facts lost on disable")
        } finally {
            owner?.let { coordinator ->
                val scope = field("scope").get(coordinator) as CoroutineScope
                scope.coroutineContext[Job]?.cancelAndJoin()
            }
            store?.close()
            check(folder.canonicalPath.startsWith(target.noBackupFilesDir.canonicalPath + File.separator))
            check(folder.deleteRecursively() && !folder.exists())
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keys.containsAlias(alias)) keys.deleteEntry(alias)
            check(!keys.containsAlias(alias))
        }
        "PASS: $checks isolated actual-owner playback consent/admission checks; store/key/worker cleared; synthetic frames only"
    }
}
