package org.attentionstats.qualification.storage

import android.app.Activity
import android.app.Instrumentation
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.os.Looper
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/** No UI, permissions, personal records, Keystore changes or production app access. */
class StorageInstrumentation : Instrumentation() {
    private var stage = "preflight"
    private val result = Bundle()
    private var checks = 0

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        try {
            verify()
            result.putInt("checks", checks)
            result.putString("stream", "PASS: synthetic encrypted Room qualification\n")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("stage", stage)
            // SQL exceptions can contain raw paths/SQL; emit only a fixed type.
            result.putString("stream", "FAIL: ${failure.javaClass.simpleName}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun expect(condition: Boolean) {
        check(condition)
        checks++
    }

    private fun verify() = runBlocking {
        expect(Looper.myLooper() != Looper.getMainLooper())
        System.loadLibrary("sqlcipher")
        val root = File(targetContext.noBackupFilesDir, "storage-fixture-${UUID.randomUUID()}")
        expect(root.mkdir())
        val rawKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        // SQLCipher's documented raw-key text form, delivered through sqlite3_key's byte API.
        val key = ("x'" + rawKey.joinToString("") { "%02x".format(it.toInt() and 255) } + "'")
            .toByteArray(Charsets.US_ASCII)
        val marker = "ATTENTION_STATS_SYNTHETIC_PRIVATE_MARKER_".repeat(16)
        val database = File(root, "fixture.db")
        try {
            stage = "room_write_wal"
            withRoom(database, key) { room, driver ->
                room.records().insert((1..128).map { FixtureRecord(it, marker + it) })
                expect(room.records().count() == 128)
                expect(room.records().marker(64) == marker + 64)
                expect(driver.opened.get() > 0)
                val wal = File(database.path + "-wal")
                expect(wal.isFile && wal.length() > 32)
                result.putLong("wal_bytes", wal.length())
                inspectFiles(root, marker)
            }

            stage = "reopen"
            withRoom(database, key) { room, _ ->
                expect(room.records().count() == 128)
                expect(room.records().marker(64) == marker + 64)
                room.records().insert(listOf(FixtureRecord(129, marker + 129)))
                expect(room.records().count() == 129)
            }

            stage = "raw_driver_interop"
            withConnection(database, key) { connection ->
                connection.prepare("SELECT COUNT(*) FROM synthetic_records").use {
                    expect(it.step() && it.getLong(0) == 129L)
                }
                connection.prepare("PRAGMA cipher_integrity_check").use {
                    // SQLCipher returns no rows when every page HMAC verifies.
                    expect(!it.step())
                }
                connection.prepare("PRAGMA integrity_check").use {
                    expect(it.step() && it.getText(0) == "ok")
                }
            }
            expect(database.readBytes().take(16).toByteArray()
                .contentEquals("SQLite format 3\u0000".toByteArray()).not())

            stage = "wrong_key"
            val wrong = key.copyOf().also { it[2] = if (it[2] == 48.toByte()) 49 else 48 }
            try {
                expectRejectedPreserved(database, wrong)
            } finally { wrong.fill(0) }
            stage = "raw_bytes_are_not_literal"
            expectRejectedPreserved(database, rawKey)

            stage = "plain_sqlite_rejected"
            val beforePlain = digest(database)
            var plainRejected = false
            try {
                android.database.sqlite.SQLiteDatabase.openDatabase(
                    database.path, null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY or
                        android.database.sqlite.SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                    android.database.DatabaseErrorHandler {
                        throw SQLiteException("Synthetic database preserved")
                    },
                ).use { db -> db.rawQuery("SELECT COUNT(*) FROM sqlite_master", null).use { it.moveToFirst() } }
            } catch (_: SQLiteException) { plainRejected = true }
            expect(plainRejected)
            expect(beforePlain.contentEquals(digest(database)))

            stage = "corruption"
            val corrupted = File(root, "corrupted.db")
            database.copyTo(corrupted)
            RandomAccessFile(corrupted, "rw").use {
                // Change authenticated ciphertext in page one; leave salt untouched.
                it.seek(128)
                val old = it.readByte()
                it.seek(128)
                it.writeByte(old.toInt() xor 1)
            }
            expectRejectedPreserved(corrupted, key)
            stage = "original_still_readable"
            withRoom(database, key) { room, _ -> expect(room.records().count() == 129) }
            inspectFiles(root, marker)
        } finally {
            rawKey.fill(0)
            key.fill(0)
            // Only the unpredictable directory created by this run can be removed.
            try {
                expect(root.canonicalFile.parentFile == targetContext.noBackupFilesDir.canonicalFile)
                expect(root.deleteRecursively())
                expect(!root.exists())
            } catch (failure: Throwable) {
                stage = "cleanup"
                throw failure
            }
            result.putInt("files_removed", 1)
        }
    }

    private suspend fun withRoom(
        path: File,
        key: ByteArray,
        block: suspend (FixtureDatabase, OwnedCipherDriver) -> Unit,
    ) {
        val driver = OwnedCipherDriver(key)
        var room: FixtureDatabase? = null
        try {
            room = buildRoom(path, driver)
            block(room, driver)
        } finally {
            room?.close()
            driver.retire()
            checks++ // retire asserts all driver connections were closed before wiping.
        }
    }

    private fun withConnection(path: File, key: ByteArray, block: (SQLiteConnection) -> Unit) {
        val driver = OwnedCipherDriver(key)
        try {
            driver.open(path.path).use { connection ->
                var prematureRetireRejected = false
                try { driver.retire() } catch (_: IllegalStateException) { prematureRetireRejected = true }
                expect(prematureRetireRejected)
                block(connection)
            }
        } finally { driver.retire() }
    }

    private fun buildRoom(path: File, driver: OwnedCipherDriver): FixtureDatabase =
        Room.databaseBuilder<FixtureDatabase>(targetContext, path.path)
            .setDriver(driver)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    private suspend fun expectRejectedPreserved(path: File, key: ByteArray) {
        val before = digest(path)
        val driver = OwnedCipherDriver(key)
        var room: FixtureDatabase? = null
        var rejected = false
        try {
            room = buildRoom(path, driver)
            room.records().count()
        } catch (_: SQLiteException) { rejected = true } finally {
            room?.close()
            driver.retire()
        }
        expect(rejected)
        expect(path.isFile && before.contentEquals(digest(path)))
        result.putInt("preserved_failures", result.getInt("preserved_failures") + 1)
        result.putInt("handler_calls", result.getInt("handler_calls") + driver.handler.calls.get())
    }

    private fun digest(path: File): ByteArray = MessageDigest.getInstance("SHA-256").digest(path.readBytes())

    private fun inspectFiles(root: File, marker: String) {
        val token = marker.take(39)
        val needles = listOf(token.toByteArray(), token.toByteArray(Charsets.UTF_16LE))
        val files = root.listFiles() ?: error("Missing owned fixture directory")
        expect(files.isNotEmpty())
        for (file in files) {
            expect(file.isFile && file.length() <= 4 * 1024 * 1024)
            val bytes = file.readBytes()
            for (needle in needles) {
                expect((0..(bytes.size - needle.size)).none { offset ->
                    needle.indices.all { bytes[offset + it] == needle[it] }
                })
            }
        }
        result.putInt("sentinel_scans", result.getInt("sentinel_scans") + files.size)
    }
}
