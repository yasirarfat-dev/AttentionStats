package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.os.Looper
import androidx.room3.withWriteTransaction
import com.attentionstats.storage.ConsentRecord
import com.attentionstats.storage.Exposure
import com.attentionstats.storage.HistoryStore
import com.attentionstats.storage.LocalKeyEnvelope
import com.attentionstats.storage.OwnedCipherDriver
import com.attentionstats.storage.SurfaceEpisode
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID

/** Uses a unique owned directory AND Keystore alias. Never opens or resets the live history store. */
internal class ReelsStorageProbe(private val instrumentation: Instrumentation) {
    var stage = "preflight"
        private set
    var checks = 0
        private set
    var keyRejections = 0
        private set
    var sentinelFiles = 0
        private set
    var removed = false
        private set

    private fun expect(condition: Boolean) { check(condition); checks++ }
    private fun digest(file: File): ByteArray = MessageDigest.getInstance("SHA-256").digest(file.readBytes())

    fun verify() = runBlocking {
        expect(Looper.myLooper() != Looper.getMainLooper())
        val context = instrumentation.targetContext
        val root = File(context.noBackupFilesDir, "reels-qualification-${UUID.randomUUID()}").canonicalFile
        check(root.parentFile == context.noBackupFilesDir.canonicalFile)
        check(!root.exists())
        expect(root.mkdir())
        val scoped = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir(): File = root
            override fun getApplicationContext(): Context = this
        }
        val alias = "attentionstats.history.wrap.fixture.${UUID.randomUUID()}"
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        check(!keystore.containsAlias(alias))
        val database = File(root, "attention-history.db")
        val wrapper = File(root, "history-key-v1")
        val marker = "ATTENTION_STATS_SYNTHETIC_REELS_MARKER"
        val instant = 1_800_000_000_000L
        var history: HistoryStore? = null
        try {
            stage = "fresh_store"
            history = HistoryStore.open(scoped, alias)
            var dao = history.database.data()
            val metadata = checkNotNull(dao.metadata())
            expect(metadata.lastRevision == 0L && metadata.configurationRevision == 0L)
            expect(dao.configurations().size == 2 && dao.configurations().all { !it.enabled && !it.paused })
            expect(dao.overlay()?.enabled == false)
            expect(wrapper.isFile && wrapper.length() in 1..256)
            expect(keystore.containsAlias(alias))
            val episode = UUID.randomUUID().toString()
            history.database.withWriteTransaction {
                dao.configure("reels.instagram", true, 1, instant)
                dao.insertConsent(ConsentRecord(UUID.randomUUID().toString(), "reels.instagram", 1, "allow", instant, 1))
                dao.insertEpisode(SurfaceEpisode(episode, marker, "short_video", instant, instant + 1000, 1, "estimate"))
                repeat(12) { index -> dao.insertExposure(Exposure(UUID.randomUUID().toString(), episode,
                    marker, instant + index, index.toLong(), index.toLong() + 1, 1, "estimate", 1, "synthetic", "UTC", 0,
                    listOf("reel", "photo", "ad")[index % 3])) }
                dao.advanceConfigurationRevision()
            }
            expect(dao.dayCount(marker, instant, instant + 1000) == 12L)
            expect(dao.visitCount(episode) == 12L)
            for (category in listOf("reel", "photo", "ad"))
                expect(dao.categoryCount(marker, instant, instant + 1000, category) == 4L)
            expect(dao.dayCategories(marker, instant, instant + 1000).associate { it.category to it.count } ==
                mapOf("reel" to 4L, "photo" to 4L, "ad" to 4L))
            expect(dao.visitCategories(episode).sumOf { it.count } == 12L)
            expect(dao.dayCount(marker, instant, instant + 1) == 1L) // Half-open range boundary.
            expect(checkNotNull(dao.metadata()).configurationRevision == 1L)
            stage = "encrypted_files"
            for (file in listOf(database, File(database.path + "-wal"), File(database.path + "-shm"))) {
                if (!file.exists()) continue
                expect(file.length() <= 4 * 1024 * 1024)
                val bytes = file.readBytes()
                expect(!contains(bytes, marker.toByteArray(Charsets.UTF_8)))
                expect(!contains(bytes, marker.toByteArray(Charsets.UTF_16LE)))
                sentinelFiles++
            }
            expect(!database.readBytes().take(16).toByteArray().contentEquals("SQLite format 3\u0000".toByteArray()))
            history.close(); history = null

            stage = "v1_migration"
            // Only this synthetic fixture: reconstruct installed v1 by removing the added column.
            val keys = LocalKeyEnvelope(scoped, database, alias).open()
            val literal = ("x'" + keys.raw.joinToString("") { "%02x".format(it.toInt() and 255) } + "'")
                .toByteArray(Charsets.US_ASCII)
            keys.raw.fill(0)
            val migrationDriver = try { OwnedCipherDriver(literal) } finally { literal.fill(0) }
            try {
                migrationDriver.open(database.path).use { connection ->
                    fun execute(sql: String) { connection.prepare(sql).use { it.step() } }
                    execute("ALTER TABLE Exposure DROP COLUMN category")
                    execute("UPDATE room_master_table SET identity_hash = 'b833294b18d888c8f6f9501a08ab743b' WHERE id = 42")
                    execute("PRAGMA user_version = 1")
                }
            } finally { migrationDriver.retire() }
            history = HistoryStore.open(scoped, alias)
            dao = history.database.data()
            expect(dao.dayCount(marker, instant, instant + 1000) == 12L)
            expect(dao.categoryCount(marker, instant, instant + 1000, "unclassified") == 12L)
            expect(dao.categoryCount(marker, instant, instant + 1000, "ad") == 0L)
            expect(checkNotNull(dao.metadata()).generation == metadata.generation)
            history.close(); history = null

            stage = "keystore_reopen"
            history = HistoryStore.open(scoped, alias)
            dao = history.database.data()
            expect(dao.dayCount(marker, instant, instant + 1000) == 12L)
            expect(dao.configurations().single { it.sourceId == "reels.instagram" }.enabled)
            expect(checkNotNull(dao.metadata()).generation == metadata.generation)
            history.close(); history = null

            stage = "wrapper_authentication"
            val original = wrapper.readBytes()
            val before = digest(database)
            wrapper.writeBytes(original.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
            expectRejected { HistoryStore.open(scoped, alias).close() }
            expect(before.contentEquals(digest(database)))
            wrapper.writeBytes(original)
            history = HistoryStore.open(scoped, alias)
            expect(history.database.data().dayCount(marker, instant, instant + 1000) == 12L)
            history.close(); history = null

            stage = "missing_wrapper"
            val beforeMissingWrapper = digest(database)
            expect(wrapper.delete())
            expectRejected { HistoryStore.open(scoped, alias).close() }
            expect(beforeMissingWrapper.contentEquals(digest(database)))
            wrapper.writeBytes(original)

            stage = "plaintext_rejected"
            var rejected = false
            try {
                SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY,
                    DatabaseErrorHandler { throw SQLiteException("Synthetic encrypted file preserved") }).use { plain ->
                    plain.rawQuery("SELECT * FROM Exposure", null).use { it.moveToFirst() }
                }
            } catch (_: SQLiteException) { rejected = true }
            expect(rejected && beforeMissingWrapper.contentEquals(digest(database)))

            stage = "atomic_delete"
            history = HistoryStore.open(scoped, alias)
            dao = history.database.data()
            val configBefore = checkNotNull(dao.metadata()).configurationRevision
            history.database.withWriteTransaction {
                dao.pauseEnabled(true)
                dao.deleteEpisodes()
                dao.deleteCoverage()
                dao.deleteConsents()
                dao.configure("reels.instagram", false, configBefore + 1, instant + 2000)
                dao.setOverlay(false)
                dao.advanceConfigurationRevision()
            }
            expect(dao.dayCount(marker, instant, instant + 1000) == 0L)
            expect(dao.visitCount(episode) == 0L)
            expect(!dao.configurations().single { it.sourceId == "reels.instagram" }.enabled)
            expect(checkNotNull(dao.metadata()).configurationRevision == configBefore + 1)
            history.close(); history = null

            stage = "key_loss"
            val afterDelete = digest(database)
            keystore.deleteEntry(alias) // Only this invocation's randomly named fixture key.
            expectRejected { HistoryStore.open(scoped, alias).close() }
            expect(afterDelete.contentEquals(digest(database)))
        } finally {
            history?.close()
            keystore.deleteEntry(alias)
            check(root.parentFile == context.noBackupFilesDir.canonicalFile)
            removed = root.deleteRecursively() && !root.exists()
            expect(removed)
        }
    }

    private suspend fun expectRejected(block: suspend () -> Unit) {
        var rejected = false
        try { block() } catch (_: Exception) { rejected = true }
        expect(rejected)
        keyRejections++
    }

    private fun contains(bytes: ByteArray, marker: ByteArray): Boolean {
        if (marker.size > bytes.size) return false
        for (offset in 0..bytes.size - marker.size) {
            if (marker.indices.all { bytes[offset + it] == marker[it] }) return true
        }
        return false
    }
}
