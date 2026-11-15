package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.ContextWrapper
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import com.attentionstats.backup.LogicalArchive
import com.attentionstats.storage.Exposure
import com.attentionstats.storage.HistoryStore
import com.attentionstats.storage.SurfaceEpisode
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** Genuine SQLite page-capacity failures in isolated production SQLCipher/Room stores. */
internal object ArchiveCapacityVerification {
    fun run(instrumentation: Instrumentation): String = runBlocking {
        val target = instrumentation.targetContext
        val root = File(target.noBackupFilesDir, "archive-capacity-${UUID.randomUUID()}")
        check(root.mkdir())
        val stores = mutableListOf<HistoryStore>()
        val aliases = mutableListOf<String>()
        val buffers = mutableListOf<ByteArray>()
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        var checks = 0
        fun expect(value: Boolean) { check(value) { "archive capacity check ${checks + 1}" }; checks++ }
        fun context(name: String) = object : ContextWrapper(target) {
            private val folder = File(root, name).also { check(it.mkdir()) }
            override fun getNoBackupFilesDir() = folder
        }
        fun alias() = "attentionstats.archive-capacity.${UUID.randomUUID()}".also { aliases.add(it) }
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
                    "synthetic-capacity-" + "x".repeat(2000), "UTC", 0, "reel", mediaKind = "video"))
            }
        }
        try {
            val source = open(context("source"), alias())
            seed(source, "incoming", 256)
            val sourceBefore = snapshot(source)
            val plain = LogicalArchive.export(source) {}.also { buffers.add(it) }
            val validated = open(context("validated"), alias())
            LogicalArchive.stage(validated, plain) {}
            val incoming = snapshot(validated, metadata = false)
            var fullTrials = 0
            var firstFailureBoundary = 0
            for (replace in listOf(false, true)) {
                val destinationContext = context(if (replace) "replace" else "stage")
                val destinationAlias = alias()
                var destination = open(destinationContext, destinationAlias)
                seed(destination, "original", 1)
                val original = snapshot(destination)
                expect(snapshot(destination, metadata = false) != incoming)
                var callbacks = 0
                // Set/read back the cap on the same confined writer used by nested archive calls.
                // This triggers real SQLITE_FULL without consuming the phone's free storage.
                val failure = runCatching { destination.database.withWriteTransaction {
                    val pages = usePrepared("PRAGMA page_count") { check(it.step()); it.getLong(0) }
                    expect(pages > 0)
                    usePrepared("PRAGMA max_page_count=$pages") {
                        expect(it.step() && it.getLong(0) == pages)
                    }
                    if (replace) LogicalArchive.replace(destination, validated) { callbacks++ }
                    else LogicalArchive.stage(destination, plain) { callbacks++ }
                } }.exceptionOrNull()
                // Reject unrelated failures: require the SQLCipher driver's SQLite FULL diagnostic.
                expect(failure != null && generateSequence(failure) { it.cause }.any {
                    it.message?.contains("code: 13") == true ||
                        it.message?.contains("SQLITE_FULL") == true ||
                        it is android.database.sqlite.SQLiteFullException
                })
                expect(callbacks > if (replace) 270 else 5)
                if (!replace) firstFailureBoundary = callbacks
                fullTrials++
                expect(snapshot(destination) == original)
                destination = reopen(destination, destinationContext, destinationAlias)
                expect(snapshot(destination) == original)
                // A generous finite cap on this writer models restored capacity for the retry.
                destination.database.withWriteTransaction {
                    usePrepared("PRAGMA max_page_count=4096") { expect(it.step() && it.getLong(0) == 4096L) }
                    if (replace) LogicalArchive.replace(destination, validated) {}
                    else LogicalArchive.stage(destination, plain) {}
                }
                expect(snapshot(destination, metadata = false) == incoming)
                val committed = snapshot(destination)
                destination = reopen(destination, destinationContext, destinationAlias)
                expect(snapshot(destination) == committed)
                expect(snapshot(source) == sourceBefore)
                expect(snapshot(validated, metadata = false) == incoming)
            }
            "PASS: $checks isolated archive capacity checks; SQLITE_FULL trials=$fullTrials; stage failure callback=$firstFailureBoundary; full-row rollback/reopen and restored-capacity retry; no device disk exhaustion or real history changes"
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
