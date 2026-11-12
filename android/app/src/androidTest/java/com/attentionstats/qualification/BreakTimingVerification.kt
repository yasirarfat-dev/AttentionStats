package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.ContextWrapper
import androidx.room3.withWriteTransaction
import com.attentionstats.diary.DiaryEngine
import com.attentionstats.storage.HistoryStore
import kotlinx.coroutines.runBlocking
import org.attentionstats.reels.CounterSignal
import org.attentionstats.reels.PageCategory
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** Actual diary/timer integration with synthetic clocks and a separate encrypted database. */
internal object BreakTimingVerification {
    fun run(instrumentation: Instrumentation): Int = runBlocking {
        val target = instrumentation.targetContext
        val folder = File(target.noBackupFilesDir, "break-timing-${UUID.randomUUID()}")
        check(folder.mkdir())
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = folder }
        val alias = "attentionstats.breaktiming.${UUID.randomUUID()}"
        var store: HistoryStore? = null
        var checks = 0
        fun expect(value: Boolean) { check(value) { "break timing ${checks + 1}" }; checks++ }
        try {
            val history = HistoryStore.open(context, alias).also { store = it }
            val dao = history.database.data()
            val diary = DiaryEngine(context)
            suspend fun configure(count: Int = 1000, minutes: Int = 1, enabled: Boolean = true) {
                history.database.withWriteTransaction {
                    dao.putDiary(checkNotNull(dao.diary()).copy(reminderEnabled = enabled,
                        reminderCount = count, reminderMinutes = minutes))
                }
            }
            var elapsed = 1_000L
            var wall = 1_000_000L
            suspend fun observe(delta: Long = 0, counted: Boolean = false,
                app: ReelApp = ReelApp.INSTAGRAM, surface: Surface = Surface.REELS,
                wallDelta: Long = delta, permitted: Boolean = true) {
                elapsed += delta; wall += wallDelta
                diary.observe(history, app, CounterSignal(elapsed, surface, 1, 1, PageCategory.REEL), wall,
                    counted, { permitted })
            }
            configure()
            observe()
            repeat(59) { observe(1_000) }
            expect(diary.reminder == null)
            expect(diary.breakProgress > .98f && diary.breakProgress < 1f)
            observe(1_000)
            expect(diary.reminder?.reason == "time")
            expect(diary.reminder?.durationMs == 60_000L && diary.reminder?.count == 0)
            expect(dao.timers(0, Long.MAX_VALUE).sumOf { it.durationMs } == 60_000L)
            diary.dismiss()
            expect(diary.reminder == null && diary.breakProgress == 0f)
            observe(1_000)
            expect(diary.reminder == null && diary.breakProgress < .02f)

            // Same unlock stretch spans apps, but never charges the handoff interval.
            diary.resetLive(); configure(count = 3, minutes = 60)
            observe(counted = true)
            observe(1_000, counted = true, app = ReelApp.TIKTOK)
            expect(diary.reminder == null)
            observe(1_000, counted = true, app = ReelApp.TIKTOK)
            expect(diary.reminder?.count == 3 && diary.reminder?.app == ReelApp.TIKTOK)
            expect(diary.reminder?.durationMs == 1_000L)

            // Exactly five minutes retains the run; strictly longer resets it.
            diary.resetLive(); configure(count = 3)
            observe(counted = true); diary.pauseSurface()
            observe(300_000, counted = true)
            expect(diary.reminder == null && diary.breakProgress > .66f)
            observe(1_000, counted = true)
            expect(diary.reminder?.count == 3)
            diary.resetLive()
            observe(counted = true); diary.pauseSurface()
            observe(300_001, counted = true)
            expect(diary.reminder == null && diary.breakProgress < .34f)
            observe(1_000, counted = true)
            expect(diary.reminder == null)
            observe(1_000, counted = true)
            expect(diary.reminder?.count == 3 && diary.reminder?.durationMs == 2_000L)

            // A pending user decision survives a gap instead of silently disappearing.
            val pending = diary.reminder
            diary.pauseSurface(); observe(300_001)
            expect(diary.reminder == pending)
            diary.dismiss(); observe(1_000)
            expect(diary.reminder == null && diary.breakProgress < .02f)

            // Unknown/outside time, long acquisition holes and wall-clock jumps add no duration.
            diary.resetLive(); configure()
            observe()
            observe(1_000, surface = Surface.OUTSIDE)
            observe(60_000)
            observe(2_001)
            observe(1_000, wallDelta = -10_000)
            observe(1_000, wallDelta = 10_000)
            expect(diary.reminder == null && diary.breakProgress == 0f)
            observe(1_000)
            expect(diary.breakProgress > 0f && diary.breakProgress < .02f)
            observe(1_000, permitted = false)
            expect(diary.reminder == null && diary.breakProgress == 0f)
            observe(1_000)
            expect(diary.breakProgress == 0f)
            configure(enabled = false); observe(1_000, counted = true)
            expect(diary.reminder == null && diary.breakProgress == -1f)
            checks
        } finally {
            store?.close()
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keys.containsAlias(alias)) keys.deleteEntry(alias)
            check(folder.parentFile?.canonicalFile == target.noBackupFilesDir.canonicalFile)
            check(folder.deleteRecursively())
        }
    }
}
