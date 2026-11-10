package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.ContextWrapper
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import com.attentionstats.backup.ArchiveEnvelope
import com.attentionstats.backup.LogicalArchive
import com.attentionstats.storage.Exposure
import com.attentionstats.storage.HistoryStore
import com.attentionstats.storage.SurfaceEpisode
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID

/** Local source-key loss and recovery with synthetic stores; never invokes Android transport. */
internal object KeyLossRecoveryVerification {
    fun run(instrumentation: Instrumentation): Int = runBlocking {
        val target = instrumentation.targetContext
        val root = File(target.noBackupFilesDir, "key-loss-test-${UUID.randomUUID()}")
        check(root.mkdir())
        val stores = mutableListOf<HistoryStore>()
        val aliases = mutableListOf<String>()
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        var checks = 0
        fun expect(value: Boolean) { check(value) { "key-loss check ${checks + 1}" }; checks++ }
        fun context(name: String) = object : ContextWrapper(target) {
            private val folder = File(root, name).also { check(it.mkdir()) }
            override fun getNoBackupFilesDir() = folder
        }
        suspend fun open(context: ContextWrapper, alias: String): HistoryStore =
            HistoryStore.open(context, alias).also { stores.add(it) }
        fun alias() = "attentionstats.keyloss.${UUID.randomUUID()}".also { aliases.add(it) }
        suspend fun origin(store: HistoryStore): String = store.database.withReadTransaction {
            usePrepared("SELECT originId FROM FactOrigin WHERE tableName = 'Exposure' AND recordId = 'key-loss-fact'") {
                check(it.step()); it.getText(0)
            }
        }
        try {
            val sourceContext = context("source")
            val sourceAlias = alias()
            val source = open(sourceContext, sourceAlias)
            source.database.withWriteTransaction {
                source.database.data().insertEpisode(SurfaceEpisode("key-loss-episode", "instagram", "home_video",
                    1_000, 5_000, 1, "synthetic"))
                source.database.data().insertExposure(Exposure("key-loss-fact", "key-loss-episode", "instagram",
                    1_000, 1_000, 1, 1, "synthetic", 1, "synthetic-key-loss", "UTC", 0, "reel", mediaKind = "video"))
            }
            val originalOrigin = checkNotNull(source.database.data().metadata()).installationEpoch
            val plaintext = LogicalArchive.export(source) {}
            val archive = try {
                ArchiveEnvelope.write(plaintext, ArchiveEnvelope.packageCredential(), profile = ArchiveEnvelope.PUBLIC_PACKAGE_PROFILE)
            } finally { plaintext.fill(0) }
            try {
                source.close(); stores.remove(source)
                val databaseFile = File(sourceContext.noBackupFilesDir, "attention-history.db")
                val wrapperFile = File(sourceContext.noBackupFilesDir, "history-key-v1")
                fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                val originalDatabase = digest(databaseFile)
                val originalWrapper = digest(wrapperFile)
                keystore.deleteEntry(sourceAlias)
                expect(!keystore.containsAlias(sourceAlias))
                expect(runCatching { open(sourceContext, sourceAlias) }.isFailure)
                expect(digest(databaseFile).contentEquals(originalDatabase))
                expect(digest(wrapperFile).contentEquals(originalWrapper))
                expect(!keystore.containsAlias(sourceAlias))

                val destinationContext = context("destination")
                val destinationAlias = alias()
                var destination = open(destinationContext, destinationAlias)
                val localOrigin = checkNotNull(destination.database.data().metadata()).installationEpoch
                expect(localOrigin != originalOrigin)
                val staging = open(context("staging"), alias())
                // The public package credential is sufficient after the original device key is gone.
                val recovered = ArchiveEnvelope.read(archive, ArchiveEnvelope.packageCredential(),
                    expectedProfile = ArchiveEnvelope.PUBLIC_PACKAGE_PROFILE)
                try { LogicalArchive.stage(staging, recovered) {} } finally { recovered.fill(0) }
                expect(staging.database.data().dayCount("instagram", 0, 10_000) == 1L)
                expect(origin(staging) == originalOrigin)
                destination.database.withWriteTransaction { LogicalArchive.replace(destination, staging) {} }
                expect(destination.database.data().dayCount("instagram", 0, 10_000) == 1L)
                expect(origin(destination) == originalOrigin)
                destination.close(); stores.remove(destination)
                destination = open(destinationContext, destinationAlias)
                expect(destination.database.data().videoTotals(0, 10_000).single().count == 1L)
                expect(origin(destination) == originalOrigin)
                expect(checkNotNull(destination.database.data().metadata()).installationEpoch == localOrigin)
                expect(destination.database.data().configurations().none { it.enabled })

                val corrupt = archive.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
                try {
                    expect(runCatching {
                        val invalid = ArchiveEnvelope.read(corrupt, ArchiveEnvelope.packageCredential(),
                            expectedProfile = ArchiveEnvelope.PUBLIC_PACKAGE_PROFILE)
                        try { LogicalArchive.stage(staging, invalid) {} } finally { invalid.fill(0) }
                        destination.database.withWriteTransaction { LogicalArchive.replace(destination, staging) {} }
                    }.isFailure)
                } finally { corrupt.fill(0) }
                expect(destination.database.data().dayCount("instagram", 0, 10_000) == 1L)
                expect(origin(destination) == originalOrigin)
            } finally { archive.fill(0) }
            checks
        } finally {
            stores.asReversed().forEach { it.close() }
            aliases.forEach { if (keystore.containsAlias(it)) keystore.deleteEntry(it) }
            check(root.parentFile?.canonicalFile == target.noBackupFilesDir.canonicalFile)
            check(root.deleteRecursively())
        }
    }
}
