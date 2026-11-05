package com.attentionstats.reels

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.room3.withWriteTransaction
import com.attentionstats.storage.ConsentRecord
import com.attentionstats.storage.CoverageInterval
import com.attentionstats.storage.Exposure
import com.attentionstats.storage.HistoryStore
import com.attentionstats.storage.InterventionEvent
import com.attentionstats.storage.SourceConfiguration
import com.attentionstats.storage.SurfaceEpisode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.attentionstats.detection.AdapterFrame
import org.attentionstats.detection.shared.Fingerprint
import org.attentionstats.reels.CounterSignal
import org.attentionstats.reels.EncounterTracker
import org.attentionstats.reels.ItemFrame
import org.attentionstats.reels.PageCategory
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.ReelRoute
import org.attentionstats.reels.Surface
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

/**
 * Single owner of short-video history. Every observation and mutation runs on one serialized
 * IO queue, so the accessibility service, the UI bridge and storage never race.
 */
internal class ReelsCoordinator private constructor(private val context: Context) {
    private val diary = com.attentionstats.diary.DiaryEngine(context)
    internal val schedules = com.attentionstats.protection.ScheduleOwner(context)
    private val archives = com.attentionstats.backup.ArchiveOwner(context)
    private val unlockSession = UnlockSessionCounter()
    val reminder get() = diary.reminder
    val breakSeconds get() = diary.breakSeconds
    val countPhotos get() = diary.countPhotos
    private val playback = mutableMapOf<ReelApp, org.attentionstats.reels.PlaybackEvidence>()
    private val lastPlayback = mutableMapOf<String, org.attentionstats.reels.PlaybackEvidence.Result>()
    private class ObservationInvalidated : RuntimeException()
    private val jobs = Channel<suspend () -> Unit>(256)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private var store: HistoryStore? = null
    private val epoch = AtomicLong()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val trackers = mutableMapOf<ReelApp, EncounterTracker>()
    private val visits = mutableMapOf<ReelApp, String>()
    private val routes = mutableMapOf<ReelApp, ReelRoute>()
    private data class OpenExposure(val id: String, val atMs: Long)
    private val openExposures = mutableMapOf<ReelApp, OpenExposure>()
    private var sessionId = UUID.randomUUID().toString()
    private var sessionOrdinal = 0L
    private val itemSecret: ByteArray
    @Volatile private var configuration: Map<String, SourceConfiguration> = emptyMap()
    @Volatile private var currentNotices: Set<String> = emptySet()
    @Volatile var overlayEnabled = false
        private set
    @Volatile private var showCounterIcons = true
    @Volatile private var allAppsToday = true
    @Volatile var serviceConnected = false
    @Volatile var health = "starting"
        private set
    /** Content-free transient status for the explicitly invoked test APK; never persisted or bridged. */
    @JvmField @Volatile var acquisitionStatus = "not_sampled"
    @JvmField @Volatile var observationStatus = "not_observed"
    @Volatile var liveOverlay: OverlaySnapshot? = null
        private set
    /** Today's headline encounter count per app, refreshed after every write; feeds daily limits. */
    private val todayCounts = ConcurrentHashMap<ReelApp, Long>()
    /** Elapsed-realtime deadline until which a person chose to allow a blocked viewer. */
    private val allowances = ConcurrentHashMap<ReelApp, Long>()
    /** Elapsed-realtime of the most recent recognized short-video surface per app (detection health). */
    private val lastRecognized = ConcurrentHashMap<ReelApp, Long>()
    private val lastRecognizedWall = ConcurrentHashMap<ReelApp, Long>()

    data class OverlayCategory(val category: PageCategory, val visit: Long, val today: Long)
    data class OverlaySnapshot(val app: ReelApp, val visit: Long, val today: Long, val epoch: Long,
        val categories: List<OverlayCategory> = emptyList(), val scrollBack: Boolean = false,
        val showIcons: Boolean = true, val allAppsToday: Boolean = true,
        val route: ReelRoute = ReelRoute.VIEWER, val includeRecognizedAds: Boolean = true,
        val sessionMs: Long = 0, val breakProgress: Float = -1f)
    data class BlockDecision(val app: ReelApp, val kind: String, val today: Long, val limit: Int)

    init {
        val prefs = context.getSharedPreferences("detection", Context.MODE_PRIVATE)
        val stored = prefs.getString("item_secret", null)
        itemSecret = stored?.let { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }
            ?: ByteArray(32).also {
                SecureRandom().nextBytes(it)
                prefs.edit().putString("item_secret", android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)).apply()
            }
        Fingerprint.seed(java.nio.ByteBuffer.wrap(itemSecret, 0, 8).long)
        scope.launch { for (job in jobs) job() }
    }

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    fun epoch(): Long = epoch.get()
    fun allowed(app: ReelApp): Boolean = health == "ready" && app.sourceId in currentNotices &&
        configuration[app.sourceId]?.let { it.enabled && !it.paused } == true
    /** Consented and switched on, but paused by the person (HUD shows a resume pill). */
    fun pausedSource(app: ReelApp): Boolean = health == "ready" && app.sourceId in currentNotices &&
        configuration[app.sourceId]?.let { it.enabled && it.paused } == true
    fun anyAllowed(): Boolean = ReelApp.entries.any { allowed(it) }
    fun grantEnabled(): Boolean {
        val expected = ComponentName(context, ReelsAccessibilityService::class.java)
        return context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName(it.resolveInfo.serviceInfo.packageName, it.resolveInfo.serviceInfo.name) == expected }
    }
    fun grantSelected(): Boolean {
        val expected = ComponentName(context, ReelsAccessibilityService::class.java)
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.any { ComponentName.unflattenFromString(it) == expected } == true
    }

    private fun publish() { main.post { listeners.forEach { it() } } }
    private fun invalidate() {
        epoch.incrementAndGet()
        diary.fence.incrementAndGet()
        liveOverlay = null
        publish()
    }

    private fun enqueue(onError: (String) -> Unit = {}, operation: suspend () -> Unit) {
        if (!jobs.trySend {
            try { operation() }
            catch (_: ObservationInvalidated) { /* A privacy/source fence rolls back this observation. */ }
            catch (_: com.attentionstats.diary.DiaryInterrupted) { onError("INTERRUPTED") }
            catch (_: Exception) {
                health = "storage_unavailable"
                try { store?.close() } catch (_: Exception) { }
                store = null
                visits.clear(); routes.clear(); trackers.clear(); openExposures.clear()
                unlockSession.clear()
                invalidate()
                onError("STORAGE_UNAVAILABLE")
            }
        }.isSuccess) {
            health = "busy"
            invalidate()
            onError("BUSY")
        }
    }

    private suspend fun opened(): HistoryStore {
        val current = store
        if (current != null) {
            if (health == "busy") { refreshConfiguration(current); health = "ready" }
            return current
        }
        archives.cleanupAbandoned()
        return HistoryStore.open(context).also {
            store = it
            refreshConfiguration(it)
            health = "ready"
            refreshTodayCounts(it)
            publish()
        }
    }

    private suspend fun refreshConfiguration(history: HistoryStore) {
        schedules.load(history)
        diary.loadChoices(history)
        configuration = history.database.data().configurations().associateBy { it.sourceId }
        currentNotices = ReelApp.entries.filter {
            (history.database.data().allowedNotice(it.sourceId) ?: 0) >= NOTICE_REVISION
        }.map { it.sourceId }.toSet()
        val display = checkNotNull(history.database.data().overlay())
        overlayEnabled = display.enabled
        showCounterIcons = display.showIcons
        allAppsToday = display.allAppsToday
    }

    private fun revision(generation: String, value: Long) = "$generation:$value"
    private suspend fun checkRevision(history: HistoryStore, expected: String): Boolean {
        val metadata = checkNotNull(history.database.data().metadata())
        return expected == revision(metadata.generation, metadata.configurationRevision)
    }

    fun snapshot(success: (String) -> Unit, failure: (String) -> Unit) = enqueue(failure) {
        success(snapshotNow(opened()).toString())
    }

    fun scheduleSnapshot(success: (String) -> Unit, failure: (String) -> Unit) = enqueue(failure) {
        opened()
        success(schedules.snapshot(grantEnabled() && serviceConnected))
    }
    fun exportArchive(cancelled: java.util.concurrent.atomic.AtomicBoolean,
        success: (ByteArray) -> Unit, failure: (String) -> Unit) = enqueue(failure) {
        try {
            val plain = com.attentionstats.backup.LogicalArchive.export(opened()) { check(!cancelled.get()) }
            val encrypted = try { com.attentionstats.backup.ArchiveEnvelope.write(plain,
                com.attentionstats.backup.ArchiveEnvelope.packageCredential(), cancel = { check(!cancelled.get()) },
                profile = com.attentionstats.backup.ArchiveEnvelope.PUBLIC_PACKAGE_PROFILE) }
                finally { plain.fill(0) }
            success(encrypted)
        } catch (_: Exception) { failure(if (cancelled.get()) "CANCELLED" else "ARCHIVE_LIMIT_OR_EXPORT_FAILED") }
    }
    fun previewArchive(bytes: ByteArray, cancelled: java.util.concurrent.atomic.AtomicBoolean,
        success: (String, Long) -> Unit, failure: (String) -> Unit) = enqueue(failure) {
        try {
            val staged = archives.preview(opened(), bytes, com.attentionstats.backup.ArchiveEnvelope.packageCredential(),
                profile = com.attentionstats.backup.ArchiveEnvelope.PUBLIC_PACKAGE_PROFILE, cancel = { check(!cancelled.get()) })
            success(staged.token, staged.summary.rows)
        } catch (_: Exception) { failure(if (cancelled.get()) "CANCELLED" else "ARCHIVE_REJECTED") }
        finally { bytes.fill(0) }
    }
    fun cancelArchive() = enqueue { archives.clear() }
    fun restoreArchive(token: String, cancelled: java.util.concurrent.atomic.AtomicBoolean,
        success: () -> Unit, failure: (String) -> Unit) {
        invalidate()
        enqueue(failure) {
            val staged = archives.pending
            if (staged == null || staged.token != token) { failure("STALE_PREVIEW"); return@enqueue }
            var committed = false
            try {
                val history = opened()
                val dao = history.database.data()
                val metadata = checkNotNull(dao.metadata())
                if (metadata.lastRevision != staged.historyRevision || metadata.configurationRevision != staged.configurationRevision) {
                    failure("STALE_PREVIEW"); return@enqueue
                }
                val oldDiaryRevision = checkNotNull(dao.diary()).revision
                history.database.withWriteTransaction {
                    com.attentionstats.backup.LogicalArchive.replace(history, staged.history) { check(!cancelled.get()) }
                    val imported = checkNotNull(dao.diary())
                    dao.putDiary(imported.copy(usageEnabled = false, reminderEnabled = false,
                        measurePlayback = false,
                        usageEpoch = "", queryThroughMs = 0, effectiveAtMs = 0, grantLost = false, onboardingSeen = true,
                        revision = Math.addExact(maxOf(oldDiaryRevision, imported.revision), 1)))
                    dao.configurations().forEach { dao.configure(it.sourceId, false, it.consentRevision, System.currentTimeMillis()) }
                    dao.disableBlockers(); dao.setOverlay(false)
                    val protection = dao.protection()
                    if (protection != null) {
                        val plan = com.attentionstats.protection.ScheduleOwner.decode(protection.document)
                        val disabled = plan.copy(windows = plan.windows.map { it.copy(enabled = false) })
                        disabled.validate(schedules.excluded)
                        dao.putProtection(protection.copy(revision = Math.addExact(protection.revision, 1),
                            document = com.attentionstats.protection.ScheduleOwner.encode(disabled).toString()))
                    }
                    dao.advanceConfigurationRevision()
                    check(!cancelled.get())
                }
                // Commit is durable here; cancellation after this point must not report a rollback.
                committed = true
                main.post { com.attentionstats.autoscroll.AutoScrollSession.clear() }
                trackers.clear(); visits.clear(); routes.clear(); openExposures.clear(); unlockSession.clear(); allowances.clear()
                diary.resetLive(); refreshConfiguration(history); refreshTodayCounts(history); publish()
                archives.clear(); success()
            } catch (_: Exception) { if (committed) { health = "storage_unavailable"; publish(); success() } else failure("RESTORE_FAILED") }
            finally { archives.clear() }
        }
    }
    fun saveSchedules(document: String, expected: Long, success: () -> Unit, failure: (String) -> Unit) = enqueue(failure) {
        val history = opened()
        val valid = try { com.attentionstats.protection.ScheduleOwner.decode(document).also { it.validate(schedules.excluded) }; true }
            catch (_: Exception) { false }
        if (!valid) { failure("INVALID_ARGUMENT"); return@enqueue }
        if (!schedules.save(history, document, expected)) { failure("REVISION_CONFLICT"); return@enqueue }
        publish(); success()
    }

    fun diarySnapshot(days: Int, success: (String) -> Unit, failure: (String) -> Unit) {
        val capturedFence = diary.fence.get()
        enqueue(failure) { success(diary.snapshot(opened(), days, capturedFence, ::blockingAvailable)) }
    }
    fun configureDiary(action: String, enabled: Boolean, count: Int, minutes: Int, expected: Long,
        success: (String) -> Unit, failure: (String) -> Unit) {
        diary.fence.incrementAndGet() // Fence queued requests as well as the active OS query.
        enqueue(failure) {
            val history = opened()
            if (!diary.configure(history, action, enabled, count, minutes, expected)) {
                failure("REVISION_CONFLICT"); return@enqueue
            }
            if (action.startsWith("block.")) ReelApp.byKey(action.removePrefix("block."))?.let { allowances.remove(it) }
            if (action == "playback") { playback.clear(); lastPlayback.clear() }
            publish()
            success(diary.snapshot(history, 1, blockingAvailable = ::blockingAvailable))
        }
    }

    // ---- Interventions -------------------------------------------------------------------

    /** Synchronous, cache-only decision so the service can react within one frame. */
    fun blockDecision(app: ReelApp, frame: AdapterFrame, foregroundPackage: String): BlockDecision? {
        if (!app.owns(foregroundPackage) || frame.surface != Surface.REELS || frame.route != ReelRoute.VIEWER || !allowed(app)) return null
        if ((allowances[app] ?: 0L) > SystemClock.elapsedRealtime()) return null
        val today = todayCounts[app] ?: 0L
        if (schedules.decision(foregroundPackage, SystemClock.elapsedRealtime(),
                org.attentionstats.protection.BlockScope.SHORT_VIDEOS).isNotEmpty())
            return BlockDecision(app, "schedule", today, 0)
        val limit = diary.blockLimits[app] ?: return null
        return when {
            limit <= 0 -> BlockDecision(app, "block", today, 0)
            today >= limit -> BlockDecision(app, "limit", today, limit)
            else -> null
        }
    }
    fun allowFor(app: ReelApp, minutes: Int) {
        allowances[app] = SystemClock.elapsedRealtime() + minutes * 60_000L
    }
    fun blockingAvailable(app: ReelApp) = allowed(app) && grantEnabled() && serviceConnected
    fun blockChoices(): Set<ReelApp> = diary.blockedApps

    fun recordIntervention(app: ReelApp, kind: String, onId: (String) -> Unit) = enqueue {
        val history = opened()
        val id = UUID.randomUUID().toString()
        history.database.data().insertIntervention(InterventionEvent(id, app.key, kind, System.currentTimeMillis(),
            headline(unlockSession.counts()), reminder?.durationMs ?: 0, todayCounts[app] ?: 0))
        history.database.data().advanceRevision()
        main.post { onId(id) }
    }
    fun decideIntervention(id: String?, choice: String) = enqueue {
        if (id == null) return@enqueue
        val history = opened()
        history.database.data().decideIntervention(id, choice, System.currentTimeMillis())
        history.database.data().advanceRevision()
    }
    fun dismissReminder(onDismissed: () -> Unit = {}) = enqueue {
        diary.dismiss(); publish(); main.post { onDismissed() }
    }
    fun stopDiaryLive() = enqueue { diary.resetLive(); publish() }
    fun pauseDiarySurface() = enqueue { diary.pauseSurface() }

    /** Screen off/on: a new unlock session starts with fresh session counts and reminders. */
    fun resetUnlockSession() {
        invalidate() // Reject observations queued across the screen/lock boundary.
        enqueue {
            store?.let { history -> ReelApp.entries.forEach { closeOpen(history, it) } }
            unlockSession.clear(); diary.resetLive()
            trackers.values.forEach { it.reset() }
            sessionId = UUID.randomUUID().toString(); sessionOrdinal = 0
            publish()
        }
    }

    // ---- Snapshot for the UI --------------------------------------------------------------

    private suspend fun snapshotNow(history: HistoryStore): JSONObject {
        val dao = history.database.data()
        val metadata = checkNotNull(dao.metadata())
        val zone = ZoneId.systemDefault()
        val today = Instant.now().atZone(zone).toLocalDate()
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sources = JSONArray()
        for (app in ReelApp.entries) {
            val config = checkNotNull(configuration[app.sourceId])
            val version = installedVersion(app)
            val counts = JSONObject()
            for (category in PageCategory.entries) counts.put(category.key,
                if (category == PageCategory.POST) JSONObject.NULL else
                    dao.categoryCount(app.key, start, end, category.key).toString())
            sources.put(JSONObject().put("app", app.key).put("enabled", config.enabled)
                .put("consentCurrent", app.sourceId in currentNotices).put("counts", counts)
                .put("paused", config.paused).put("installed", version.isNotEmpty())
                .put("version", version).put("supported", version.isNotEmpty())
                .put("lastRecognizedMs", lastRecognizedWall[app]?.toString() ?: JSONObject.NULL)
                .put("today", dao.dayCount(app.key, start, end).toString())
                .put("visit", unlockSession.total(app)?.toString() ?: JSONObject.NULL))
        }
        return JSONObject().put("contractVersion", 6).put("revision", revision(metadata.generation, metadata.configurationRevision))
            .put("historyRevision", revision(metadata.generation, metadata.lastRevision))
            .put("generation", metadata.generation).put("storage", health)
            .put("noticeRevision", NOTICE_REVISION)
            .put("accessibilityEnabled", grantEnabled()).put("accessibilitySelected", grantSelected())
            .put("serviceConnected", serviceConnected)
            .put("overlayEnabled", overlayEnabled).put("calibrationEnabled", false)
            .put("diagnosticsAvailable", false)
            .put("counterAppearance", JSONObject().put("showIcons", showCounterIcons).put("allAppsToday", allAppsToday))
            .put("storageBytes", listOf("attention-history.db", "attention-history.db-wal", "attention-history.db-shm")
                .sumOf { java.io.File(context.noBackupFilesDir, it).length() }.toString())
            .put("session", JSONObject().put("count", headline(unlockSession.counts()).toString()))
            .put("device", JSONObject().put("model", android.os.Build.MODEL).put("manufacturer", android.os.Build.MANUFACTURER)
                .put("sdk", android.os.Build.VERSION.SDK_INT).put("release", android.os.Build.VERSION.RELEASE)
                .put("appVersion", ownVersion()))
            .put("day", today.toString()).put("zone", zone.id).put("sources", sources)
    }

    @Suppress("DEPRECATION") // API 24-32 require the legacy PackageManager overload.
    fun installedVersion(app: ReelApp): String {
        for (name in app.packages) try {
            return context.packageManager.getPackageInfo(name, 0).versionName?.take(100)?.takeIf { it.isNotBlank() } ?: "installed"
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) { }
        return ""
    }
    @Suppress("DEPRECATION")
    private fun ownVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (_: Exception) { "" }

    // ---- Configuration --------------------------------------------------------------------

    fun configure(appKey: String, enabled: Boolean, expected: String,
        success: (String) -> Unit, failure: (String) -> Unit) {
        val app = ReelApp.byKey(appKey) ?: run { failure("INVALID_ARGUMENT"); return }
        invalidate() // Fence old observations before the user-requested mutation enters the queue.
        enqueue(failure) {
            val history = opened()
            if (!checkRevision(history, expected)) { failure("REVISION_CONFLICT"); return@enqueue }
            val now = System.currentTimeMillis()
            history.database.withWriteTransaction {
                val dao = history.database.data()
                val next = Math.addExact(checkNotNull(dao.metadata()).configurationRevision, 1)
                closeVisits(history, app)
                dao.configure(app.sourceId, enabled, next, now)
                dao.insertConsent(ConsentRecord(UUID.randomUUID().toString(), app.sourceId, NOTICE_REVISION,
                    if (enabled) "allow" else "withdraw", now, next))
                dao.closeCoverage(app.sourceId, now)
                dao.insertCoverage(CoverageInterval(UUID.randomUUID().toString(), app.sourceId, now, null,
                    if (enabled) "partial" else "disabled", if (enabled) "live_only" else "consent_withdrawn", next))
                dao.advanceConfigurationRevision()
            }
            refreshConfiguration(history)
            publish()
            success(snapshotNow(history).toString())
        }
    }

    /** One consent covering every source at once (the onboarding "Turn on" path). */
    fun configureAll(enabled: Boolean, expected: String, success: (String) -> Unit, failure: (String) -> Unit) =
        mutate(expected, success, failure) { history ->
            val dao = history.database.data()
            val now = System.currentTimeMillis()
            val next = Math.addExact(checkNotNull(dao.metadata()).configurationRevision, 1)
            for (app in ReelApp.entries) {
                dao.configure(app.sourceId, enabled, next, now)
                dao.insertConsent(ConsentRecord(UUID.randomUUID().toString(), app.sourceId, NOTICE_REVISION,
                    if (enabled) "allow" else "withdraw", now, next))
                dao.closeCoverage(app.sourceId, now)
                dao.insertCoverage(CoverageInterval(UUID.randomUUID().toString(), app.sourceId, now, null,
                    if (enabled) "partial" else "disabled", if (enabled) "live_only" else "consent_withdrawn", next))
            }
            if (enabled) dao.setOverlay(true)
        }

    fun setPaused(paused: Boolean, expected: String, success: (String) -> Unit, failure: (String) -> Unit) =
        mutate(expected, success, failure) { history ->
            val dao = history.database.data()
            val next = Math.addExact(checkNotNull(dao.metadata()).configurationRevision, 1)
            val now = System.currentTimeMillis()
            for (config in dao.configurations().filter { it.enabled }) {
                dao.closeCoverage(config.sourceId, now)
                dao.insertCoverage(CoverageInterval(UUID.randomUUID().toString(), config.sourceId, now, null,
                    if (paused) "paused" else "partial", if (paused) "user_paused" else "live_only", next))
            }
            dao.pauseEnabled(paused)
        }

    fun setOverlay(enabled: Boolean, expected: String, success: (String) -> Unit, failure: (String) -> Unit) =
        mutate(expected, success, failure, resetObservation = false) { history -> history.database.data().setOverlay(enabled) }

    fun setCounterAppearance(showIcons: Boolean, allApps: Boolean, expected: String,
        success: (String) -> Unit, failure: (String) -> Unit) =
        mutate(expected, success, failure, resetObservation = false) { history ->
            history.database.data().setCounterAppearance(showIcons, allApps)
        }

    fun deleteHistory(expected: String, success: (String) -> Unit, failure: (String) -> Unit) =
        mutate(expected, success, failure) { history ->
            val dao = history.database.data()
            for (app in ReelApp.entries) dao.configure(app.sourceId, false,
                Math.addExact(checkNotNull(dao.metadata()).configurationRevision, 1), System.currentTimeMillis())
            dao.setOverlay(false)
            dao.deleteEpisodes() // FK cascade deletes exposures in the same transaction.
            dao.deleteCoverage()
            dao.deleteConsents()
            dao.deleteUsage()
            dao.deleteTimers()
            dao.deleteInterventions()
            dao.deleteProtection()
            dao.deleteOrigins()
            main.post { com.attentionstats.autoscroll.AutoScrollSession.clear() }
            com.attentionstats.backup.SystemBackupState.configure(context, false)
            com.attentionstats.backup.SystemBackupState.clearPending(context)
            archives.clear()
            schedules.reset()
            dao.disableBlockers()
            unlockSession.clear()
            todayCounts.clear()
            val preferences = checkNotNull(dao.diary())
            dao.putDiary(preferences.copy(usageEnabled = false, reminderEnabled = false,
                measurePlayback = false,
                usageEpoch = "", effectiveAtMs = 0, queryThroughMs = 0,
                revision = preferences.revision + 1))
            diary.resetLive()
            diary.loadChoices(history)
            trackers.values.forEach { it.reset() }
        }

    private fun mutate(expected: String, success: (String) -> Unit, failure: (String) -> Unit,
        resetObservation: Boolean = true, block: suspend (HistoryStore) -> Unit) {
        if (resetObservation) invalidate()
        enqueue(failure) {
            val history = opened()
            if (!checkRevision(history, expected)) { failure("REVISION_CONFLICT"); return@enqueue }
            history.database.withWriteTransaction {
                if (resetObservation) closeVisits(history)
                block(history)
                history.database.data().advanceConfigurationRevision()
            }
            refreshConfiguration(history)
            if (!resetObservation) liveOverlay?.let { refreshOverlay(history, it.app) }
            publish()
            success(snapshotNow(history).toString())
        }
    }

    private suspend fun closeVisits(history: HistoryStore, only: ReelApp? = null) {
        diary.resetLive()
        if (only == null) unlockSession.clear() else unlockSession.clear(only)
        for (app in ReelApp.entries.filter { only == null || it == only }) {
            closeOpen(history, app)
            trackers[app]?.leave()
        }
    }

    /** Close the open episode and the focused item's dwell. */
    private suspend fun closeOpen(history: HistoryStore, app: ReelApp, at: Long = System.currentTimeMillis()) {
        playback.remove(app)
        val dao = history.database.data()
        openExposures.remove(app)?.let { lastPlayback.remove(it.id); dao.closeDwell(it.id, (at - it.atMs).coerceIn(0, MAX_DWELL_MS)) }
        visits.remove(app)?.let { dao.closeEpisode(it, at) }
        routes.remove(app)
    }

    // ---- Observation ----------------------------------------------------------------------

    /** One adapter reading from the accessibility service. Serialized; returns immediately. */
    fun observe(app: ReelApp, frame: AdapterFrame, item: ItemFrame, windowId: Int, capturedEpoch: Long) = enqueue {
        if (capturedEpoch != epoch.get() || !allowed(app) || !grantEnabled()) { observationStatus = "fenced_or_not_granted"; return@enqueue }
        val history = opened()
        val dao = history.database.data()
        val wall = System.currentTimeMillis()
        if (item.surface == Surface.REELS) { lastRecognized[app] = item.atMs; lastRecognizedWall[app] = wall }
        val tracker = trackers.getOrPut(app) { EncounterTracker() }
        val encounter = tracker.observe(item)
        observationStatus = if (encounter == null) "no_new_encounter:${item.surface}" else "encounter:${encounter.category}:revisit=${encounter.revisit}"
        if (item.surface == Surface.OUTSIDE || item.surface == Surface.UNKNOWN) {
            history.database.withWriteTransaction { closeOpen(history, app, wall); dao.advanceRevision() }
        }
        if (encounter != null) {
            history.database.withWriteTransaction {
                if (capturedEpoch != epoch.get() || !allowed(app) || !grantEnabled()) throw ObservationInvalidated()
                openExposures.remove(app)?.let { lastPlayback.remove(it.id); dao.closeDwell(it.id, (wall - it.atMs).coerceIn(0, MAX_DWELL_MS)) }
                if (routes[app] != encounter.route) {
                    visits.remove(app)?.let { dao.closeEpisode(it, wall) }
                    routes[app] = encounter.route
                }
                val episode = visits.getOrPut(app) {
                    UUID.randomUUID().toString().also {
                        dao.insertEpisode(SurfaceEpisode(it, app.key, encounter.route.key, wall, null, DETECTOR_REVISION, "estimate"))
                    }
                }
                val config = checkNotNull(configuration[app.sourceId])
                val zone = ZoneId.systemDefault()
                val exposureId = UUID.randomUUID().toString()
                if (!encounter.revisit) sessionOrdinal++
                dao.insertExposure(Exposure(exposureId, episode, app.key, wall, item.atMs,
                    Math.addExact(dao.visitCount(episode), 1), DETECTOR_REVISION, "estimate", config.consentRevision,
                    if (encounter.route == ReelRoute.HOME) "settled_feed_video" else "settled_viewer_item",
                    zone.id, Instant.ofEpochMilli(wall).atZone(zone).offset.totalSeconds, encounter.category.key,
                    surface = if (encounter.route == ReelRoute.HOME) "feed" else "viewer",
                    revisit = encounter.revisit, appVersion = installedVersion(app), method = frame.method,
                    itemKey = itemKey(encounter.identity), unlockSessionId = sessionId,
                    sessionOrdinal = if (encounter.revisit) 0 else sessionOrdinal, mediaKind = frame.mediaKind))
                dao.advanceRevision()
                openExposures[app] = OpenExposure(exposureId, wall)
                playback[app] = org.attentionstats.reels.PlaybackEvidence()
            }
            if (!encounter.revisit) unlockSession.record(app, encounter.category)
            refreshTodayCounts(history)
            if (SystemClock.elapsedRealtime() - widgetAt > 20_000) refreshWidget(force = false)
        }
        val counted = encounter != null && !encounter.revisit && encounter.category != PageCategory.PHOTO
        val exposure = openExposures[app]
        if (diary.measurePlayback && exposure != null && capturedEpoch == epoch.get()) {
            val eligible = frame.surface == Surface.REELS && frame.mediaKind == "video" && tracker.focused == frame.identity
            val evidence = playback[app]?.sample(item.atMs, if (eligible) frame.positionMs else null, if (eligible) frame.durationMs else null)
            if (evidence != null && lastPlayback[exposure.id] != evidence) {
                history.database.withWriteTransaction {
                    if (capturedEpoch != epoch.get() || !diary.measurePlayback || !allowed(app)) throw ObservationInvalidated()
                    dao.updatePlayback(exposure.id, evidence.confirmedMs, evidence.qualified, evidence.completed)
                    dao.advanceRevision()
                    if (capturedEpoch != epoch.get()) throw ObservationInvalidated()
                }
                lastPlayback[exposure.id] = evidence
            }
        }
        val signal = CounterSignal(item.atMs, item.surface, null, windowId, item.category, item.route)
        if (capturedEpoch == epoch.get() && allowed(app) && grantEnabled())
            diary.observe(history, app, signal, wall, counted) { capturedEpoch == epoch.get() && allowed(app) && grantEnabled() }
        if ((item.surface == Surface.REELS || (item.route == ReelRoute.HOME && frame.method.endsWith(".feed"))) && capturedEpoch == epoch.get())
            refreshOverlay(history, app, encounter?.revisit == true, capturedEpoch, item.route)
        else if (item.surface == Surface.OUTSIDE || item.surface == Surface.UNKNOWN) liveOverlay = null
        publish()
    }

    private fun itemKey(identity: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(itemSecret)
        digest.update(java.nio.ByteBuffer.allocate(8).putLong(identity).array())
        return digest.digest().take(8).joinToString("") { "%02x".format(it) }
    }

    private suspend fun refreshTodayCounts(history: HistoryStore) {
        val dao = history.database.data()
        val zone = ZoneId.systemDefault()
        val day = Instant.now().atZone(zone).toLocalDate()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        for (app in ReelApp.entries) {
            val categories = dao.dayCategories(app.key, start, end).associate { it.category to it.count }
            todayCounts[app] = headline(categories)
        }
    }

    @Volatile private var widgetAt = 0L
    /** Home-screen widget refresh; throttled so scrolling never floods the launcher. */
    fun refreshWidget(force: Boolean = true) = enqueue {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - widgetAt < 20_000) return@enqueue
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        if (manager.getAppWidgetIds(android.content.ComponentName(context, com.attentionstats.widget.TodayWidget::class.java)).isEmpty())
            return@enqueue // No widget placed: skip the work entirely.
        widgetAt = now
        val history = opened()
        refreshTodayCounts(history)
        // A widget problem must never take counting down with it.
        try { renderWidget(history) } catch (failure: Exception) { android.util.Log.w("AttentionStats", "widget refresh failed", failure) }
    }

    private suspend fun renderWidget(history: HistoryStore) {
        val day = JSONObject(diary.snapshot(history, 1, blockingAvailable = ::blockingAvailable))
            .getJSONArray("days").getJSONObject(0)
        val apps = day.getJSONArray("apps")
        var watching = 0L; var screen = 0L; var top: Pair<String, Long>? = null
        for (index in 0 until apps.length()) {
            val app = apps.getJSONObject(index)
            watching += app.optString("reelsMs").toLongOrNull() ?: 0
            val ms = app.optString("appMs").toLongOrNull() ?: 0
            screen += ms
            if (ms > (top?.second ?: 0) && app.optString("packageName") != context.packageName) top = app.getString("name") to ms
        }
        val interventions = day.getJSONObject("interventions")
        val breaks = interventions.keys().asSequence().filter { it.startsWith("break.") }.sumOf { interventions.getLong(it) }
        val videoApps = ReelApp.entries.map { it.displayName to (todayCounts[it] ?: 0L) }.sortedByDescending { it.second }
        com.attentionstats.widget.TodayWidget.render(context, com.attentionstats.widget.WidgetData(
            todayCounts.values.sum(), headline(unlockSession.counts()), watching, screen, breaks, videoApps, top))
    }

    private fun headline(values: Map<String, Long>): Long = values.filterKeys { key ->
        val category = PageCategory.byKey(key)
        category.video || (category == PageCategory.AD && diary.includeRecognizedAds)
    }.values.sum()

    private suspend fun refreshOverlay(history: HistoryStore, app: ReelApp, scrollBack: Boolean = false,
        capturedEpoch: Long = epoch.get(), route: ReelRoute? = null) {
        val dao = history.database.data()
        val zone = ZoneId.systemDefault()
        val day = Instant.now().atZone(zone).toLocalDate()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayRows = if (allAppsToday) dao.allAppDayCategories(start, end) else dao.dayCategories(app.key, start, end)
        val todayCategories = dayRows.associate { it.category to it.count }
        val visitCategories = unlockSession.counts()
        if (capturedEpoch != epoch.get() || !allowed(app)) { liveOverlay = null; return }
        liveOverlay = OverlaySnapshot(app, headline(visitCategories), headline(todayCategories), capturedEpoch,
            PageCategory.entries.filter { it != PageCategory.POST }.map {
                OverlayCategory(it, visitCategories[it.key] ?: 0, todayCategories[it.key] ?: 0)
            }, scrollBack, showCounterIcons, allAppsToday, route ?: routes[app] ?: ReelRoute.VIEWER, diary.includeRecognizedAds,
            reminder?.durationMs ?: 0, diary.breakProgress)
    }

    companion object {
        /** Notice 6: version-independent detection that reads on-screen labels in memory. */
        const val NOTICE_REVISION = 6
        const val DETECTOR_REVISION = 14
        private const val MAX_DWELL_MS = 30 * 60_000L
        @Volatile private var instance: ReelsCoordinator? = null
        fun get(context: Context): ReelsCoordinator = instance ?: synchronized(this) {
            instance ?: ReelsCoordinator(context.applicationContext).also { instance = it }
        }
    }
}
