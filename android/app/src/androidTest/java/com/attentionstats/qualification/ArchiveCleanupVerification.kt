package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.ContextWrapper
import com.attentionstats.backup.ArchiveEnvelope
import com.attentionstats.backup.ArchiveOwner
import com.attentionstats.backup.LogicalArchive
import com.attentionstats.storage.HistoryStore
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** Real filesystem denial, isolated encrypted stores/keys; never opens the live history. */
internal object ArchiveCleanupVerification {
    fun run(instrumentation: Instrumentation): String = runBlocking {
        val target = instrumentation.targetContext
        val root = File(target.noBackupFilesDir, "archive-cleanup-test-${UUID.randomUUID()}")
        check(root.mkdir())
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = root }
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val aliases = mutableListOf<String>()
        val stores = mutableListOf<HistoryStore>()
        var checks = 0
        fun expect(value: Boolean) { check(value) { "archive cleanup check ${checks + 1}" }; checks++ }
        fun stageAlias(token: String) = "attentionstats.archive.stage.$token".also { aliases.add(it) }
        suspend fun open(folder: File, alias: String): HistoryStore {
            val isolated = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = folder }
            return HistoryStore.open(isolated, alias).also { stores.add(it) }
        }
        try {
            val untouched = File(root, "unrelated-sentinel").also { it.writeText("synthetic") }
            val token = UUID.randomUUID().toString()
            val abandoned = File(root, "archive-stage-$token").also { check(it.mkdir()) }
            val alias = stageAlias(token)
            val store = open(abandoned, alias)
            store.close(); stores.remove(store)
            expect(keys.containsAlias(alias))
            val sentinel = File(abandoned, "synthetic-sentinel").also { it.writeText("synthetic") }
            check(abandoned.setWritable(false, true))
            expect(!sentinel.delete()) // Establish actual denial before testing production cleanup.
            val owner = ArchiveOwner(context)
            expect(runCatching { owner.cleanupAbandoned() }.isFailure)
            expect(abandoned.exists())
            expect(!keys.containsAlias(alias))
            check(abandoned.setWritable(true, true))
            owner.cleanupAbandoned()
            expect(!abandoned.exists())
            expect(untouched.readText() == "synthetic")

            val liveFolder = File(root, "synthetic-live").also { check(it.mkdir()) }
            val liveAlias = "attentionstats.archive-cleanup.${UUID.randomUUID()}".also { aliases.add(it) }
            val live = open(liveFolder, liveAlias)
            val original = checkNotNull(live.database.data().metadata())
            val plain = LogicalArchive.export(live) {}
            val archive = try {
                ArchiveEnvelope.write(plain, ArchiveEnvelope.packageCredential(), profile = ArchiveEnvelope.PUBLIC_PACKAGE_PROFILE)
            } finally { plain.fill(0) }
            try {
                val staged = owner.preview(live, archive, ArchiveEnvelope.packageCredential(),
                    profile = ArchiveEnvelope.PUBLIC_PACKAGE_PROFILE) {}
                aliases.add(staged.alias)
                expect(owner.pending === staged && keys.containsAlias(staged.alias))
                val retained = File(staged.folder, "synthetic-sentinel").also { it.writeText("synthetic") }
                // Close before denying directory writes so this specifically tests file cleanup.
                staged.history.close()
                check(staged.folder.setWritable(false, true))
                expect(!retained.delete())
                expect(runCatching { owner.clear() }.isFailure)
                expect(owner.pending == null) // The cancelled token cannot authorize an import.
                expect(staged.folder.exists())
                expect(!keys.containsAlias(staged.alias))
                check(staged.folder.setWritable(true, true))
                owner.clear()
                expect(owner.pending == null && !staged.folder.exists())
                owner.clear()
                expect(owner.pending == null)
                expect(live.database.data().metadata() == original)
                expect(untouched.readText() == "synthetic")
            } finally { archive.fill(0) }
            "PASS: $checks isolated archive cleanup checks; real deletion denial, key retirement, retained retry and successful cleanup; no real history changes"
        } finally {
            // Restore only permissions on this UUID-owned synthetic tree, including failed trials.
            root.walkTopDown().filter { it.isDirectory }.forEach { it.setWritable(true, true) }
            stores.asReversed().forEach { it.close() }
            aliases.forEach { if (keys.containsAlias(it)) keys.deleteEntry(it) }
            check(aliases.none { keys.containsAlias(it) })
            check(root.parentFile?.canonicalFile == target.noBackupFilesDir.canonicalFile)
            check(root.deleteRecursively() && !root.exists())
        }
    }
}
