package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.ContextWrapper
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteConnection
import com.attentionstats.storage.Exposure
import com.attentionstats.storage.HistoryStore
import com.attentionstats.storage.LocalKeyEnvelope
import com.attentionstats.storage.OwnedCipherDriver
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** Synthetic stores only: exercise failed production opens, not the live coordinator. */
internal object MigrationRecoveryVerification {
    private fun SQLiteConnection.rows(sql: String): List<List<String?>> = prepare(sql).use { query ->
        buildList {
            while (query.step()) add((0 until query.getColumnCount()).map {
                if (query.isNull(it)) null else query.getText(it)
            })
        }
    }

    fun run(instrumentation: Instrumentation): String = runBlocking {
        val target = instrumentation.targetContext
        val root = File(target.noBackupFilesDir, "migration-recovery-${UUID.randomUUID()}")
        check(root.mkdir())
        val aliases = mutableListOf<String>()
        val stores = mutableListOf<HistoryStore>()
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        var checks = 0
        fun expect(value: Boolean, label: String) { check(value) { "migration recovery: $label" }; checks++ }
        fun snapshot(connection: SQLiteConnection): Map<String, List<List<String?>>> {
            val tables = connection.rows("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
                .map { checkNotNull(it.single()) }
            return tables.associateWith { table ->
                val quoted = table.replace("\"", "\"\"")
                val width = connection.rows("PRAGMA table_info(\"$quoted\")").size
                connection.rows("SELECT * FROM \"$quoted\" ORDER BY ${(1..width).joinToString(",")}")
            } + ("sqlite_master" to connection.rows("SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name"))
        }
        try {
            for (scenario in listOf("future", "migration-abort")) {
                val folder = File(root, scenario).also { check(it.mkdir()) }
                val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = folder }
                val alias = "attentionstats.migration-recovery.${UUID.randomUUID()}".also { aliases.add(it) }
                val path = File(folder, "attention-history.db")
                val store = HistoryStore.open(context, alias).also { stores.add(it) }
                store.database.withWriteTransaction {
                    usePrepared("INSERT INTO SurfaceEpisode VALUES ('kept','synthetic','viewer',1000,2000,1,'synthetic')") { it.step() }
                    store.database.data().insertExposure(Exposure(
                        id = "kept-child", surfaceEpisodeId = "kept", appId = "synthetic",
                        occurredAtMs = 1001, observedElapsedMs = 1001, ordinal = 1,
                        detectorRevision = 1, countKind = "encounter", consentRevision = 0,
                        reasonCode = "synthetic 'quoted' reason — retained", observedZoneId = "UTC",
                        observedOffsetSeconds = 0,
                    ))
                    usePrepared("UPDATE DiaryConfiguration SET breakSeconds=7, measurePlayback=0, usageEnabled=0") { it.step() }
                    if (scenario == "future") usePrepared("INSERT INTO FactOrigin VALUES ('Exposure','kept-child','00000000-0000-4000-8000-000000000001')") { it.step() }
                }
                store.close(); stores.remove(store)
                val envelope = File(folder, "history-key-v1").readBytes()
                val keys = LocalKeyEnvelope(context, path, alias).open()
                val key = ByteArray(67)
                key[0] = 'x'.code.toByte(); key[1] = '\''.code.toByte(); key[66] = '\''.code.toByte()
                val alphabet = "0123456789abcdef"
                for (index in keys.raw.indices) {
                    val value = keys.raw[index].toInt() and 255
                    key[2 + index * 2] = alphabet[value ushr 4].code.toByte()
                    key[3 + index * 2] = alphabet[value and 15].code.toByte()
                }
                val driver = try { OwnedCipherDriver(key) } finally { key.fill(0); keys.raw.fill(0) }
                try {
                    // Close raw handles before Room opens; each inspection uses a new connection.
                    driver.open(path.path).use { connection ->
                        if (scenario == "future") {
                            connection.rows("CREATE TABLE FutureSentinel (id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
                            connection.rows("INSERT INTO FutureSentinel VALUES (1,'synthetic future data')")
                            connection.rows("PRAGMA user_version=10")
                        } else {
                            // Schema 8 differs only by the absent FactOrigin table. Retain real exported identity.
                            val schema = instrumentation.context.assets.open("com.attentionstats.storage.AttentionDatabase/8.json")
                                .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
                            connection.rows("DROP TABLE FactOrigin")
                            val identity = schema.getString("identityHash")
                            check(identity.matches(Regex("[0-9a-f]+")))
                            connection.rows("UPDATE room_master_table SET identity_hash='$identity' WHERE id=42")
                            connection.rows("PRAGMA user_version=8")
                            // Abort identity publication after generated migration DDL has executed.
                            connection.rows("CREATE TRIGGER synthetic_migration_abort BEFORE INSERT ON room_master_table WHEN NEW.id=42 BEGIN SELECT RAISE(ABORT,'synthetic migration abort'); END")
                        }
                    }
                    val original = driver.open(path.path).use { snapshot(it) }
                    repeat(2) { attempt ->
                        val failure = runCatching {
                            val unexpected = HistoryStore.open(context, alias).also { stores.add(it) }
                            unexpected.close(); stores.remove(unexpected)
                        }.exceptionOrNull()
                        val diagnostic = if (scenario == "future") "migration" else "synthetic migration abort"
                        expect(failure != null && generateSequence(failure) { it.cause }.any {
                            it.message?.contains(diagnostic, ignoreCase = true) == true
                        }, "$scenario attempt $attempt rejects for expected reason")
                        driver.open(path.path).use { connection ->
                            expect(connection.rows("PRAGMA user_version") == listOf(listOf(if (scenario == "future") "10" else "8")), "$scenario version unchanged")
                            expect(snapshot(connection) == original, "$scenario every table, identity, index and trigger preserved")
                            expect(connection.rows("PRAGMA integrity_check") == listOf(listOf("ok")), "$scenario SQLite integrity")
                            expect(connection.rows("PRAGMA cipher_integrity_check").isEmpty(), "$scenario cipher integrity")
                        }
                        expect(File(folder, "history-key-v1").readBytes().contentEquals(envelope), "$scenario wrapper unchanged")
                        expect(keystore.containsAlias(alias), "$scenario wrapping alias retained")
                    }
                    if (scenario == "migration-abort") {
                        driver.open(path.path).use { it.rows("DROP TRIGGER synthetic_migration_abort") }
                        var recovered = HistoryStore.open(context, alias).also { stores.add(it) }
                        expect(recovered.database.data().diary()?.breakSeconds == 7, "retry retains saved choice")
                        expect(recovered.database.data().metadata()?.generation == keys.generation, "retry retains key identity")
                        recovered.close(); stores.remove(recovered)
                        val committed = driver.open(path.path).use { connection ->
                            expect(connection.rows("PRAGMA user_version") == listOf(listOf("9")), "retry advances version")
                            expect(connection.rows("SELECT * FROM FactOrigin").isEmpty(), "retry creates empty origin table")
                            val after = snapshot(connection)
                            expect(original.filterKeys { it != "sqlite_master" && it != "room_master_table" }
                                .all { (table, rows) -> after[table] == rows }, "retry preserves every original application row")
                            after
                        }
                        recovered = HistoryStore.open(context, alias).also { stores.add(it) }
                        recovered.close(); stores.remove(recovered)
                        expect(driver.open(path.path).use { snapshot(it) } == committed, "recovered every-table reopen")
                        expect(File(folder, "history-key-v1").readBytes().contentEquals(envelope), "retry wrapper unchanged")
                    }
                } finally { driver.retire() }
            }
            "PASS: $checks isolated migration rejection/recovery checks; repeated future-version rejection and identity-publication abort, complete logical preservation and repaired retry/reopen; no process kill or real history changes"
        } finally {
            stores.asReversed().forEach { it.close() }
            aliases.forEach { if (keystore.containsAlias(it)) keystore.deleteEntry(it) }
            check(aliases.none { keystore.containsAlias(it) })
            check(root.parentFile?.canonicalFile == target.noBackupFilesDir.canonicalFile)
            check(root.deleteRecursively() && !root.exists())
        }
    }
}
