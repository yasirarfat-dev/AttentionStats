package com.attentionstats.storage

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.withWriteTransaction
import org.attentionstats.reels.ReelApp
import java.io.File

/** Sole native history store. All openings/mutations are submitted by the serialized coordinator. */
internal class HistoryStore private constructor(
    val database: AttentionDatabase, private val driver: OwnedCipherDriver,
) : AutoCloseable {
    override fun close() { database.close(); driver.retire() }

    companion object {
        suspend fun open(context: Context, wrappingAlias: String = "attentionstats.history.wrap.v1"): HistoryStore {
            check(!context.isDeviceProtectedStorage)
            System.loadLibrary("sqlcipher")
            val path = File(context.noBackupFilesDir, "attention-history.db")
            val keys = LocalKeyEnvelope(context, path, wrappingAlias).open()
            val literal = ByteArray(67)
            literal[0] = 'x'.code.toByte(); literal[1] = '\''.code.toByte(); literal[66] = '\''.code.toByte()
            val alphabet = "0123456789abcdef"
            for (index in keys.raw.indices) {
                val value = keys.raw[index].toInt() and 255
                literal[2 + index * 2] = alphabet[value ushr 4].code.toByte()
                literal[3 + index * 2] = alphabet[value and 15].code.toByte()
            }
            val driver = try { OwnedCipherDriver(literal) } finally { literal.fill(0); keys.raw.fill(0) }
            var database: AttentionDatabase? = null
            try {
                database = Room.databaseBuilder<AttentionDatabase>(context, path.path).setDriver(driver)
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).build()
                val data = database.data()
                database.withWriteTransaction {
                    val metadata = data.metadata()
                    if (metadata == null) {
                        data.insertMetadata(StoreMetadata(generation = keys.generation,
                            installationEpoch = keys.installationEpoch, lastRevision = 0,
                            createdAtMs = System.currentTimeMillis()))
                        ReelApp.entries.forEach { data.insertConfiguration(SourceConfiguration(
                            it.sourceId, false, 0, System.currentTimeMillis())) }
                        data.insertOverlay(LiveOverlayConfiguration())
                    } else {
                        check(metadata.generation == keys.generation &&
                            metadata.installationEpoch == keys.installationEpoch) { "KEY_UNAVAILABLE" }
                    }
                    if (data.closeInterruptedEpisodes() > 0) data.advanceRevision()
                }
                return HistoryStore(database, driver)
            } catch (failure: Exception) {
                database?.close()
                driver.retire()
                throw failure
            }
        }
    }
}
