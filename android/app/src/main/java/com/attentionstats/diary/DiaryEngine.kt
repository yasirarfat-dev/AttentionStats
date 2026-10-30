package com.attentionstats.diary

import android.app.AppOpsManager
import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.os.UserManager
import androidx.room3.withWriteTransaction
import com.attentionstats.storage.AppUsageEvent
import com.attentionstats.storage.CoverageInterval
import com.attentionstats.storage.ConsentRecord
import com.attentionstats.storage.DiaryConfiguration
import com.attentionstats.storage.HistoryStore
import com.attentionstats.storage.ReelsTimer
import org.attentionstats.reels.CounterSignal
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

internal class DiaryInterrupted : RuntimeException()

/** Invoked only on the history coordinator's serialized IO queue; no parallel writer. */
internal class DiaryEngine(private val context: Context) {
    /** Apps whose short-video viewer is blocked, with their daily limit (0 = always blocked). */
    @Volatile var blockLimits: Map<ReelApp, Int> = emptyMap()
        private set
    val blockedApps: Set<ReelApp> get() = blockLimits.keys
    @Volatile var breakSeconds = 10
        private set
    @Volatile var includeRecognizedAds = true
        private set
    suspend fun loadChoices(history: HistoryStore) {
        blockLimits = history.database.data().blockers().filter { it.enabled }
            .mapNotNull { config -> ReelApp.byKey(config.appId)?.let { it to config.dailyLimit } }.toMap()
        val config = checkNotNull(history.database.data().diary())
        includeRecognizedAds = config.includeRecognizedAds
        breakSeconds = config.breakSeconds
    }
    val fence = AtomicLong()
    @Volatile var reminder: Reminder? = null
        private set
    /** 0..1 progress toward the next mindful break, or -1 when breaks are off. Drives the HUD ring. */
    @Volatile var breakProgress = -1f
        private set
    data class Reminder(val app: ReelApp, val count: Int, val durationMs: Long, val reason: String)
    private data class Sample(val app: ReelApp, val route: String, val window: Int, val wall: Long, val elapsed: Long)
    private var sample: Sample? = null
    private var timer: ReelsTimer? = null
    private var sessionApp: ReelApp? = null
    private var sessionCount = 0
    private var sessionDuration = 0L
    private var lastQueryElapsed = 0L
    /** Last time a short-video surface was on screen; a long gap starts a new run toward the next break. */
    private var lastEligibleElapsed = 0L
    var usageStatus = "disabled"
        private set

    @Suppress("DEPRECATION") // checkOpNoThrow is available on all supported APIs.
    fun usageGranted(): Boolean = context.getSystemService(AppOpsManager::class.java)
        .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

    fun resetLive() { sample = null; timer = null; sessionApp = null; lastEligibleElapsed = 0L; dismiss() }
    fun pauseSurface() { sample = null; timer = null }
    fun dismiss() { reminder = null; sessionCount = 0; sessionDuration = 0; if (breakProgress > 0) breakProgress = 0f }

    suspend fun configure(history: HistoryStore, action: String, enabled: Boolean, count: Int, minutes: Int,
        expected: Long): Boolean {
        val dao = history.database.data()
        val config = checkNotNull(dao.diary())
        if (config.revision != expected) return false
        val now = System.currentTimeMillis()
        history.database.withWriteTransaction {
            val next = when (action) {
                "usage" -> {
                    dao.insertConsent(ConsentRecord(UUID.randomUUID().toString(), "usage.apps", 1,
                        if (enabled) "allow" else "withdraw", now, Math.addExact(checkNotNull(dao.metadata()).configurationRevision, 1)))
                    // Close the old epoch before disabling/restarting; no OS history before this decision.
                    if (config.usageEnabled) dao.insertUsage(listOf(boundary(config.usageEpoch, now)))
                    config.copy(usageEnabled = enabled, usageEpoch = UUID.randomUUID().toString(),
                        effectiveAtMs = now, queryThroughMs = now, grantLost = false)
                }
                "reminder" -> config.copy(reminderEnabled = enabled, reminderCount = count, reminderMinutes = minutes)
                "onboarding" -> config.copy(onboardingSeen = true)
                "ads" -> config.copy(includeRecognizedAds = enabled)
                "break" -> config.copy(breakSeconds = count.coerceIn(3, 60))
                else -> {
                    require(action.startsWith("block."))
                    val app = checkNotNull(ReelApp.byKey(action.removePrefix("block.")))
                    dao.putBlocker(com.attentionstats.storage.ReelsBlockConfiguration(app.key, enabled,
                        if (count >= 1000) 0 else count))
                    dao.insertConsent(ConsentRecord(UUID.randomUUID().toString(), "protect.${app.sourceId}", 1,
                        if (enabled) "allow" else "withdraw", now, Math.addExact(checkNotNull(dao.metadata()).configurationRevision, 1)))
                    config
                }
            }
            dao.putDiary(next.copy(revision = Math.addExact(config.revision, 1)))
            dao.advanceConfigurationRevision()
        }
        resetLive()
        loadChoices(history)
        lastQueryElapsed = 0
        return true
    }

    private fun boundary(epoch: String, now: Long) = AppUsageEvent("boundary:$epoch:$now", epoch, "", now, -1)

    suspend fun collect(history: HistoryStore, capturedFence: Long) {
        fun checkFence() { if (capturedFence != fence.get()) throw DiaryInterrupted() }
        checkFence()
        val dao = history.database.data()
        var config = checkNotNull(dao.diary())
        if (!config.usageEnabled) { usageStatus = "disabled"; return }
        val now = System.currentTimeMillis()
        if (!usageGranted() || !context.getSystemService(UserManager::class.java).isUserUnlocked) {
            usageStatus = "permission_needed"
            if (!config.grantLost) history.database.withWriteTransaction {
                checkFence()
                dao.insertUsage(listOf(boundary(config.usageEpoch, config.queryThroughMs)))
                dao.insertCoverage(CoverageInterval(UUID.randomUUID().toString(), "usage.apps",
                    config.queryThroughMs, now, "unknown", "observed_access_gap", config.revision))
                dao.putDiary(config.copy(grantLost = true, queryThroughMs = now))
                dao.advanceRevision()
                checkFence()
            }
            return
        }
        if (config.grantLost || config.queryThroughMs > now) {
            // Resume from a fresh consent epoch; never backfill an observed grant-loss/clock gap.
            config = config.copy(usageEpoch = UUID.randomUUID().toString(), effectiveAtMs = now,
                queryThroughMs = now, grantLost = false)
            history.database.withWriteTransaction { checkFence(); dao.putDiary(config); dao.advanceRevision(); checkFence() }
        }
        val elapsed = SystemClock.elapsedRealtime()
        if (lastQueryElapsed != 0L && elapsed - lastQueryElapsed < 10_000) return
        lastQueryElapsed = elapsed
        var start = maxOf(config.effectiveAtMs, config.queryThroughMs - 300_000, now - 7 * 86_400_000L)
        var retentionGap = start > config.queryThroughMs
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val digest = MessageDigest.getInstance("SHA-256")
        val alphabet = "0123456789abcdef"
        var slices = 0
        while (start < now && slices++ < 28) {
            val end = minOf(now, start + 21_600_000L)
            val result = manager.queryEvents(start, end) ?: run { usageStatus = "unavailable"; return }
            val values = mutableListOf<AppUsageEvent>()
            val event = UsageEvents.Event()
            var scanned = 0
            while (result.hasNextEvent()) {
                if (++scanned > 20_000) { usageStatus = "dense_history"; return } // No partial slice commit.
                if (scanned % 256 == 0) checkFence()
                result.getNextEvent(event)
                if (event.eventType !in setOf(1, 2, 15, 16, 17, 18, 23, 26, 27) || event.timeStamp !in start until end) continue
                val app = if (event.eventType in setOf(1, 2, 23)) event.packageName ?: continue else ""
                if (app.length > 256) continue
                val key = "${config.usageEpoch}|${event.timeStamp}|${event.eventType}|$app"
                val bytes = digest.digest(key.toByteArray())
                val id = CharArray(64) { index ->
                    val value = bytes[index / 2].toInt() and 255
                    alphabet[if (index % 2 == 0) value ushr 4 else value and 15]
                }.concatToString()
                values.add(AppUsageEvent(id, config.usageEpoch, app, event.timeStamp, event.eventType))
            }
            if (capturedFence != fence.get() || !usageGranted()) throw DiaryInterrupted()
            history.database.withWriteTransaction {
                if (capturedFence != fence.get() || !usageGranted()) throw DiaryInterrupted()
                if (retentionGap) {
                    dao.insertUsage(listOf(boundary(config.usageEpoch, config.queryThroughMs)))
                    dao.insertCoverage(CoverageInterval(UUID.randomUUID().toString(), "usage.apps",
                        config.queryThroughMs, start, "unknown", "outside_recovery_window", config.revision))
                }
                dao.insertUsage(values)
                config = config.copy(queryThroughMs = end)
                dao.putDiary(config)
                dao.advanceRevision()
                if (capturedFence != fence.get() || !usageGranted()) throw DiaryInterrupted()
            }
            start = end
            retentionGap = false
        }
        usageStatus = "partial" // Successful queries do not prove complete OS coverage.
    }

    suspend fun observe(history: HistoryStore, app: ReelApp, signal: CounterSignal, wall: Long, counted: Boolean,
        permitted: () -> Boolean = { true }) {
        if (!permitted()) { resetLive(); return }
        val eligible = signal.surface == Surface.REELS
        if (!eligible) { sample = null; timer = null; return }
        // A "stretch" ends after a real pause away from short videos (screen still unlocked).
        if (lastEligibleElapsed != 0L && signal.elapsedMs - lastEligibleElapsed > RUN_GAP_MS && reminder == null) {
            sessionCount = 0; sessionDuration = 0; if (breakProgress > 0) breakProgress = 0f
        }
        lastEligibleElapsed = signal.elapsedMs
        if (sessionApp != app) {
            pauseSurface(); sessionApp = app
            reminder = reminder?.copy(app = app)
        }
        val previous = sample
        val next = Sample(app, signal.route.key, signal.windowId, wall, signal.elapsedMs)
        var delta = 0L
        if (previous != null && previous.app == app && previous.route == next.route && previous.window == next.window) {
            val elapsed = next.elapsed - previous.elapsed
            val wallDelta = wall - previous.wall
            if (elapsed in 1..2_000 && wallDelta > 0 && kotlin.math.abs(wallDelta - elapsed) <= 250) {
                delta = elapsed
                val current = timer?.takeIf { it.endMs == previous.wall }
                timer = if (current != null) current.copy(endMs = wall, durationMs = current.durationMs + elapsed)
                    else ReelsTimer(UUID.randomUUID().toString(), app.key, next.route, previous.wall, wall, elapsed)
                history.database.withWriteTransaction {
                    if (!permitted()) throw DiaryInterrupted()
                    history.database.data().putTimer(checkNotNull(timer))
                    history.database.data().advanceRevision()
                    if (!permitted()) throw DiaryInterrupted()
                }
            } else timer = null
        } else timer = null
        sample = next
        val config = checkNotNull(history.database.data().diary())
        if (!config.reminderEnabled) { dismiss(); breakProgress = -1f; return }
        if (counted && (includeRecognizedAds || signal.category != org.attentionstats.reels.PageCategory.AD)) sessionCount++
        sessionDuration += delta
        breakProgress = maxOf(sessionCount.toFloat() / maxOf(1, config.reminderCount),
            sessionDuration.toFloat() / maxOf(1L, config.reminderMinutes * 60_000L)).coerceIn(0f, 1f)
        if (reminder == null && (sessionCount >= config.reminderCount || sessionDuration >= config.reminderMinutes * 60_000L)) {
            reminder = Reminder(app, sessionCount, sessionDuration, if (sessionCount >= config.reminderCount) "count" else "time")
        }
    }

    suspend fun snapshot(history: HistoryStore, days: Int, capturedFence: Long = fence.get(),
        blockingAvailable: (ReelApp) -> Boolean = { false }): String {
        collect(history, capturedFence)
        val dao = history.database.data()
        val config = checkNotNull(dao.diary())
        loadChoices(history)
        val zone = ZoneId.systemDefault()
        val today = Instant.now().atZone(zone).toLocalDate()
        val first = today.minusDays(days - 1L)
        val start = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val events = dao.usageEvents(start - 21_600_000, now)
        val timers = dao.timers(start, now)
        val bounded = events.size <= 100_000 && timers.size <= 100_000
        val timeline = if (bounded) events.map { UsageTimeline.Event(it.epoch, it.packageName, it.atMs, it.kind) } else emptyList()
        val through = minOf(now, config.queryThroughMs.takeIf { config.usageEnabled && !config.grantLost } ?: now)
        val intervals = UsageTimeline.reduce(timeline, through)
        val entries = UsageTimeline.entries(timeline, through)
        val labels = mutableMapOf<String, String>()
        val rows = JSONArray()
        for (index in 0 until days) {
            val day = first.plusDays(index.toLong())
            val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val until = minOf(now, day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            val appTimes = intervals.filter { it.start < until && it.end > from }.groupBy { it.app }
                .mapValues { (_, values) -> values.sumOf { minOf(it.end, until) - maxOf(it.start, from) } }
            val opens = entries.filter { it.at >= from && it.at < until }.groupingBy { it.app }.eachCount()
            val reelTimes = if (bounded) timers.filter { it.startMs < until && it.endMs > from }.groupBy { it.appId }
                .mapValues { (_, values) -> values.sumOf {
                    val overlap = minOf(it.endMs, until) - maxOf(it.startMs, from)
                    if (it.endMs <= it.startMs) 0L else (it.durationMs.toDouble() * overlap / (it.endMs - it.startMs)).toLong()
                } } else emptyMap()
            val counts = dao.reelsCounts(from, until).associate { it.appId to it.count }
            val photos = dao.categoryTotalsByApp(from, until, "photo").associate { it.appId to it.count }
            val revisits = dao.revisitCounts(from, until).associate { it.appId to it.count }
            val packages = appTimes.keys + opens.keys + ReelApp.entries.map { it.packageName }
            val apps = JSONArray()
            for (pkg in packages.sortedByDescending { appTimes[it] ?: 0 }) {
                val social = ReelApp.byPackage(pkg)
                val label = labels.getOrPut(pkg) { label(pkg) }
                val ads = social?.let { dao.categoryCount(it.key, from, until, "ad") } ?: 0
                val total = (counts[social?.key] ?: 0) - (photos[social?.key] ?: 0)
                apps.put(JSONObject().put("packageName", pkg).put("name", label)
                    .put("social", social?.key ?: JSONObject.NULL)
                    .put("appMs", appTimes[pkg]?.toString() ?: JSONObject.NULL)
                    .put("appOpens", if (pkg in appTimes || pkg in opens) (opens[pkg] ?: 0).toString() else JSONObject.NULL)
                    .put("reelsMs", reelTimes[social?.key]?.toString() ?: JSONObject.NULL)
                    .put("reelsCount", (if (config.includeRecognizedAds) total else maxOf(0, total - ads)).toString())
                    .put("adCount", ads.toString())
                    .put("photoCount", (photos[social?.key] ?: 0).toString())
                    .put("revisitCount", (revisits[social?.key] ?: 0).toString()))
            }
            val hours = JSONArray(LongArray(24).also { buckets ->
                dao.hourly(from, until).forEach { if (it.category in 0..23) buckets[it.category] = it.count }
            }.map { it.toString() })
            val interventions = JSONObject()
            dao.interventionTotals(from, until).forEach { total ->
                val key = total.kind + "." + total.choice.ifEmpty { "shown" }
                interventions.put(key, interventions.optLong(key) + total.count)
            }
            rows.put(JSONObject().put("day", day.toString()).put("apps", apps).put("hours", hours)
                .put("interventions", interventions))
        }
        return JSONObject().put("contractVersion", 2).put("revision", config.revision.toString())
            .put("usageEnabled", config.usageEnabled).put("usageGranted", usageGranted())
            .put("batteryOptimized", !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName))
            .put("backgroundRestricted", if (Build.VERSION.SDK_INT >= 28) context.getSystemService(ActivityManager::class.java).isBackgroundRestricted else JSONObject.NULL)
            .put("usageStatus", if (bounded) usageStatus else "dense_history")
            .put("effectiveAtMs", config.effectiveAtMs.toString()).put("queryThroughMs", config.queryThroughMs.toString())
            .put("reminderEnabled", config.reminderEnabled).put("reminderCount", config.reminderCount)
            .put("reminderMinutes", config.reminderMinutes).put("onboardingSeen", config.onboardingSeen)
            .put("includeRecognizedAds", config.includeRecognizedAds)
            .put("breakSeconds", config.breakSeconds)
            .put("blockers", JSONArray(ReelApp.entries.map { app -> JSONObject().put("app", app.key)
                .put("enabled", app in blockedApps).put("dailyLimit", blockLimits[app] ?: 0)
                .put("available", blockingAvailable(app)) }))
            .put("zone", zone.id).put("days", rows).toString()
    }

    @Suppress("DEPRECATION")
    private fun label(pkg: String): String = try {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString().take(100)
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) { pkg.take(100) }

    companion object {
        /** Minutes away from short videos after which a break countdown starts over. */
        const val RUN_GAP_MS = 5 * 60_000L
    }
}
