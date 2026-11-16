package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.ContextWrapper
import androidx.room3.withReadTransaction
import androidx.sqlite.SQLiteConnection
import com.attentionstats.storage.HistoryStore
import com.attentionstats.storage.LocalKeyEnvelope
import com.attentionstats.storage.OwnedCipherDriver
import kotlinx.coroutines.runBlocking
import org.attentionstats.reels.ReelApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** Historical exported DDL + synthetic rows, upgraded only through the production opener. */
internal object HistoricalMigrationVerification {
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun sql(value: Any?): String = when (value) {
        null -> "NULL"
        is Number -> value.toString()
        else -> "'${value.toString().replace("'", "''")}'"
    }
    private fun SQLiteConnection.exec(sql: String) { prepare(sql).use { it.step() } }

    fun run(instrumentation: Instrumentation): String = runBlocking {
        val target = instrumentation.targetContext
        val root = File(target.noBackupFilesDir, "historical-migrations-${UUID.randomUUID()}")
        check(root.mkdir())
        val aliases = mutableListOf<String>()
        val stores = mutableListOf<HistoryStore>()
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        var checks = 0
        fun expect(value: Boolean, label: String) { check(value) { "historical migration: $label" }; checks++ }
        fun schema(version: Int) = instrumentation.context.assets.open(
            "com.attentionstats.storage.AttentionDatabase/$version.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("database")
        }
        val current = schema(9).getJSONArray("entities").objects().associateBy { it.getString("tableName") }
        suspend fun rows(store: HistoryStore, table: String, columns: List<String>): List<List<String?>> =
            store.database.withReadTransaction {
                usePrepared("SELECT ${columns.joinToString(",") { "`$it`" }} FROM `$table` ORDER BY ${columns.indices.joinToString(",") { (it + 1).toString() }}") { query ->
                    buildList { while (query.step()) add(columns.indices.map { if (query.isNull(it)) null else query.getText(it) }) }
                }
            }
        suspend fun snapshot(store: HistoryStore) = current.mapValues { (table, entity) ->
            rows(store, table, entity.getJSONArray("fields").objects().map { it.getString("columnName") })
        }
        try {
            // Load SQLCipher through the product class loader, as in ordinary store opening.
            val freshFolder = File(root, "fresh").also { check(it.mkdir()) }
            val freshContext = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = freshFolder }
            val freshAlias = "attentionstats.historical-migrations.${UUID.randomUUID()}".also { aliases.add(it) }
            var fresh = HistoryStore.open(freshContext, freshAlias).also { stores.add(it) }
            val defaults = checkNotNull(fresh.database.data().diary())
            expect(!defaults.countPhotos && !defaults.measurePlayback && !defaults.usageEnabled &&
                !defaults.reminderEnabled && defaults.breakSeconds == 3, "fresh choices and timing defaults")
            expect(fresh.database.data().configurations().all { !it.enabled }, "fresh sources disabled")
            val freshCommitted = snapshot(fresh)
            fresh.close(); stores.remove(fresh)
            fresh = HistoryStore.open(freshContext, freshAlias).also { stores.add(it) }
            expect(snapshot(fresh) == freshCommitted, "fresh every-table reopen")
            fresh.close(); stores.remove(fresh)
            for (version in 1..9) {
                val folder = File(root, "v$version").also { check(it.mkdir()) }
                val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = folder }
                val alias = "attentionstats.historical-migrations.${UUID.randomUUID()}".also { aliases.add(it) }
                val path = File(folder, "attention-history.db")
                val keys = LocalKeyEnvelope(context, path, alias).open()
                // Match the production raw-key literal without creating an immutable secret String.
                val key = ByteArray(67)
                key[0] = 'x'.code.toByte(); key[1] = '\''.code.toByte(); key[66] = '\''.code.toByte()
                val alphabet = "0123456789abcdef"
                for (index in keys.raw.indices) {
                    val value = keys.raw[index].toInt() and 255
                    key[2 + index * 2] = alphabet[value ushr 4].code.toByte()
                    key[3 + index * 2] = alphabet[value and 15].code.toByte()
                }
                val driver = try { OwnedCipherDriver(key) } finally { key.fill(0); keys.raw.fill(0) }
                val historical = schema(version)
                val entities = historical.getJSONArray("entities").objects()
                val expected = linkedMapOf<String, List<Map<String, Any?>>>()
                try {
                    driver.open(path.path).use { connection ->
                        connection.exec("PRAGMA foreign_keys=ON")
                        connection.exec("BEGIN IMMEDIATE")
                        try {
                            for (entity in entities) {
                                val table = entity.getString("tableName")
                                connection.exec(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                                (entity.optJSONArray("indices") ?: JSONArray()).objects().forEach {
                                    connection.exec(it.getString("createSql").replace("\${TABLE_NAME}", table))
                                }
                                val singleton = table in setOf("StoreMetadata", "LiveOverlayConfiguration", "DiaryConfiguration", "ProtectionConfiguration")
                                val sourceIds = ReelApp.entries.map { it.sourceId }.sorted()
                                val count = if (table == "SourceConfiguration") sourceIds.size else if (singleton) 1 else 2
                                val values = (0 until count).map { index ->
                                    entity.getJSONArray("fields").objects().associate { field ->
                                        val column = field.getString("columnName")
                                        val value: Any? = when (column) {
                                            "id" -> if (singleton) 1 else "$table-$index"
                                            "generation" -> keys.generation
                                            "installationEpoch" -> keys.installationEpoch
                                            "lastRevision" -> 17
                                            "configurationRevision" -> 23
                                            "sourceId" -> if (table == "SourceConfiguration") sourceIds[index] else sourceIds.first()
                                            "surfaceEpisodeId" -> "SurfaceEpisode-$index"
                                            "appId" -> if (table == "ReelsBlockConfiguration" && index == 1) "tiktok" else "instagram"
                                            "surfaceId", "route" -> "home_video"
                                            "startMs", "occurredAtMs", "atMs" -> 1_000L + index
                                            "endMs" -> if (table == "CoverageInterval" && index == 1) null else 5_000L + index
                                            "durationMs" -> 4_000
                                            "breakSeconds" -> 7
                                            "observedZoneId" -> "Asia/Dhaka"
                                            "observedOffsetSeconds" -> 21600
                                            "enabled", "usageEnabled", "measurePlayback", "reminderEnabled",
                                            "showIcons", "allAppsToday", "includeRecognizedAds", "countPhotos" -> 0
                                            "paused", "grantLost", "onboardingSeen" -> 1
                                            "category" -> if (index == 0) "reel" else "photo"
                                            "surface" -> "viewer"
                                            "mediaKind" -> if (index == 0) "video" else "photo"
                                            "playbackMs", "dwellMs" -> -1
                                            "qualifiedWatch", "observedCompletion", "revisit" -> 0
                                            "kind" -> if (table == "AppUsageEvent") 1 else "break"
                                            "countKind" -> "encounter"
                                            "packageName" -> "example.synthetic.app"
                                            "usageEpoch", "epoch" -> "synthetic-usage-epoch"
                                            "choice" -> if (index == 0) "left" else ""
                                            "decision" -> "deny"
                                            "quality", "state" -> "synthetic"
                                            "reasonCode" -> if (index == 0) "synthetic 'quoted' reason — retained" else ""
                                            "document" -> "{\"groups\":[],\"windows\":[]}"
                                            "tableName" -> "Exposure"
                                            "recordId" -> "Exposure-$index"
                                            "originId" -> "00000000-0000-4000-8000-000000000001"
                                            else -> if (field.getString("affinity") == "INTEGER") 31L + index else "synthetic-$column-$index"
                                        }
                                        column to value
                                    }
                                }
                                values.forEach { row -> connection.exec("INSERT INTO `$table` (${row.keys.joinToString(",") { "`$it`" }}) VALUES (${row.values.joinToString(",") { sql(it) }})") }
                                expected[table] = values
                            }
                            val setup = historical.getJSONArray("setupQueries")
                            repeat(setup.length()) { connection.exec(setup.getString(it)) }
                            connection.exec("PRAGMA user_version=$version")
                            connection.exec("COMMIT")
                        } catch (failure: Exception) { connection.exec("ROLLBACK"); throw failure }
                    }
                } finally { driver.retire() }
                // A ciphertext header, not a plaintext SQLite fixture.
                expect(!path.inputStream().use { input -> ByteArray(16).also { check(input.read(it) == 16) } }.contentEquals("SQLite format 3\u0000".toByteArray()), "v$version encrypted header")
                var store = HistoryStore.open(context, alias).also { stores.add(it) }
                for ((table, values) in expected) {
                    val columns = values.first().keys.toList()
                    val original = values.map { row -> columns.map { row[it]?.toString() } }
                    // Stable synthetic IDs and singleton keys make this the same ordering as SQL.
                    expect(rows(store, table, columns) == original, "v$version $table original columns")
                    val oldColumns = columns.toSet()
                    for (field in current.getValue(table).getJSONArray("fields").objects().filter { it.getString("columnName") !in oldColumns }) {
                        val column = field.getString("columnName")
                        val default = field.getString("defaultValue").removeSurrounding("'")
                        expect(rows(store, table, listOf(column)).all { it == listOf(default) }, "v$version $table.$column migrated default")
                    }
                }
                for (table in current.keys - expected.keys - "DiaryConfiguration") {
                    expect(rows(store, table, listOf(current.getValue(table).getJSONArray("fields").getJSONObject(0).getString("columnName"))).isEmpty(), "v$version new $table empty")
                }
                val data = store.database.data()
                expect(data.dayCount("instagram", 0, 10_000) == 2L, "v$version retained encounters query")
                expect(data.playbackTotals(0, 10_000).isEmpty(), "v$version unknown playback not fabricated")
                expect(data.diary()?.measurePlayback == false, "v$version playback consent off")
                val diary = checkNotNull(data.diary())
                // Existing rows receive SQL migration defaults; a newly initialized row uses Kotlin defaults.
                expect(diary.countPhotos == (version in 4..7), "v$version photo choice/default")
                expect(diary.breakSeconds == (if (version < 4) 3 else if (version < 6) 10 else 7), "v$version break choice/default")
                expect(data.configurations().all { !it.enabled && it.paused }, "v$version source choices retained")
                expect(data.blockers().all { !it.enabled }, "v$version blockers remain disabled")
                expect(data.metadata()?.generation == keys.generation && data.metadata()?.installationEpoch == keys.installationEpoch, "v$version key identity")
                val committed = snapshot(store)
                store.close(); stores.remove(store)
                store = HistoryStore.open(context, alias).also { stores.add(it) }
                expect(snapshot(store) == committed, "v$version every-table reopen")
                store.close(); stores.remove(store)
            }
            "PASS: $checks isolated historical migration checks; populated versions 1..9 to production schema 9 and fresh defaults; original columns, added defaults, queries, keys and every-table reopen; no real history changes"
        } finally {
            stores.asReversed().forEach { it.close() }
            aliases.forEach { if (keystore.containsAlias(it)) keystore.deleteEntry(it) }
            check(aliases.none { keystore.containsAlias(it) })
            check(root.parentFile?.canonicalFile == target.noBackupFilesDir.canonicalFile)
            check(root.deleteRecursively() && !root.exists())
        }
    }
}
