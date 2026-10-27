package com.attentionstats.storage

import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
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
data class LiveOverlayConfiguration(@PrimaryKey val id: Int = 1, val enabled: Boolean = false)

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

@Dao
interface AttentionDao {
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
    @Insert suspend fun insertConsent(value: ConsentRecord)
    @Insert suspend fun insertEpisode(value: SurfaceEpisode)
    @Query("UPDATE SurfaceEpisode SET endMs = :at WHERE id = :id AND endMs IS NULL")
    suspend fun closeEpisode(id: String, at: Long)
    @Query("UPDATE SurfaceEpisode SET endMs = startMs, quality = 'interrupted' WHERE endMs IS NULL")
    suspend fun closeInterruptedEpisodes(): Int
    @Insert suspend fun insertExposure(value: Exposure)
    @Query("SELECT COUNT(*) FROM Exposure WHERE appId = :appId AND occurredAtMs >= :start AND occurredAtMs < :end")
    suspend fun dayCount(appId: String, start: Long, end: Long): Long
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

@Database(entities = [StoreMetadata::class, SourceConfiguration::class, LiveOverlayConfiguration::class,
    ConsentRecord::class, SurfaceEpisode::class, Exposure::class, CoverageInterval::class],
    version = 1, exportSchema = true)
abstract class AttentionDatabase : RoomDatabase() {
    abstract fun data(): AttentionDao
}
