package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.ContextWrapper
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import com.attentionstats.backup.LogicalArchive
import com.attentionstats.storage.Exposure
import com.attentionstats.storage.HistoryStore
import com.attentionstats.storage.SurfaceEpisode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.util.UUID

/** Every logical-record cancellation boundary, on isolated production SQLCipher/Room stores. */
internal object ArchiveInterruptionVerification {
    fun run(instrumentation: Instrumentation): String = runBlocking {
        val target = instrumentation.targetContext
        val root = File(target.noBackupFilesDir, "archive-interruption-${UUID.randomUUID()}")
        check(root.mkdir())
        val stores = mutableListOf<HistoryStore>()
        val aliases = mutableListOf<String>()
        val buffers = mutableListOf<ByteArray>()
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        var checks = 0
        fun expect(value: Boolean) { check(value) { "archive interruption check ${checks + 1}" }; checks++ }
        fun context(name: String) = object : ContextWrapper(target) {
            private val folder = File(root, name).also { check(it.mkdir()) }
            override fun getNoBackupFilesDir() = folder
        }
        fun alias() = "attentionstats.archive-interruption.${UUID.randomUUID()}".also { aliases.add(it) }
        suspend fun open(context: ContextWrapper, alias: String) =
            HistoryStore.open(context, alias).also { stores.add(it) }
        suspend fun reopen(store: HistoryStore, context: ContextWrapper, alias: String): HistoryStore {
            store.close(); stores.remove(store)
            return open(context, alias)
        }
        // Compare every persisted application table, including provenance and local metadata.
        // Export bytes are unsuitable: their manifest contains the current wall-clock time.
        suspend fun snapshot(store: HistoryStore, metadata: Boolean = true): String = store.database.withReadTransaction {
            val names = usePrepared("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table' ORDER BY name") {
                buildList { while (it.step()) add(it.getText(0)) }
            }
            val result = JSONArray()
            for (table in names.filter { metadata || it != "StoreMetadata" }) {
                require(Regex("[A-Za-z][A-Za-z0-9_]*").matches(table))
                val count = usePrepared("PRAGMA table_info(`$table`)") { query ->
                    var columns = 0; while (query.step()) columns++; columns
                }
                val rows = JSONArray()
                usePrepared("SELECT * FROM `$table` ORDER BY ${(1..count).joinToString(",")}") { query ->
                    while (query.step()) {
                        val row = JSONArray()
                        repeat(count) { row.put(if (query.isNull(it)) org.json.JSONObject.NULL else query.getText(it)) }
                        rows.put(row)
                    }
                }
                result.put(JSONArray().put(table).put(rows))
            }
            result.toString()
        }
        suspend fun seed(store: HistoryStore, prefix: String, count: Int) = store.database.withWriteTransaction {
            store.database.data().insertEpisode(SurfaceEpisode("$prefix-episode", "instagram", "home_video",
                1_000, 5_000, 1, "synthetic"))
            repeat(count) { index ->
                store.database.data().insertExposure(Exposure("$prefix-$index", "$prefix-episode", "instagram",
                    1_000L + index, 1_000L + index, index + 1L, 1, "synthetic", 1,
                    "synthetic-interruption", "UTC", 0, "reel", mediaKind = "video"))
            }
        }
        suspend fun cancelledAt(boundary: Int, operation: suspend (() -> Unit) -> Unit) {
            val injected = CancellationException("synthetic logical-record cancellation")
            var calls = 0
            val failure = runCatching { operation { if (++calls == boundary) throw injected } }.exceptionOrNull()
            expect(failure === injected)
            expect(calls == boundary)
        }
        try {
            val sourceContext = context("source"); val sourceAlias = alias()
            var source = open(sourceContext, sourceAlias)
            seed(source, "incoming", 3)
            val sourceBefore = snapshot(source)
            var exportBoundaries = 0
            val plain = LogicalArchive.export(source) { exportBoundaries++ }.also { buffers.add(it) }
            expect(exportBoundaries > 5)
            for (boundary in 1..exportBoundaries) {
                cancelledAt(boundary) { cancel ->
                    LogicalArchive.export(source, cancel).also { it.fill(0) }
                }
                expect(snapshot(source) == sourceBefore)
                source = reopen(source, sourceContext, sourceAlias)
                expect(snapshot(source) == sourceBefore)
            }

            val validated = open(context("validated"), alias())
            var stageBoundaries = 0
            LogicalArchive.stage(validated, plain) { stageBoundaries++ }
            expect(stageBoundaries == exportBoundaries)
            val incoming = snapshot(validated, metadata = false)
            val destinationContext = context("destination"); val destinationAlias = alias()
            var destination = open(destinationContext, destinationAlias)
            seed(destination, "original", 1)
            val original = snapshot(destination)
            expect(snapshot(destination, metadata = false) != incoming)
            for (boundary in 1..stageBoundaries) {
                // Includes cancellation after DELETEs and after each partially inserted record.
                cancelledAt(boundary) { cancel -> LogicalArchive.stage(destination, plain, cancel) }
                expect(snapshot(destination) == original)
                destination = reopen(destination, destinationContext, destinationAlias)
                expect(snapshot(destination) == original)
            }

            // Count the actual nested export + stage callbacks, rather than assuming a fixed schema.
            val probe = open(context("probe"), alias())
            var replaceBoundaries = 0
            probe.database.withWriteTransaction {
                LogicalArchive.replace(probe, validated) { replaceBoundaries++ }
            }
            expect(replaceBoundaries == exportBoundaries + stageBoundaries)
            for (boundary in 1..replaceBoundaries) {
                cancelledAt(boundary) { cancel -> destination.database.withWriteTransaction {
                    LogicalArchive.replace(destination, validated, cancel)
                } }
                expect(snapshot(destination) == original)
                destination = reopen(destination, destinationContext, destinationAlias)
                expect(snapshot(destination) == original)
            }

            // All records inserted, but the caller's transaction has not committed yet.
            val beforeCommit = IOException("synthetic pre-commit failure")
            val rollback = runCatching { destination.database.withWriteTransaction {
                LogicalArchive.replace(destination, validated) {}
                throw beforeCommit
            } }.exceptionOrNull()
            expect(rollback === beforeCommit)
            expect(snapshot(destination) == original)
            destination = reopen(destination, destinationContext, destinationAlias)
            expect(snapshot(destination) == original)

            val localMetadata = destination.database.data().metadata()
            // Simulate failed delivery after the transaction returned, then verify durable data.
            val afterCommit = IOException("synthetic post-commit observer failure")
            val observer = runCatching {
                destination.database.withWriteTransaction { LogicalArchive.replace(destination, validated) {} }
                throw afterCommit
            }.exceptionOrNull()
            expect(observer === afterCommit)
            expect(snapshot(destination, metadata = false) == incoming)
            expect(destination.database.data().metadata() == localMetadata)
            val committed = snapshot(destination)
            destination = reopen(destination, destinationContext, destinationAlias)
            expect(snapshot(destination) == committed)
            // Reapplying this low-level replacement has no extra facts or provenance changes.
            destination.database.withWriteTransaction { LogicalArchive.replace(destination, validated) {} }
            expect(snapshot(destination) == committed)
            expect(snapshot(validated, metadata = false) == incoming)
            expect(snapshot(source) == sourceBefore)
            "PASS: $checks isolated archive interruption checks; export=$exportBoundaries stage=$stageBoundaries replace=$replaceBoundaries cancellation boundaries; reopen preservation, pre-commit rollback, post-commit persistence and repeat replacement; no transport or real history changes"
        } finally {
            buffers.forEach { it.fill(0) }
            stores.asReversed().forEach { it.close() }
            aliases.forEach { if (keystore.containsAlias(it)) keystore.deleteEntry(it) }
            check(aliases.none { keystore.containsAlias(it) })
            check(root.parentFile?.canonicalFile == target.noBackupFilesDir.canonicalFile)
            check(root.deleteRecursively())
            check(!root.exists())
        }
    }
}
