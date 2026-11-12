package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.ContextWrapper
import com.attentionstats.diary.DiaryEngine
import com.attentionstats.storage.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Synthetic retained history in an isolated encrypted store; never writes the user's database. */
internal object HistoryPageVerification {
    fun run(instrumentation: Instrumentation): Int = runBlocking {
        val target = instrumentation.targetContext
        val folder = File(target.noBackupFilesDir, "history-page-${UUID.randomUUID()}")
        check(folder.mkdir())
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = folder }
        val alias = "attentionstats.historypage.${UUID.randomUUID()}"
        var store: HistoryStore? = null
        var checks = 0
        fun expect(value: Boolean) { check(value) { "history page ${checks + 1}" }; checks++ }
        try {
            val history = HistoryStore.open(context, alias).also { store = it }
            val dao = history.database.data()
            val diary = DiaryEngine(context)
            suspend fun page(end: String = "") = JSONObject(diary.historyPage(history, end, diary.fence.get()) { false })
            fun source(page: JSONObject) = page.getJSONArray("sources").let { rows ->
                (0 until rows.length()).map { rows.getJSONObject(it) }.first { it.getString("key") == "instagram" }
            }
            val empty = page()
            expect(empty.isNull("firstDay") && empty.getInt("dayCount") == 0)
            expect(empty.isNull("olderEnd") && empty.isNull("newerEnd"))
            expect(source(empty).getString("videos") == "0" && source(empty).isNull("reelsMs"))
            val today = LocalDate.now()
            val zone = ZoneId.systemDefault()
            fun at(daysAgo: Long) = today.minusDays(daysAgo).atStartOfDay(zone).toInstant().toEpochMilli()
            val now = System.currentTimeMillis()
            // Earliest retained fact is an intervention, not an exposure or installation timestamp.
            dao.insertIntervention(InterventionEvent("old-break", "instagram", "break", at(65), 0, 0, 0))
            dao.insertEpisode(SurfaceEpisode("episode", "instagram", "viewer", at(60), at(60) + 100_000, 1, "partial"))
            suspend fun exposure(id: String, atMs: Long, ordinal: Long, category: String = "reel", revisit: Boolean = false) {
                dao.insertExposure(Exposure(id, "episode", "instagram", atMs, ordinal, ordinal, 1, "observed", 1,
                    "synthetic", zone.id, 0, category = category, revisit = revisit))
            }
            exposure("old-video", at(60) + 1_000, 1)
            exposure("new-video-1", now - 5_000, 2)
            exposure("new-video-2", now - 4_000, 3)
            exposure("revisit", now - 3_000, 4, revisit = true)
            exposure("photo", now - 2_000, 5, category = "photo")
            dao.putTimer(ReelsTimer("old-time", "instagram", "viewer", at(60), at(60) + 60_000, 60_000))
            dao.putTimer(ReelsTimer("new-time", "instagram", "viewer", now - 21_000, now - 1_000, 20_000))
            dao.insertUsage(listOf(AppUsageEvent("open", "epoch", "com.example.synthetic", at(60) + 1_000, 1),
                AppUsageEvent("close", "epoch", "com.example.synthetic", at(60) + 2_000, 2)))
            val latest = page()
            expect(latest.getString("firstDay") == today.minusDays(65).toString())
            expect(latest.getInt("dayCount") == 66)
            expect(latest.getString("lastRecordedDay") == today.toString())
            expect(source(latest).getString("videos") == "3")
            expect(source(latest).getString("reelsMs") == "80000")
            expect(latest.getJSONArray("sources").getJSONObject(1).isNull("reelsMs"))
            expect(latest.getJSONObject("snapshot").getJSONArray("days").length() == 30)
            expect(latest.getString("olderEnd") == today.minusDays(30).toString() && latest.isNull("newerEnd"))
            val middle = page(latest.getString("olderEnd"))
            expect(middle.getString("pageStart") == today.minusDays(59).toString())
            expect(middle.getString("newerEnd") == today.toString())
            val oldest = page(middle.getString("olderEnd"))
            expect(oldest.getString("pageStart") == today.minusDays(65).toString())
            expect(oldest.getJSONObject("snapshot").getJSONArray("days").length() == 6)
            expect(oldest.isNull("olderEnd"))
            expect(source(oldest).getString("videos") == "3")
            val oldDay = oldest.getJSONObject("snapshot").getJSONArray("days").getJSONObject(5)
            val apps = oldDay.getJSONArray("apps")
            val rows = (0 until apps.length()).map { apps.getJSONObject(it) }
            expect(rows.first { it.optString("social") == "instagram" }.getString("videoCount") == "1")
            expect(rows.first { it.getString("packageName") == "com.example.synthetic" }.getString("appMs") == "1000")
            expect(runCatching { page(today.plusDays(1).toString()) }.isFailure)
            expect(runCatching { page(today.minusDays(66).toString()) }.isFailure)
            // A last-day pause after midnight must confirm the full pre-boundary interval.
            dao.insertUsage(listOf(AppUsageEvent("tail-open", "epoch", "com.example.tail", at(59) - 120_000, 1),
                AppUsageEvent("tail-close", "epoch", "com.example.tail", at(59) + 120_000, 2)))
            val pairedTail = page(today.minusDays(60).toString()).getJSONObject("snapshot")
                .getJSONArray("days").getJSONObject(5).getJSONArray("apps")
            expect((0 until pairedTail.length()).map { pairedTail.getJSONObject(it) }
                .first { it.getString("packageName") == "com.example.tail" }.getString("appMs") == "120000")
            // Usage-event-only history must establish bounds even without media or interventions.
            dao.deleteEpisodes(); dao.deleteTimers(); dao.deleteInterventions()
            val usageOnly = page()
            expect(usageOnly.getString("firstDay") == today.minusDays(60).toString())
            expect(source(usageOnly).getString("videos") == "0" && source(usageOnly).isNull("reelsMs"))
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
