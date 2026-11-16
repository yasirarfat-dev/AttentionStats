package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.ContextWrapper
import android.os.Process
import android.os.SystemClock
import android.util.AtomicFile
import androidx.room3.PooledConnection
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import com.attentionstats.storage.Exposure
import com.attentionstats.storage.HistoryStore
import com.attentionstats.storage.SurfaceEpisode
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID

/** Host kills only the marker's exact app PID; all database/key material here is synthetic. */
internal object DatabaseCrashVerification {
    fun verifyCleanup(instrumentation: Instrumentation): String {
        val entries = checkNotNull(instrumentation.targetContext.noBackupFilesDir.list())
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val aliases = keystore.aliases().toList()
        for (prefix in listOf("database-crash-", "migration-recovery-")) check(entries.none { it.startsWith(prefix) })
        for (prefix in listOf("attentionstats.database-crash.", "attentionstats.migration-recovery."))
            check(aliases.none { it.startsWith(prefix) })
        return "PASS: 4 independent crash/migration directory and Keystore prefix absence checks; no live store opened"
    }

    private suspend fun PooledConnection.rows(sql: String): List<List<String?>> = usePrepared(sql) { query ->
        buildList {
            while (query.step()) add((0 until query.getColumnCount()).map {
                if (query.isNull(it)) null else query.getText(it)
            })
        }
    }

    private suspend fun snapshot(store: HistoryStore): Map<String, List<List<String?>>> =
        store.database.withReadTransaction {
            rows("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
                .map { checkNotNull(it.single()) }.associateWith { table ->
                    val quoted = table.replace("\"", "\"\"")
                    val width = rows("PRAGMA table_info(\"$quoted\")").size
                    rows("SELECT * FROM \"$quoted\" ORDER BY ${(1..width).joinToString(",")}")
                }
        }

    private fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }

    private fun encoded(value: Map<String, List<List<String?>>>) = JSONObject().apply {
        value.forEach { (table, rows) -> put(table, JSONArray().apply {
            rows.forEach { row -> put(JSONArray().apply { row.forEach { put(it ?: JSONObject.NULL) } }) }
        }) }
    }

    private fun decoded(value: JSONObject): Map<String, List<List<String?>>> = value.keys().asSequence()
        .associateWith { table -> value.getJSONArray(table).let { rows ->
            (0 until rows.length()).map { index -> rows.getJSONArray(index).let { row ->
                (0 until row.length()).map { if (row.isNull(it)) null else row.getString(it) }
            } }
        } }

    fun run(instrumentation: Instrumentation, mode: String, token: String): String = runBlocking {
        check(UUID.fromString(token).toString() == token)
        check(mode in setOf("prepare-before", "prepare-after", "verify-before", "verify-after"))
        val target = instrumentation.targetContext
        val root = File(target.noBackupFilesDir, "database-crash-$token")
        check(root.parentFile?.canonicalFile == target.noBackupFilesDir.canonicalFile)
        val alias = "attentionstats.database-crash.$token"
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = root }
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val before = mode.endsWith("before")
        val preparing = mode.startsWith("prepare")
        if (preparing) {
            // Never enter cleanup ownership if a token already belongs to another trial.
            check(!root.exists() && !keystore.containsAlias(alias))
            check(root.mkdir())
        }
        var store: HistoryStore? = null
        var checks = 0
        fun expect(value: Boolean, label: String) { check(value) { "database crash: $label" }; checks++ }
        suspend fun hold() {
            val deadline = SystemClock.elapsedRealtime() + 90_000
            while (SystemClock.elapsedRealtime() < deadline) delay(100)
            error("database crash: host did not kill prepared process within 90 seconds")
        }
        fun ready(expected: Map<String, List<List<String?>>>) {
            val report = JSONObject().put("token", token).put("before", before).put("pid", Process.myPid())
                .put("writerPolicyChecks", checks)
                .put("wrapperSha256", digest(File(root, "history-key-v1")))
                .put("expected", encoded(expected))
            val marker = AtomicFile(File(root, "ready.json"))
            val output = marker.startWrite()
            try { output.write(report.toString().toByteArray()); marker.finishWrite(output) }
            catch (failure: Throwable) { marker.failWrite(output); throw failure }
        }
        try {
            if (preparing) {
                val current = HistoryStore.open(context, alias).also { store = it }
                current.database.withWriteTransaction {
                    current.database.data().insertEpisode(SurfaceEpisode("kept", "com.example.synthetic", "viewer", 1000, 2000, 1, "synthetic"))
                    current.database.data().insertExposure(exposure("kept-child", "kept", 1))
                    rows("UPDATE DiaryConfiguration SET breakSeconds=7, measurePlayback=0, usageEnabled=0")
                }
                val original = snapshot(current)
                current.database.withWriteTransaction {
                    expect(rows("PRAGMA synchronous") == listOf(listOf("2")), "writer FULL before crash")
                    expect(rows("PRAGMA journal_mode") == listOf(listOf("wal")), "writer WAL before crash")
                    current.database.data().insertEpisode(SurfaceEpisode("candidate", "com.example.synthetic", "viewer", 3000, 4000, 1, "synthetic"))
                    repeat(32) { current.database.data().insertExposure(exposure("candidate-$it", "candidate", it.toLong())) }
                    rows("UPDATE DiaryConfiguration SET breakSeconds=11")
                    current.database.data().advanceRevision()
                    if (before) { ready(original); hold() }
                }
                // Marker follows a returned commit, while the database and its WAL remain open.
                check(!before)
                check(File(root, "attention-history.db-wal").length() > 0)
                ready(snapshot(current)); hold()
                error("unreachable")
            }
            check(root.isDirectory && keystore.containsAlias(alias))
            val report = JSONObject(File(root, "ready.json").readText())
            expect(report.getString("token") == token && report.getBoolean("before") == before, "owned marker matches phase")
            expect(report.getInt("pid") != Process.myPid(), "verification is in a new process")
            expect(report.getInt("writerPolicyChecks") == 2, "prepared writer used WAL and FULL")
            val expected = decoded(report.getJSONObject("expected"))
            val wrapper = File(root, "history-key-v1")
            expect(digest(wrapper) == report.getString("wrapperSha256"), "wrapper retained across kill")
            var current = HistoryStore.open(context, alias).also { store = it }
            expect(snapshot(current) == expected, "all original tables recover exact precommit or postcommit state")
            current.database.withReadTransaction {
                expect(rows("PRAGMA integrity_check") == listOf(listOf("ok")), "SQLite integrity after kill")
                expect(rows("PRAGMA cipher_integrity_check").isEmpty(), "cipher integrity after kill")
                expect(rows("PRAGMA foreign_key_check").isEmpty(), "no orphan after kill")
                expect(rows("SELECT COUNT(*) FROM Exposure") == listOf(listOf(if (before) "1" else "33")), "whole child batch recovered atomically")
                expect(rows("SELECT breakSeconds FROM DiaryConfiguration") == listOf(listOf(if (before) "7" else "11")), "choice commit boundary retained")
            }
            current.close(); store = null
            current = HistoryStore.open(context, alias).also { store = it }
            expect(snapshot(current) == expected, "second encrypted reopen is idempotent")
            current.database.withWriteTransaction {
                current.database.data().insertExposure(exposure("retry", "kept", 2))
                current.database.data().advanceRevision()
            }
            val retried = snapshot(current)
            current.close(); store = null
            current = HistoryStore.open(context, alias).also { store = it }
            expect(snapshot(current) == retried, "subsequent write and revision persist after reopen")
            expect(digest(wrapper) == report.getString("wrapperSha256") && keystore.containsAlias(alias), "wrapping identity unchanged")
            "PASS: $checks isolated ${if (before) "precommit rollback" else "postcommit durability"} process-kill recovery checks; all-table recovery, whole batch/configuration boundary, integrity, repeat reopen and retry; not power-loss or owner/UI acceptance"
        } finally {
            // SIGKILL deliberately bypasses finally in preparation; verification owns that cleanup.
            store?.close()
            if (keystore.containsAlias(alias)) keystore.deleteEntry(alias)
            check(!keystore.containsAlias(alias))
            if (root.exists()) check(root.deleteRecursively())
            check(!root.exists())
        }
    }

    private fun exposure(id: String, episode: String, ordinal: Long) = Exposure(
        id = id, surfaceEpisodeId = episode, appId = "com.example.synthetic",
        occurredAtMs = if (episode == "candidate") 3001 else 1001,
        observedElapsedMs = if (episode == "candidate") 3001 else 1001,
        ordinal = ordinal, detectorRevision = 1, countKind = "encounter",
        consentRevision = 0, reasonCode = "synthetic crash fixture", observedZoneId = "UTC", observedOffsetSeconds = 0,
    )
}
