package com.attentionstats.backup

import android.util.JsonReader
import android.util.JsonToken
import androidx.room3.PooledConnection
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import com.attentionstats.storage.HistoryStore
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.StringReader

/** Explicit schema-9 snapshot; stable fact IDs/origins survive replacement and re-export. */
internal object LogicalArchive {
    val tables = listOf("SourceConfiguration", "LiveOverlayConfiguration", "ConsentRecord", "SurfaceEpisode",
        "Exposure", "CoverageInterval", "DiaryConfiguration", "AppUsageEvent", "ReelsTimer",
        "ReelsBlockConfiguration", "InterventionEvent", "ProtectionConfiguration")
    private const val MAX_RECORD = 262_144
    private const val MAX_ROWS = 100_000L
    private val factTables = setOf("ConsentRecord", "SurfaceEpisode", "Exposure", "CoverageInterval",
        "AppUsageEvent", "ReelsTimer", "InterventionEvent")
    data class Column(val name: String, val integer: Boolean, val nullable: Boolean)
    data class Summary(val rows: Long, val createdAtMs: Long)

    private suspend fun columns(connection: PooledConnection, table: String): List<Column> =
        connection.usePrepared("PRAGMA table_info(`$table`)") { statement -> buildList {
            while (statement.step()) {
                val type = statement.getText(2)
                require(type == "INTEGER" || type == "TEXT")
                add(Column(statement.getText(1), type == "INTEGER", statement.getLong(3) == 0L))
            }
        } }

    suspend fun export(history: HistoryStore, cancel: () -> Unit): ByteArray = history.database.withReadTransaction {
        val output = ByteArrayOutputStream()
        val stream = DataOutputStream(output)
        fun record(kind: Int, value: JSONObject) {
            cancel()
            val bytes = value.toString().toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_RECORD && output.size().toLong() + bytes.size + 5 <= ArchiveEnvelope.MAX_PLAIN)
            stream.writeByte(kind); stream.writeInt(bytes.size); stream.write(bytes)
        }
        val counts = JSONObject()
        var total = 0L
        for (table in tables) usePrepared("SELECT COUNT(*) FROM `$table`") { statement ->
            check(statement.step()); val count = statement.getLong(0)
            total = Math.addExact(total, count); require(total <= MAX_ROWS)
            counts.put(table, count.toString())
        }
        val localOrigin = checkNotNull(history.database.data().metadata()).installationEpoch
        record(1, JSONObject().put("format", "attention-stats-history").put("version", "2").put("schema", "9")
            .put("createdAtMs", System.currentTimeMillis().toString()).put("entities", counts).put("rows", total.toString()))
        for (table in tables) {
            val columns = columns(this, table)
            val facts = table in factTables
            val query = if (facts) "SELECT t.*, o.originId FROM `$table` t LEFT JOIN FactOrigin o ON o.tableName = '$table' AND o.recordId = t.id ORDER BY t.id"
                else "SELECT * FROM `$table` ORDER BY `${columns.first().name}`"
            usePrepared(query) { statement ->
                while (statement.step()) {
                    val values = JSONObject()
                    columns.forEachIndexed { index, column ->
                        values.put(column.name, if (statement.isNull(index)) JSONObject.NULL
                            else if (column.integer) statement.getLong(index).toString() else statement.getText(index))
                    }
                    // Live grant state and ingestion cursors never travel with a portable archive.
                    if (table == "DiaryConfiguration") {
                        values.put("usageEnabled", "0").put("usageEpoch", "").put("queryThroughMs", "0")
                            .put("effectiveAtMs", "0").put("grantLost", "0").put("measurePlayback", "0")
                    }
                    val row = JSONObject().put("table", table).put("values", values)
                    if (facts) row.put("originId", if (statement.isNull(columns.size)) localOrigin else statement.getText(columns.size))
                    record(2, row)
                }
            }
        }
        record(3, JSONObject().put("rows", total.toString()))
        output.toByteArray()
    }

    suspend fun stage(history: HistoryStore, plaintext: ByteArray, cancel: () -> Unit): Summary {
        require(plaintext.size <= ArchiveEnvelope.MAX_PLAIN)
        return history.database.withWriteTransaction {
            val columns = tables.associateWith { columns(this, it) }
            usePrepared("DELETE FROM FactOrigin") { it.step() }
            tables.asReversed().forEach { usePrepared("DELETE FROM `$it`") { query -> query.step() } }
            val input = DataInputStream(ByteArrayInputStream(plaintext))
            fun record(expected: Int): JSONObject {
                cancel(); require(input.readUnsignedByte() == expected)
                val size = input.readInt(); require(size in 2..MAX_RECORD && size <= input.available())
                val bytes = ByteArray(size); input.readFully(bytes)
                val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                return strictJson(text)
            }
            val manifest = record(1)
            exact(manifest, setOf("format", "version", "schema", "createdAtMs", "entities", "rows"))
            require(manifest.getString("format") == "attention-stats-history" && manifest.getString("version") == "2" && manifest.getString("schema") == "9")
            val expectedRows = decimal(manifest, "rows"); require(expectedRows in 0..MAX_ROWS)
            val createdAt = decimal(manifest, "createdAtMs"); require(createdAt >= 0)
            val counts = manifest.getJSONObject("entities"); exact(counts, tables.toSet())
            require(tables.sumOf { decimal(counts, it).also { value -> require(value in 0..MAX_ROWS) } } == expectedRows)
            val actual = tables.associateWith { 0L }.toMutableMap()
            var lastTable = 0
            repeat(expectedRows.toInt()) {
                val row = record(2)
                val table = row.getString("table"); val index = tables.indexOf(table)
                require(index >= lastTable && index >= 0); lastTable = index
                exact(row, if (table in factTables) setOf("table", "values", "originId") else setOf("table", "values"))
                val values = row.getJSONObject("values"); val definition = columns.getValue(table)
                exact(values, definition.map { it.name }.toSet())
                if (table in setOf("DiaryConfiguration", "LiveOverlayConfiguration", "ProtectionConfiguration")) require(values.getString("id") == "1")
                val flags = setOf("enabled", "paused", "showIcons", "allAppsToday", "revisit", "usageEnabled", "grantLost", "reminderEnabled",
                    "onboardingSeen", "includeRecognizedAds", "measurePlayback", "countPhotos", "qualifiedWatch", "observedCompletion")
                definition.filter { it.name in flags }.forEach { require(decimal(values, it.name) in 0..1) }
                if (table == "Exposure") {
                    require(values.getString("category") in setOf("reel", "photo", "post", "ad", "unclassified"))
                    require(values.getString("mediaKind") in setOf("video", "photo", "unknown"))
                    require(decimal(values, "playbackMs") in -1..86_400_000 && decimal(values, "dwellMs") in -1..86_400_000)
                }
                val names = definition.joinToString(",") { "`${it.name}`" }
                val slots = definition.joinToString(",") { "?" }
                usePrepared("INSERT INTO `$table` ($names) VALUES ($slots)") { query ->
                    definition.forEachIndexed { columnIndex, column ->
                        val value = values.get(column.name)
                        when {
                            value == JSONObject.NULL -> { require(column.nullable); query.bindNull(columnIndex + 1) }
                            column.integer -> query.bindLong(columnIndex + 1, decimal(values, column.name))
                            else -> {
                                require(value is String && value.length <= if (table == "ProtectionConfiguration") 131_072 else 4096)
                                query.bindText(columnIndex + 1, value)
                            }
                        }
                    }
                    query.step()
                }
                if (table in factTables) {
                    val origin = row.getString("originId")
                    require(java.util.UUID.fromString(origin).toString() == origin)
                    usePrepared("INSERT INTO FactOrigin (tableName, recordId, originId) VALUES (?, ?, ?)") { query ->
                        query.bindText(1, table); query.bindText(2, values.getString("id")); query.bindText(3, origin); query.step()
                    }
                }
                actual[table] = actual.getValue(table) + 1
            }
            val end = record(3); exact(end, setOf("rows"))
            require(decimal(end, "rows") == expectedRows && input.available() == 0)
            tables.forEach { require(actual.getValue(it) == decimal(counts, it)) }
            require(actual.getValue("DiaryConfiguration") == 1L && actual.getValue("LiveOverlayConfiguration") == 1L)
            usePrepared("PRAGMA foreign_key_check") { query -> require(!query.step()) }
            Summary(expectedRows, createdAt)
        }
    }

    /** Copy validated staging rows under one live transaction. A failure rolls back every deletion. */
    suspend fun replace(live: HistoryStore, staged: HistoryStore, cancel: () -> Unit) {
        // Re-serialize the validated store, so no provider/archive bytes or SQL identifiers are trusted at commit.
        val snapshot = export(staged, cancel)
        try { stage(live, snapshot, cancel) } finally { snapshot.fill(0) }
    }

    private fun decimal(value: JSONObject, name: String): Long {
        val text = value.get(name)
        require(text is String && Regex("-?(0|[1-9][0-9]{0,18})").matches(text) && text != "-0")
        return text.toLong()
    }
    private fun exact(value: JSONObject, expected: Set<String>) { require(value.keys().asSequence().toSet() == expected) }

    /** Android JSONObject alone accepts duplicate keys; reject duplicates, numbers and excess depth first. */
    private fun strictJson(text: String): JSONObject {
        JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            fun read(depth: Int): Any {
                require(depth <= 8)
                return when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> {
                        reader.beginObject(); val result = JSONObject(); val keys = mutableSetOf<String>()
                        while (reader.hasNext()) {
                            val name = reader.nextName(); require(name.length <= 64 && keys.add(name) && keys.size <= 64)
                            result.put(name, read(depth + 1))
                        }
                        reader.endObject(); result
                    }
                    JsonToken.BEGIN_ARRAY -> {
                        reader.beginArray(); val result = JSONArray()
                        while (reader.hasNext()) { require(result.length() < 1000); result.put(read(depth + 1)) }
                        reader.endArray(); result
                    }
                    JsonToken.STRING -> reader.nextString().also { require(it.length <= 131_072) }
                    JsonToken.NULL -> { reader.nextNull(); JSONObject.NULL }
                    else -> throw IllegalArgumentException("Unsupported archive value")
                }
            }
            val value = read(0); require(value is JSONObject && reader.peek() == JsonToken.END_DOCUMENT)
            return value
        }
    }
}
