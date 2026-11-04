package com.attentionstats.backup

import android.content.Context
import android.content.ContextWrapper
import com.attentionstats.storage.HistoryStore
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** Job state is private to the serialized history owner; no external filename selects a local path. */
internal class ArchiveOwner(private val context: Context) {
    data class Staged(val token: String, val history: HistoryStore, val folder: File, val alias: String,
        val summary: LogicalArchive.Summary, val historyRevision: Long, val configurationRevision: Long)
    var pending: Staged? = null
        private set

    /** Called once before opening live history, while this owner has no active job. */
    fun cleanupAbandoned() {
        check(pending == null)
        val names = Regex("archive-stage-[0-9a-f-]{36}")
        val leftovers = context.noBackupFilesDir.listFiles()?.filter { it.isDirectory && names.matches(it.name) } ?: emptyList()
        require(leftovers.size <= 32)
        leftovers.forEach { folder -> cleanup(folder, "attentionstats.archive.stage.${folder.name.removePrefix("archive-stage-")}") }
    }

    suspend fun preview(live: HistoryStore, archive: ByteArray, key: String, profile: Int = 1,
        cancel: () -> Unit): Staged {
        clear()
        val plain = ArchiveEnvelope.read(archive, key, cancel = cancel, expectedProfile = profile)
        val token = UUID.randomUUID().toString()
        val folder = File(context.noBackupFilesDir, "archive-stage-$token")
        check(folder.mkdir())
        val alias = "attentionstats.archive.stage.$token"
        val isolated = object : ContextWrapper(context) { override fun getNoBackupFilesDir(): File = folder }
        var stage: HistoryStore? = null
        try {
            stage = HistoryStore.open(isolated, alias)
            val summary = LogicalArchive.stage(stage, plain, cancel)
            val metadata = checkNotNull(live.database.data().metadata())
            // Historical choices may be viewed in the archive, but cannot activate on this device.
            val choices = stage.database.data().protection()
            if (choices != null) com.attentionstats.protection.ScheduleOwner.decode(choices.document)
                .validate(emptySet())
            cancel()
            return Staged(token, stage, folder, alias, summary, metadata.lastRevision, metadata.configurationRevision).also { pending = it }
        } catch (failure: Exception) {
            stage?.close(); cleanup(folder, alias); throw failure
        } finally { plain.fill(0) }
    }

    fun clear() {
        val current = pending ?: return
        pending = null
        try { current.history.close() } finally { cleanup(current.folder, current.alias) }
    }
    private fun cleanup(folder: File, alias: String) {
        check(folder.parentFile?.canonicalFile == context.noBackupFilesDir.canonicalFile && folder.name.startsWith("archive-stage-"))
        folder.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); if (containsAlias(alias)) deleteEntry(alias) }
    }
}
