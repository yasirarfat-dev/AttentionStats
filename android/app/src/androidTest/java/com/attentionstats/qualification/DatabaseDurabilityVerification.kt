package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.ContextWrapper
import androidx.room3.PooledConnection
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import com.attentionstats.storage.HistoryStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** Inspect the real opener's connections; the harness never changes synchronization settings. */
internal object DatabaseDurabilityVerification {
    private suspend fun PooledConnection.rows(sql: String): List<List<String?>> = usePrepared(sql) { query ->
        buildList {
            while (query.step()) add((0 until query.getColumnCount()).map {
                if (query.isNull(it)) null else query.getText(it)
            })
        }
    }

    fun run(instrumentation: Instrumentation): String = runBlocking {
        val target = instrumentation.targetContext
        val root = File(target.noBackupFilesDir, "database-durability-${UUID.randomUUID()}")
        check(root.mkdir())
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = root }
        val alias = "attentionstats.database-durability.${UUID.randomUUID()}"
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        var store: HistoryStore? = null
        var checks = 0
        fun expect(value: Boolean, label: String) { check(value) { "database durability: $label" }; checks++ }
        val report = JSONObject()
        suspend fun PooledConnection.settings(): Map<String, String> = listOf(
            "foreign_keys", "journal_mode", "synchronous", "wal_autocheckpoint", "page_size",
            "cipher_page_size", "cipher_use_hmac", "cipher_version", "user_version",
        ).associateWith { name ->
            val values = rows("PRAGMA $name")
            expect(values.size == 1 && values.single().size == 1 && values.single().single() != null,
                "$name returns an actual value")
            values.single().single()!!
        }
        suspend fun inspect(current: HistoryStore, phase: String) {
            // SQLCipher has an internal pool: a writer-role lease without a transaction
            // does not pin a native writer. Measure while the write transaction owns it.
            val writer = current.database.withWriteTransaction { settings() }
            val reader = current.database.useReaderConnection { it.settings() }
            report.put(phase, JSONObject().put("writer", JSONObject(writer)).put("reader", JSONObject(reader)))
            for ((role, values) in listOf("writer" to writer, "reader" to reader)) {
                expect(values.getValue("foreign_keys") == "1", "$phase $role foreign keys enabled")
                expect(values.getValue("journal_mode") == "wal", "$phase $role WAL enabled")
                expect(if (role == "writer") values.getValue("synchronous") == "2"
                    else values.getValue("synchronous").toInt() in 1..3, "$phase $role synchronization policy")
                expect(values.getValue("wal_autocheckpoint").toLong() > 0, "$phase $role automatic checkpoint enabled")
                expect(values.getValue("page_size") == values.getValue("cipher_page_size"), "$phase $role cipher page size")
                expect(values.getValue("cipher_use_hmac") == "1", "$phase $role HMAC enabled")
                expect(values.getValue("user_version") == "9", "$phase $role schema version")
            }
            current.database.withReadTransaction {
                expect(rows("PRAGMA integrity_check") == listOf(listOf("ok")), "$phase full SQLite integrity")
                expect(rows("PRAGMA foreign_key_check").isEmpty(), "$phase no foreign key violations")
                expect(rows("PRAGMA cipher_integrity_check").isEmpty(), "$phase cipher page HMAC integrity")
            }
        }
        suspend fun exposureCount(current: HistoryStore) = current.database.withReadTransaction {
            rows("SELECT COUNT(*) FROM Exposure").single().single()!!.toInt()
        }
        suspend fun childRows(current: HistoryStore) = current.database.withReadTransaction {
            rows("SELECT * FROM Exposure ORDER BY id")
        }
        suspend fun reject(current: HistoryStore, statement: String, label: String, diagnostic: String) {
            val original = childRows(current)
            val failure = runCatching { current.database.withWriteTransaction { rows(statement) } }.exceptionOrNull()
            expect(failure != null && generateSequence(failure) { it.cause }.any {
                it.message?.contains(diagnostic, ignoreCase = true) == true
            }, "$label rejected by SQLite constraint")
            expect(childRows(current) == original, "$label preserves every child column")
        }
        // Named columns avoid depending on Room-generated column order; all values are synthetic.
        fun insertExposure(id: String, parent: String = "parent", ordinal: Int = 1) =
            "INSERT INTO Exposure (id,surfaceEpisodeId,appId,occurredAtMs,observedElapsedMs,ordinal,detectorRevision,countKind,consentRevision,reasonCode,observedZoneId,observedOffsetSeconds) " +
                "VALUES ('$id','$parent','synthetic',1000,1000,$ordinal,1,'synthetic',0,'synthetic','UTC',0)"
        try {
            var current = HistoryStore.open(context, alias).also { store = it }
            current.database.withReadTransaction {
                report.put("sqliteVersion", rows("SELECT sqlite_version()").single().single())
                report.put("sqliteSourceId", rows("SELECT sqlite_source_id()").single().single())
            }
            inspect(current, "fresh")
            current.database.withWriteTransaction {
                rows("INSERT INTO SurfaceEpisode VALUES ('parent','synthetic','synthetic',1000,2000,1,'synthetic')")
                rows(insertExposure("kept"))
            }
            expect(exposureCount(current) == 1, "committed valid child")
            val committed = childRows(current)
            reject(current, insertExposure("orphan", "missing"), "foreign key", "FOREIGN KEY constraint failed")
            reject(current, insertExposure("kept", ordinal = 2), "primary key", "UNIQUE constraint failed: Exposure.id")
            reject(current, insertExposure("duplicate-ordinal"), "unique parent ordinal", "UNIQUE constraint failed: Exposure.surfaceEpisodeId, Exposure.ordinal")
            reject(current, "UPDATE Exposure SET appId=NULL WHERE id='kept'", "not null", "NOT NULL constraint failed: Exposure.appId")
            expect(exposureCount(current) == 1, "constraint failures preserve committed child")
            val rollbackMarker = IllegalStateException("synthetic rollback")
            val rollback = runCatching { current.database.withWriteTransaction {
                rows("DELETE FROM SurfaceEpisode WHERE id='parent'")
                expect(rows("SELECT COUNT(*) FROM Exposure") == listOf(listOf("0")), "cascade inside transaction")
                throw rollbackMarker
            } }.exceptionOrNull()
            expect(rollback === rollbackMarker && exposureCount(current) == 1, "cascade rollback restores child")
            expect(childRows(current) == committed, "cascade rollback preserves every child column")
            // Checkpoint on the confined writer outside a transaction; read all three status columns.
            current.database.useWriterConnection { connection ->
                val checkpoint = connection.rows("PRAGMA wal_checkpoint(FULL)").single()
                expect(checkpoint.size == 3 && checkpoint[0] == "0" && checkpoint[1] == checkpoint[2], "complete nonbusy checkpoint")
                report.put("checkpoint", org.json.JSONArray(checkpoint))
            }
            current.close(); store = null
            current = HistoryStore.open(context, alias).also { store = it }
            expect(exposureCount(current) == 1, "committed child survives reopen")
            expect(childRows(current) == committed, "every child column survives reopen")
            inspect(current, "reopened")
            current.database.withWriteTransaction { rows("DELETE FROM SurfaceEpisode WHERE id='parent'") }
            expect(exposureCount(current) == 0, "committed cascade")
            current.close(); store = null
            current = HistoryStore.open(context, alias).also { store = it }
            expect(exposureCount(current) == 0, "cascade survives reopen")
            inspect(current, "afterCascade")
            "PASS: $checks isolated database inspection checks; effective settings=${report}; constraints, rollback, checkpoint and encrypted reopen; synchronization level is reported, not power-loss acceptance"
        } finally {
            store?.close()
            if (keystore.containsAlias(alias)) keystore.deleteEntry(alias)
            check(!keystore.containsAlias(alias))
            check(root.parentFile?.canonicalFile == target.noBackupFilesDir.canonicalFile)
            check(root.deleteRecursively() && !root.exists())
        }
    }
}
