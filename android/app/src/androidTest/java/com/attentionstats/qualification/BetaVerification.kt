package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.ContextWrapper
import androidx.room3.withWriteTransaction
import androidx.room3.withReadTransaction
import com.attentionstats.backup.ArchiveEnvelope
import com.attentionstats.backup.ArchiveOwner
import com.attentionstats.backup.LogicalArchive
import com.attentionstats.storage.Exposure
import com.attentionstats.storage.HistoryStore
import com.attentionstats.storage.SurfaceEpisode
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** Physical-device archive checks use only task-owned encrypted stores and synthetic history. */
internal object BetaVerification {
    fun run(instrumentation: Instrumentation): Int = runBlocking {
        val context = instrumentation.targetContext
        val root = File(context.noBackupFilesDir, "beta-verification-${UUID.randomUUID()}")
        check(root.mkdir())
        val aliases = mutableListOf<String>()
        val stores = mutableListOf<HistoryStore>()
        val preferences = mutableListOf<String>()
        var checks = 0
        fun expect(value: Boolean) { check(value) { "beta check ${checks + 1}" }; checks++ }
        suspend fun isolated(name: String): HistoryStore {
            val folder = File(root, name); check(folder.mkdir())
            val alias = "attentionstats.beta.$name.${UUID.randomUUID()}"; aliases.add(alias)
            return HistoryStore.open(object : ContextWrapper(context) { override fun getNoBackupFilesDir() = folder }, alias).also { stores.add(it) }
        }
        try {
            val live = isolated("source")
            val stage = isolated("staging")
            val dao = live.database.data()
            live.database.withWriteTransaction {
                dao.insertEpisode(SurfaceEpisode("synthetic-episode", "instagram", "home_video", 1_000, 5_000, 1, "synthetic"))
                for ((index, category) in listOf("reel", "photo", "ad").withIndex()) {
                    dao.insertExposure(Exposure("synthetic-$index", "synthetic-episode", "instagram", 1_000L + index,
                        1_000L + index, index + 1L, 1, "synthetic", 1, "synthetic-archive-marker",
                        "UTC", 0, category, mediaKind = if (category == "reel") "video" else "photo"))
                }
            }
            val plain = LogicalArchive.export(live) {}
            val rawKey = ArchiveEnvelope.randomBytes(32)
            val key = try { ArchiveEnvelope.encodeCredential(rawKey) } finally { rawKey.fill(0) }
            val archive = ArchiveEnvelope.write(plain, key)
            expect(!archive.toString(Charsets.ISO_8859_1).contains("synthetic-archive-marker"))
            expect(ArchiveEnvelope.read(archive, key).contentEquals(plain))
            val publicArchive = ArchiveEnvelope.write(plain, ArchiveEnvelope.packageCredential(),
                profile = ArchiveEnvelope.PUBLIC_PACKAGE_PROFILE)
            // No installation key or remembered credential is involved: the public package alone suffices.
            val publicCredential = ArchiveEnvelope.encodeCredential(java.security.MessageDigest.getInstance("SHA-256")
                .digest("attention.stats".toByteArray(Charsets.US_ASCII)))
            expect(publicArchive[12].toInt() == ArchiveEnvelope.PUBLIC_PACKAGE_PROFILE)
            expect(ArchiveEnvelope.read(publicArchive, publicCredential,
                expectedProfile = ArchiveEnvelope.PUBLIC_PACKAGE_PROFILE).contentEquals(plain))
            expect(runCatching { ArchiveEnvelope.read(publicArchive, publicCredential) }.isFailure)
            val relabelled = publicArchive.copyOf().also { it[12] = 1 }
            expect(runCatching { ArchiveEnvelope.read(relabelled, publicCredential) }.isFailure)
            val publicCorrupt = publicArchive.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            expect(runCatching { ArchiveEnvelope.read(publicCorrupt, publicCredential,
                expectedProfile = ArchiveEnvelope.PUBLIC_PACKAGE_PROFILE) }.isFailure)
            publicCorrupt.fill(0); relabelled.fill(0)
            val wrong = ArchiveEnvelope.encodeCredential(ArchiveEnvelope.randomBytes(32))
            expect(runCatching { ArchiveEnvelope.read(archive, wrong) }.isFailure)
            expect(runCatching { ArchiveEnvelope.read(archive.copyOf(archive.size - 1), key) }.isFailure)
            expect(runCatching { ArchiveEnvelope.read(archive + byteArrayOf(0), key) }.isFailure)
            val corrupt = archive.copyOf().also { it[it.lastIndex - 10] = (it[it.lastIndex - 10].toInt() xor 1).toByte() }
            expect(runCatching { ArchiveEnvelope.read(corrupt, key) }.isFailure)
            val summary = LogicalArchive.stage(stage, plain) {}
            expect(summary.rows >= 10)
            expect(stage.database.data().dayCount("instagram", 0, 10_000) == 3L)
            expect(stage.database.data().photoTotals(0, 10_000).single().count == 2L)
            expect(stage.database.data().videoTotals(0, 10_000).single().count == 1L)
            val before = dao.dayCount("instagram", 0, 10_000)
            expect(runCatching { live.database.withWriteTransaction {
                LogicalArchive.replace(live, stage) {}
                error("injected rollback")
            } }.isFailure)
            expect(dao.dayCount("instagram", 0, 10_000) == before)
            // A separate destination proves successful replacement and cancelled replacement
            // preserve the correct generation, rather than just copying a store onto itself.
            val destination = isolated("destination")
            val destinationDao = destination.database.data()
            destination.database.withWriteTransaction {
                destinationDao.insertEpisode(SurfaceEpisode("old-episode", "instagram", "home_video", 1_000, 5_000, 1, "synthetic"))
                destinationDao.insertExposure(Exposure("old-only", "old-episode", "instagram", 1_000,
                    1_000, 1, 1, "synthetic", 1, "synthetic-old-generation", "UTC", 0, "photo", mediaKind = "photo"))
            }
            var replacementChecks = 0
            expect(runCatching { destination.database.withWriteTransaction {
                LogicalArchive.replace(destination, stage) { check(++replacementChecks < summary.rows + 5) }
            } }.isFailure)
            expect(destinationDao.dayCount("instagram", 0, 10_000) == 1L)
            expect(destinationDao.photoTotals(0, 10_000).single().count == 1L)
            destination.database.withWriteTransaction { LogicalArchive.replace(destination, stage) {} }
            expect(destinationDao.dayCount("instagram", 0, 10_000) == 3L)
            expect(destinationDao.photoTotals(0, 10_000).single().count == 2L)
            expect(destinationDao.videoTotals(0, 10_000).single().count == 1L)
            suspend fun origin(store: HistoryStore, id: String): String = store.database.withReadTransaction {
                usePrepared("SELECT originId FROM FactOrigin WHERE tableName = 'Exposure' AND recordId = ?") {
                    it.bindText(1, id); check(it.step()); it.getText(0)
                }
            }
            val sourceOrigin = checkNotNull(dao.metadata()).installationEpoch
            expect(origin(destination, "synthetic-0") == sourceOrigin)
            destinationDao.insertExposure(Exposure("new-local", "synthetic-episode", "instagram", 1_005,
                1_005, 4, 1, "synthetic", 1, "synthetic", "UTC", 0, "reel", mediaKind = "video"))
            val nextStage = isolated("reexport")
            val reexported = LogicalArchive.export(destination) {}
            try { LogicalArchive.stage(nextStage, reexported) {} } finally { reexported.fill(0) }
            expect(origin(nextStage, "synthetic-0") == sourceOrigin)
            expect(origin(nextStage, "new-local") == checkNotNull(destinationDao.metadata()).installationEpoch)
            expect(origin(nextStage, "new-local") != sourceOrigin)
            expect(runCatching { LogicalArchive.stage(stage, plain.copyOf(plain.size - 1)) {} }.isFailure)
            expect(stage.database.data().dayCount("instagram", 0, 10_000) == 3L)
            var cancellationChecks = 0
            expect(runCatching { LogicalArchive.stage(stage, plain) { check(++cancellationChecks < 5) } }.isFailure)
            expect(stage.database.data().dayCount("instagram", 0, 10_000) == 3L)
            val owner = ArchiveOwner(context)
            try {
                val preview = owner.preview(live, archive, key) {}
                expect(preview.summary.rows == summary.rows)
                expect(dao.dayCount("instagram", 0, 10_000) == before)
                owner.clear()
                expect(!preview.folder.exists())
                val publicPreview = owner.preview(live, publicArchive, publicCredential,
                    profile = ArchiveEnvelope.PUBLIC_PACKAGE_PROFILE) {}
                expect(publicPreview.summary.rows == summary.rows)
                expect(dao.dayCount("instagram", 0, 10_000) == before)
                owner.clear()
                expect(!publicPreview.folder.exists())
            } finally { owner.clear() }
            val agentFiles = File(root, "agent-files").apply { check(mkdir()) }
            val agentNoBackup = File(root, "agent-private").apply { check(mkdir()) }
            val preferencePrefix = "beta-system-${UUID.randomUUID()}-"
            val agentContext = object : ContextWrapper(context) {
                override fun getFilesDir() = agentFiles
                override fun getNoBackupFilesDir() = agentNoBackup
                override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                    val scoped = preferencePrefix + name
                    if (scoped !in preferences) preferences.add(scoped)
                    return context.getSharedPreferences(scoped, mode)
                }
            }
            val agent = object : com.attentionstats.backup.SystemBackupAgent() {
                init { attachBaseContext(agentContext) }
            }
            val input = File(root, "agent-input").apply { writeBytes(publicArchive) }
            val expectedFile = File(agentFiles, com.attentionstats.backup.SystemBackupState.RELATIVE_FILE)
            fun deliver(file: File = expectedFile, size: Long = publicArchive.size.toLong()) {
                android.os.ParcelFileDescriptor.open(input, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use {
                    agent.onRestoreFile(it, size, file, android.app.backup.BackupAgent.TYPE_FILE, 0, 0)
                }
            }
            expect(!com.attentionstats.backup.SystemBackupState.enabled(agent))
            deliver()
            val pending = com.attentionstats.backup.SystemBackupState.pending(agent)
            expect(pending.readBytes().contentEquals(publicArchive))
            expect(!com.attentionstats.backup.SystemBackupState.enabled(agent))
            expect(runCatching { deliver(File(agentFiles, "unexpected")) }.isFailure)
            expect(runCatching { deliver(size = ArchiveEnvelope.MAX_CIPHER + 1L) }.isFailure)
            input.writeBytes(publicArchive.copyOf(publicArchive.size - 1))
            expect(runCatching { deliver() }.isFailure)
            expect(pending.readBytes().contentEquals(publicArchive))
            input.writeBytes(publicArchive.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
            expect(runCatching { deliver() }.isFailure)
            expect(pending.readBytes().contentEquals(publicArchive))
            com.attentionstats.backup.SystemBackupState.clearPending(agent)
            expect(!pending.exists())
            plain.fill(0); archive.fill(0); corrupt.fill(0); publicArchive.fill(0)
            checks
        } finally {
            stores.asReversed().forEach { it.close() }
            preferences.forEach { context.deleteSharedPreferences(it) }
            check(root.parentFile?.canonicalFile == context.noBackupFilesDir.canonicalFile)
            root.deleteRecursively()
            val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            aliases.forEach { if (keystore.containsAlias(it)) keystore.deleteEntry(it) }
        }
    }
}
