package com.attentionstats.reels

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AtomicFile
import android.view.accessibility.AccessibilityManager
import androidx.room3.withWriteTransaction
import com.attentionstats.storage.ConsentRecord
import com.attentionstats.storage.CoverageInterval
import com.attentionstats.storage.Exposure
import com.attentionstats.storage.HistoryStore
import com.attentionstats.storage.SourceConfiguration
import com.attentionstats.storage.SurfaceEpisode
import com.attentionstats.BuildConfig
import com.attentionstats.detectors.DetectorRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.attentionstats.reels.CounterReducer
import org.attentionstats.reels.CounterSignal
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface
import org.attentionstats.reels.PageCategory
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong
import java.io.File

internal class ReelsCoordinator private constructor(private val context: Context) {
    private class ObservationInvalidated : RuntimeException()
    private val jobs = Channel<suspend () -> Unit>(64)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private var store: HistoryStore? = null
    private val epoch = AtomicLong()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val reducers = mutableMapOf<ReelApp, CounterReducer>()
    private val visits = mutableMapOf<ReelApp, String>()
    private val structures = JSONObject()
    private val calibrationExpiry = Runnable { setCalibration(false) }
    private var lastStructureBytes: ByteArray? = null
    @Volatile private var configuration: Map<String, SourceConfiguration> = emptyMap()
    @Volatile private var currentNotices: Set<String> = emptySet()
    @Volatile var overlayEnabled = false
        private set
    @Volatile private var showCounterIcons = true
    @Volatile private var allAppsToday = true
    @Volatile var serviceConnected = false
    @Volatile var health = "starting"
        private set
    @Volatile var liveOverlay: OverlaySnapshot? = null
        private set
    @Volatile var calibrationEnabled = false
        private set

    data class OverlayCategory(val category: PageCategory, val visit: Long, val today: Long)
    data class OverlaySnapshot(val app: ReelApp, val visit: Long, val today: Long, val epoch: Long,
        val categories: List<OverlayCategory> = emptyList(), val scrollBack: Boolean = false,
        val showIcons: Boolean = true, val allAppsToday: Boolean = true)

    init {
        scope.launch { for (job in jobs) job() }
        clearStructure() // A previous process's temporary permission never carries into this process.
    }
    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    fun epoch(): Long = epoch.get()
    fun allowed(app: ReelApp): Boolean = health == "ready" && app.sourceId in currentNotices &&
        configuration[app.sourceId]?.let { it.enabled && !it.paused } == true
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
        liveOverlay = null
        publish()
    }

    private fun enqueue(onError: (String) -> Unit = {}, operation: suspend () -> Unit) {
        if (!jobs.trySend {
            try { operation() }
            catch (_: ObservationInvalidated) { /* A privacy/source fence rolls back this observation. */ }
            catch (_: Exception) {
                health = "storage_unavailable"
                try { store?.close() } catch (_: Exception) { }
                store = null
                visits.clear()
                reducers.clear()
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
            if (health == "busy") {
                refreshConfiguration(current)
                health = "ready"
            }
            return current
        }
        return HistoryStore.open(context).also {
            store = it
            refreshConfiguration(it)
            for (app in ReelApp.entries) reducers[app] = CounterReducer(it.database.data().hasExposure(app.key))
            health = "ready"
            publish()
        }
    }

    private suspend fun refreshConfiguration(history: HistoryStore) {
        configuration = history.database.data().configurations().associateBy { it.sourceId }
        currentNotices = ReelApp.entries.filter {
            (history.database.data().allowedNotice(it.sourceId) ?: 0) >= requiredNotice(it)
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
        val history = opened()
        success(snapshotNow(history).toString())
    }

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
            val supported = DetectorRegistry.detector(app, version) != null
            val visit = visits[app]
            val counts = JSONObject()
            for (category in PageCategory.entries) counts.put(category.key,
                if (category == PageCategory.POST) JSONObject.NULL else
                    dao.categoryCount(app.key, start, end, category.key).toString())
            sources.put(JSONObject().put("app", app.key).put("enabled", config.enabled)
                .put("consentCurrent", app.sourceId in currentNotices).put("counts", counts)
                .put("paused", config.paused).put("installed", version.isNotEmpty())
                .put("version", version).put("supported", supported)
                .put("today", dao.dayCount(app.key, start, end).toString())
                .put("visit", visit?.let { dao.visitCount(it).toString() } ?: JSONObject.NULL))
        }
        return JSONObject().put("contractVersion", 5).put("revision", revision(metadata.generation, metadata.configurationRevision))
            .put("historyRevision", revision(metadata.generation, metadata.lastRevision))
            .put("generation", metadata.generation).put("storage", health)
            .put("accessibilityEnabled", grantEnabled()).put("accessibilitySelected", grantSelected())
            .put("serviceConnected", serviceConnected)
            .put("overlayEnabled", overlayEnabled).put("calibrationEnabled", calibrationEnabled)
            .put("diagnosticsAvailable", BuildConfig.DEBUG)
            .put("counterAppearance", JSONObject().put("showIcons", showCounterIcons).put("allAppsToday", allAppsToday))
            .put("day", today.toString()).put("zone", zone.id).put("sources", sources)
    }

    @Suppress("DEPRECATION") // API 24-32 require the legacy PackageManager overload.
    fun installedVersion(app: ReelApp): String = try {
        context.packageManager.getPackageInfo(app.packageName, 0).versionName?.take(100) ?: ""
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) { "" }

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
                dao.insertConsent(ConsentRecord(UUID.randomUUID().toString(), app.sourceId, requiredNotice(app),
                    if (enabled) "allow" else "withdraw", now, next))
                dao.closeCoverage(app.sourceId, now)
                dao.insertCoverage(CoverageInterval(UUID.randomUUID().toString(), app.sourceId, now, null,
                    if (enabled) "partial" else "disabled", if (enabled) "live_only" else "consent_withdrawn", next))
                dao.advanceConfigurationRevision()
            }
            refreshConfiguration(history)
            reducers[app] = CounterReducer(history.database.data().hasExposure(app.key))
            publish()
            success(snapshotNow(history).toString())
        }
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
            for (app in ReelApp.entries) reducers[app] = CounterReducer(false)
            calibrationEnabled = false
            clearStructure()
        }

    private fun mutate(expected: String, success: (String) -> Unit, failure: (String) -> Unit,
        resetObservation: Boolean = true,
        block: suspend (HistoryStore) -> Unit) {
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
            if (!resetObservation) liveOverlay?.let { refreshOverlay(history, it.app, it.scrollBack) }
            publish()
            success(snapshotNow(history).toString())
        }
    }

    private suspend fun closeVisits(history: HistoryStore, only: ReelApp? = null) {
        for (app in ReelApp.entries.filter { only == null || it == only }) {
            visits.remove(app)?.let { history.database.data().closeEpisode(it, System.currentTimeMillis()) }
            reducers[app]?.reset()
        }
    }

    fun setCalibration(enabled: Boolean) {
        check(!enabled || (BuildConfig.DEBUG && grantEnabled() && ReelApp.entries.any { allowed(it) })) { "CONSENT_REQUIRED" }
        if (enabled == calibrationEnabled) {
            if (!enabled) clearStructure()
            return
        }
        calibrationEnabled = enabled
        main.removeCallbacks(calibrationExpiry)
        if (enabled) main.postDelayed(calibrationExpiry, 600_000)
        if (!enabled) clearStructure()
        // A fresh matching scope must not be compared with an older reducer page ordinal.
        // Queue this before publishing the new epoch to acquisition.
        enqueue { reducers.values.forEach { it.reset() } }
        invalidate()
        publish()
    }

    fun recordStructure(app: ReelApp, shape: JSONObject, capturedEpoch: Long) = enqueue {
        if (!BuildConfig.DEBUG || !calibrationEnabled || !allowed(app) || capturedEpoch != epoch.get()) return@enqueue
        structures.put(app.key, shape)
        val bytes = JSONObject().put("scope", "consented structural detector qualification only; no content")
            .put("targets", structures).toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > 32_768 || lastStructureBytes?.contentEquals(bytes) == true) return@enqueue
        val output = AtomicFile(File(context.noBackupFilesDir, "reels-structure.json"))
        val stream = output.startWrite()
        try { stream.write(bytes); output.finishWrite(stream); lastStructureBytes = bytes }
        catch (failure: Exception) { output.failWrite(stream); throw failure }
    }

    fun clearStructure() = enqueue {
        ReelApp.entries.forEach { structures.remove(it.key) }
        lastStructureBytes = null
        AtomicFile(File(context.noBackupFilesDir, "reels-structure.json")).delete()
    }

    fun observe(app: ReelApp, signal: CounterSignal, capturedEpoch: Long,
        observedAtMs: Long = System.currentTimeMillis(), observedZoneId: String = ZoneId.systemDefault().id) = enqueue {
        if (capturedEpoch != epoch.get() || !allowed(app) || !grantEnabled()) return@enqueue
        val history = opened()
        val reducer = checkNotNull(reducers[app])
        val proposal = reducer.observe(signal)
        if (signal.surface == Surface.OUTSIDE || signal.surface == Surface.UNKNOWN) {
            visits.remove(app)?.let { id -> history.database.withWriteTransaction {
                history.database.data().closeEpisode(id, System.currentTimeMillis())
                history.database.data().advanceRevision()
            } }
        }
        if (proposal != null) {
            val config = checkNotNull(configuration[app.sourceId])
            if (capturedEpoch != epoch.get() || !allowed(app)) return@enqueue
            val id = visits[app] ?: UUID.randomUUID().toString()
            val now = observedAtMs
            history.database.withWriteTransaction {
                if (capturedEpoch != epoch.get() || !allowed(app) || !grantEnabled()) throw ObservationInvalidated()
                val dao = history.database.data()
                val detectorRevision = checkNotNull(DetectorRegistry.detector(app, installedVersion(app))).definition.revision
                if (visits[app] == null) dao.insertEpisode(SurfaceEpisode(id, app.key, "reels_viewer", now,
                    null, detectorRevision, "estimate"))
                dao.insertExposure(Exposure(UUID.randomUUID().toString(), id, app.key, now,
                    proposal.elapsedMs, Math.addExact(dao.visitCount(id), 1), detectorRevision, "estimate",
                    config.consentRevision, "settled_page_transition", observedZoneId,
                    Instant.ofEpochMilli(now).atZone(ZoneId.of(observedZoneId)).offset.totalSeconds, proposal.category.key))
                dao.advanceRevision()
                if (capturedEpoch != epoch.get() || !allowed(app) || !grantEnabled()) throw ObservationInvalidated()
            }
            visits[app] = id
            reducer.acknowledge(proposal)
        }
        // Keep committed totals in place during a known viewer's unsettled page transition.
        if (signal.surface == Surface.REELS && capturedEpoch == epoch.get()) {
            refreshOverlay(history, app, proposal?.scrollBack ?: liveOverlay?.takeIf { it.app == app }?.scrollBack ?: false, capturedEpoch)
        } else liveOverlay = null
        publish()
    }

    private suspend fun refreshOverlay(history: HistoryStore, app: ReelApp, scrollBack: Boolean,
        capturedEpoch: Long = epoch.get()) {
        val dao = history.database.data()
        val zone = ZoneId.systemDefault()
        val day = Instant.now().atZone(zone).toLocalDate()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayRows = if (allAppsToday) dao.allAppDayCategories(start, end) else dao.dayCategories(app.key, start, end)
        val todayCategories = dayRows.associate { it.category to it.count }
        val visitCategories = visits[app]?.let { dao.visitCategories(it).associate { row -> row.category to row.count } }
            ?: emptyMap()
        if (capturedEpoch != epoch.get() || !allowed(app)) { liveOverlay = null; return }
        liveOverlay = OverlaySnapshot(app, visitCategories.values.sum(), todayCategories.values.sum(), capturedEpoch,
            PageCategory.entries.filter { it != PageCategory.POST }.map {
                OverlayCategory(it, visitCategories[it.key] ?: 0, todayCategories[it.key] ?: 0)
            }, scrollBack, showCounterIcons, allAppsToday)
    }

    companion object {
        const val NOTICE_REVISION = 3
        fun requiredNotice(app: ReelApp): Int = if (app == ReelApp.TIKTOK) 4 else NOTICE_REVISION
        @Volatile private var instance: ReelsCoordinator? = null
        fun get(context: Context): ReelsCoordinator = instance ?: synchronized(this) {
            instance ?: ReelsCoordinator(context.applicationContext).also { instance = it }
        }
    }
}
