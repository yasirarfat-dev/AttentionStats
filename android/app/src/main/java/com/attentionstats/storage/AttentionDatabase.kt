package com.attentionstats.storage

import androidx.room3.Dao
import androidx.room3.AutoMigration
import androidx.room3.ColumnInfo
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.Upsert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase

@Entity
data class StoreMetadata(
    @PrimaryKey val id: Int = 1,
    val generation: String,
    val installationEpoch: String,
    val lastRevision: Long,
    val createdAtMs: Long,
    val configurationRevision: Long = 0,
)

@Entity
data class SourceConfiguration(
    @PrimaryKey val sourceId: String,
    val enabled: Boolean,
    val consentRevision: Long,
    val effectiveAtMs: Long,
    val paused: Boolean = false,
)

@Entity
data class LiveOverlayConfiguration(
    @PrimaryKey val id: Int = 1, val enabled: Boolean = false,
    @ColumnInfo(defaultValue = "1") val showIcons: Boolean = true,
    @ColumnInfo(defaultValue = "1") val allAppsToday: Boolean = true,
)

@Entity(indices = [Index("sourceId", "atMs")])
data class ConsentRecord(
    @PrimaryKey val id: String,
    val sourceId: String,
    val noticeRevision: Int,
    val decision: String,
    val atMs: Long,
    val configRevision: Long,
)

@Entity(indices = [Index("appId", "startMs")])
data class SurfaceEpisode(
    @PrimaryKey val id: String,
    val appId: String,
    val surfaceId: String,
    val startMs: Long,
    val endMs: Long?,
    val detectorRevision: Int,
    val quality: String,
)

@Entity(
    foreignKeys = [ForeignKey(entity = SurfaceEpisode::class, parentColumns = ["id"],
        childColumns = ["surfaceEpisodeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("appId", "occurredAtMs"), Index("surfaceEpisodeId", "ordinal", unique = true)],
)
data class Exposure(
    @PrimaryKey val id: String,
    val surfaceEpisodeId: String,
    val appId: String,
    val occurredAtMs: Long,
    val observedElapsedMs: Long,
    val ordinal: Long,
    val detectorRevision: Int,
    val countKind: String,
    val consentRevision: Long,
    val reasonCode: String,
    val observedZoneId: String,
    val observedOffsetSeconds: Int,
    @ColumnInfo(defaultValue = "'unclassified'") val category: String = "unclassified",
    /** "viewer" (full-screen short-video player) or "feed" (inline video in a scrolling feed). */
    @ColumnInfo(defaultValue = "'viewer'") val surface: String = "viewer",
    /** How long this item stayed in focus; -1 until the next item or departure closes it. */
    @ColumnInfo(defaultValue = "-1") val dwellMs: Long = -1,
    /** Returned to an item already seen this unlock session; excluded from encounter totals. */
    @ColumnInfo(defaultValue = "0") val revisit: Boolean = false,
    @ColumnInfo(defaultValue = "''") val appVersion: String = "",
    /** Detector path that produced the encounter, e.g. "instagram.viewer" or "facebook.feed". */
    @ColumnInfo(defaultValue = "''") val method: String = "",
    /** Device-local salted key of the item, for rewatch analysis; never derived across devices. */
    @ColumnInfo(defaultValue = "''") val itemKey: String = "",
    @ColumnInfo(defaultValue = "''") val unlockSessionId: String = "",
    /** 1-based position of this new encounter within its unlock session (0 for revisits). */
    @ColumnInfo(defaultValue = "0") val sessionOrdinal: Long = 0,
)

@Entity(indices = [Index("sourceId", "startMs")])
data class CoverageInterval(
    @PrimaryKey val id: String,
    val sourceId: String,
    val startMs: Long,
    val endMs: Long?,
    val state: String,
    val reasonCode: String,
    val configRevision: Long,
)

@Entity
data class DiaryConfiguration(
    @PrimaryKey val id: Int = 1,
    val usageEnabled: Boolean = false,
    val usageEpoch: String = "",
    val effectiveAtMs: Long = 0,
    val queryThroughMs: Long = 0,
    val grantLost: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderCount: Int = 15,
    val reminderMinutes: Int = 15,
    val onboardingSeen: Boolean = false,
    @ColumnInfo(defaultValue = "1") val includeRecognizedAds: Boolean = true,
    val revision: Long = 0,
    /** Seconds a mindful-break screen waits before its choices unlock. */
    @ColumnInfo(defaultValue = "10") val breakSeconds: Int = 10,
)

/** Only package, timestamp and admitted event kind; no activity names or content. */
@Entity(indices = [Index("atMs")])
data class AppUsageEvent(@PrimaryKey val id: String, val epoch: String,
    val packageName: String, val atMs: Long, val kind: Int)

@Entity(indices = [Index("startMs")])
data class ReelsTimer(@PrimaryKey val id: String, val appId: String, val route: String,
    val startMs: Long, val endMs: Long, val durationMs: Long)

@Entity
data class ReelsBlockConfiguration(
    @PrimaryKey val appId: String,
    val enabled: Boolean = false,
    /** 0 blocks the short-video viewer outright; N > 0 blocks once today's encounters reach N. */
    @ColumnInfo(defaultValue = "0") val dailyLimit: Int = 0,
)

/** Every break reminder or block shown, and what the person chose. Powers "how often did I take a break". */
@Entity(indices = [Index("atMs")])
data class InterventionEvent(
    @PrimaryKey val id: String,
    val appId: String,
    /** "break" (mindful pause), "block" (always blocked) or "limit" (daily limit reached). */
    val kind: String,
    val atMs: Long,
    val sessionCount: Long,
    val sessionMs: Long,
    val todayCount: Long,
    /** "left", "continued", "allowed" or "" while undecided. */
    val choice: String = "",
    val decidedAtMs: Long = 0,
)

@Dao
interface AttentionDao {
    @Query("SELECT * FROM DiaryConfiguration WHERE id = 1")
    suspend fun diary(): DiaryConfiguration?
    @Upsert suspend fun putDiary(value: DiaryConfiguration)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertUsage(value: List<AppUsageEvent>)
    @Query("SELECT * FROM AppUsageEvent WHERE atMs >= :start AND atMs < :end ORDER BY atMs, id LIMIT 100001")
    suspend fun usageEvents(start: Long, end: Long): List<AppUsageEvent>
    @Upsert suspend fun putTimer(value: ReelsTimer)
    @Query("SELECT * FROM ReelsTimer WHERE startMs < :end AND endMs > :start ORDER BY startMs LIMIT 100001")
    suspend fun timers(start: Long, end: Long): List<ReelsTimer>
    @Query("SELECT appId, COUNT(*) AS count FROM Exposure WHERE occurredAtMs >= :start AND occurredAtMs < :end AND revisit = 0 GROUP BY appId")
    suspend fun reelsCounts(start: Long, end: Long): List<AppCount>
    @Query("DELETE FROM AppUsageEvent") suspend fun deleteUsage()
    @Query("DELETE FROM ReelsTimer") suspend fun deleteTimers()
    @Query("SELECT * FROM ReelsBlockConfiguration") suspend fun blockers(): List<ReelsBlockConfiguration>
    @Insert suspend fun insertIntervention(value: InterventionEvent)
    @Query("SELECT appId, COUNT(*) AS count FROM Exposure WHERE occurredAtMs >= :start AND occurredAtMs < :end AND revisit = 0 AND category = :category GROUP BY appId")
    suspend fun categoryTotalsByApp(start: Long, end: Long, category: String): List<AppCount>
    @Query("UPDATE InterventionEvent SET choice = :choice, decidedAtMs = :at WHERE id = :id AND choice = ''")
    suspend fun decideIntervention(id: String, choice: String, at: Long)
    @Query("SELECT kind, choice, COUNT(*) AS count FROM InterventionEvent WHERE atMs >= :start AND atMs < :end GROUP BY kind, choice")
    suspend fun interventionTotals(start: Long, end: Long): List<InterventionTotal>
    @Query("DELETE FROM InterventionEvent") suspend fun deleteInterventions()
    @Query("UPDATE Exposure SET dwellMs = :dwell WHERE id = :id AND dwellMs < 0")
    suspend fun closeDwell(id: String, dwell: Long)
    @Query("SELECT appId, SUM(dwellMs) AS count FROM Exposure WHERE occurredAtMs >= :start AND occurredAtMs < :end AND dwellMs > 0 GROUP BY appId")
    suspend fun dwellTotals(start: Long, end: Long): List<AppCount>
    @Query("SELECT appId, COUNT(*) AS count FROM Exposure WHERE occurredAtMs >= :start AND occurredAtMs < :end AND revisit = 1 GROUP BY appId")
    suspend fun revisitCounts(start: Long, end: Long): List<AppCount>
    @Query("SELECT CAST(strftime('%H', occurredAtMs / 1000, 'unixepoch', 'localtime') AS INTEGER) AS category, COUNT(*) AS count FROM Exposure WHERE occurredAtMs >= :start AND occurredAtMs < :end AND revisit = 0 GROUP BY category")
    suspend fun hourly(start: Long, end: Long): List<HourTotal>
    @Upsert suspend fun putBlocker(value: ReelsBlockConfiguration)
    @Query("UPDATE ReelsBlockConfiguration SET enabled = 0") suspend fun disableBlockers()
    @Query("SELECT * FROM StoreMetadata WHERE id = 1")
    suspend fun metadata(): StoreMetadata?
    @Insert suspend fun insertMetadata(value: StoreMetadata)
    @Query("UPDATE StoreMetadata SET lastRevision = lastRevision + 1 WHERE id = 1")
    suspend fun advanceRevision()
    @Query("UPDATE StoreMetadata SET lastRevision = lastRevision + 1, configurationRevision = configurationRevision + 1 WHERE id = 1")
    suspend fun advanceConfigurationRevision()
    @Query("SELECT * FROM SourceConfiguration ORDER BY sourceId")
    suspend fun configurations(): List<SourceConfiguration>
    @Insert suspend fun insertConfiguration(value: SourceConfiguration)
    @Query("UPDATE SourceConfiguration SET enabled = :enabled, paused = 0, consentRevision = :revision, effectiveAtMs = :at WHERE sourceId = :sourceId")
    suspend fun configure(sourceId: String, enabled: Boolean, revision: Long, at: Long)
    @Query("UPDATE SourceConfiguration SET paused = :paused WHERE enabled = 1")
    suspend fun pauseEnabled(paused: Boolean)
    @Query("SELECT * FROM LiveOverlayConfiguration WHERE id = 1")
    suspend fun overlay(): LiveOverlayConfiguration?
    @Insert suspend fun insertOverlay(value: LiveOverlayConfiguration)
    @Query("UPDATE LiveOverlayConfiguration SET enabled = :enabled WHERE id = 1")
    suspend fun setOverlay(enabled: Boolean)
    @Query("UPDATE LiveOverlayConfiguration SET showIcons = :showIcons, allAppsToday = :allAppsToday WHERE id = 1")
    suspend fun setCounterAppearance(showIcons: Boolean, allAppsToday: Boolean)
    @Insert suspend fun insertConsent(value: ConsentRecord)
    @Insert suspend fun insertEpisode(value: SurfaceEpisode)
    @Query("UPDATE SurfaceEpisode SET endMs = :at WHERE id = :id AND endMs IS NULL")
    suspend fun closeEpisode(id: String, at: Long)
    @Query("UPDATE SurfaceEpisode SET endMs = startMs, quality = 'interrupted' WHERE endMs IS NULL")
    suspend fun closeInterruptedEpisodes(): Int
    @Insert suspend fun insertExposure(value: Exposure)
    @Query("SELECT COUNT(*) FROM Exposure WHERE appId = :appId AND occurredAtMs >= :start AND occurredAtMs < :end AND revisit = 0")
    suspend fun dayCount(appId: String, start: Long, end: Long): Long
    @Query("SELECT COUNT(*) FROM Exposure WHERE appId = :appId AND occurredAtMs >= :start AND occurredAtMs < :end AND category = :category AND revisit = 0")
    suspend fun categoryCount(appId: String, start: Long, end: Long, category: String): Long
    @Query("SELECT category, COUNT(*) AS count FROM Exposure WHERE appId = :appId AND occurredAtMs >= :start AND occurredAtMs < :end AND revisit = 0 GROUP BY category")
    suspend fun dayCategories(appId: String, start: Long, end: Long): List<CategoryTotal>
    @Query("SELECT category, COUNT(*) AS count FROM Exposure WHERE occurredAtMs >= :start AND occurredAtMs < :end AND revisit = 0 GROUP BY category")
    suspend fun allAppDayCategories(start: Long, end: Long): List<CategoryTotal>
    @Query("SELECT category, COUNT(*) AS count FROM Exposure WHERE surfaceEpisodeId = :id AND revisit = 0 GROUP BY category")
    suspend fun visitCategories(id: String): List<CategoryTotal>
    @Query("SELECT MAX(noticeRevision) FROM ConsentRecord WHERE sourceId = :sourceId AND decision = 'allow'")
    suspend fun allowedNotice(sourceId: String): Int?
    @Query("SELECT EXISTS(SELECT 1 FROM Exposure WHERE appId = :appId LIMIT 1)")
    suspend fun hasExposure(appId: String): Boolean
    @Query("SELECT COUNT(*) FROM Exposure WHERE surfaceEpisodeId = :id")
    suspend fun visitCount(id: String): Long
    @Insert suspend fun insertCoverage(value: CoverageInterval)
    @Query("UPDATE CoverageInterval SET endMs = MAX(startMs, :at) WHERE sourceId = :source AND endMs IS NULL")
    suspend fun closeCoverage(source: String, at: Long)
    @Query("DELETE FROM SurfaceEpisode") suspend fun deleteEpisodes()
    @Query("DELETE FROM CoverageInterval") suspend fun deleteCoverage()
    @Query("DELETE FROM ConsentRecord") suspend fun deleteConsents()
}

data class CategoryTotal(val category: String, val count: Long)
data class AppCount(val appId: String, val count: Long)
data class HourTotal(val category: Int, val count: Long)
data class InterventionTotal(val kind: String, val choice: String, val count: Long)

@Database(entities = [StoreMetadata::class, SourceConfiguration::class, LiveOverlayConfiguration::class,
    ConsentRecord::class, SurfaceEpisode::class, Exposure::class, CoverageInterval::class,
    DiaryConfiguration::class, AppUsageEvent::class, ReelsTimer::class, ReelsBlockConfiguration::class,
    InterventionEvent::class],
    version = 6, autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6)],
    exportSchema = true)
abstract class AttentionDatabase : RoomDatabase() {
    abstract fun data(): AttentionDao
}
