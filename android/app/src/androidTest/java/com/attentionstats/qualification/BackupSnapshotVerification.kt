package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.ContextWrapper
import com.attentionstats.backup.SystemBackupState
import com.attentionstats.backup.SystemBackupSnapshot
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Isolated files and preferences only; never requests transport or opens live history. */
internal object BackupSnapshotVerification {
    fun run(instrumentation: Instrumentation): String {
        val target = instrumentation.targetContext
        val token = UUID.randomUUID().toString()
        val root = File(target.noBackupFilesDir, "backup-snapshot-test-$token")
        check(root.mkdir())
        val preference = "backup-snapshot-test-$token"
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = root
            override fun getNoBackupFilesDir() = File(root, "private").also { check(it.mkdirs() || it.isDirectory) }
            override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences(preference, mode)
        }
        val snapshot = File(root, SystemBackupState.RELATIVE_FILE)
        val prefs = context.getSharedPreferences("system-backup", 0)
        var checks = 0
        fun expect(value: Boolean) { check(value) { "backup snapshot check ${checks + 1}" }; checks++ }
        try {
            check(snapshot.parentFile!!.mkdir())
            val sentinel = File(snapshot.parentFile, "unrelated-sentinel").also { it.writeText("synthetic") }
            check(prefs.edit().putBoolean("enabled", false).commit())
            snapshot.writeText("synthetic stale snapshot")
            expect(snapshot.exists())
            SystemBackupState.initialize(context)
            expect(!snapshot.exists())
            expect(!SystemBackupState.enabled(context))
            expect(sentinel.readText() == "synthetic")

            snapshot.writeText("synthetic stale snapshot")
            check(snapshot.parentFile!!.setWritable(false, true))
            expect(!snapshot.delete())
            SystemBackupState.initialize(context)
            expect(snapshot.exists())
            expect(SystemBackupState.status(context) == SystemBackupSnapshot.CLEANUP_STATUS)
            expect(!SystemBackupState.enabled(context))
            var entered = false
            expect(runCatching { SystemBackupSnapshot.withFile(snapshot) { entered = true } }.exceptionOrNull()
                is SystemBackupSnapshot.CleanupFailure)
            expect(!entered)
            check(snapshot.parentFile!!.setWritable(true, true))
            SystemBackupState.initialize(context)
            expect(!snapshot.exists() && !SystemBackupState.enabled(context))
            expect(SystemBackupState.status(context) != SystemBackupSnapshot.CLEANUP_STATUS)

            SystemBackupSnapshot.withFile(snapshot) { it.writeText("synthetic successful handoff") }
            expect(!snapshot.exists())
            val operationFailure = IOException("synthetic handoff failure")
            val failed = runCatching { SystemBackupSnapshot.withFile(snapshot) {
                it.writeText("synthetic failed handoff")
                throw operationFailure
            } }.exceptionOrNull()
            expect(failed === operationFailure && !snapshot.exists())

            val successfulHandoffCleanup = runCatching { SystemBackupSnapshot.withFile(snapshot) {
                it.writeText("synthetic handed-off snapshot")
                check(it.parentFile!!.setWritable(false, true))
            } }.exceptionOrNull()
            expect(successfulHandoffCleanup is SystemBackupSnapshot.CleanupFailure)
            expect(snapshot.exists())
            check(snapshot.parentFile!!.setWritable(true, true))
            SystemBackupSnapshot.discard(snapshot)
            expect(!snapshot.exists())

            val cleanupFailure = runCatching { SystemBackupSnapshot.withFile(snapshot) {
                it.writeText("synthetic retained handoff")
                check(it.parentFile!!.setWritable(false, true))
                throw operationFailure
            } }.exceptionOrNull()
            expect(cleanupFailure is SystemBackupSnapshot.CleanupFailure)
            expect(cleanupFailure!!.suppressed.singleOrNull() === operationFailure)
            expect(snapshot.exists())
            check(snapshot.parentFile!!.setWritable(true, true))
            SystemBackupSnapshot.discard(snapshot)
            expect(!snapshot.exists())

            val inUse = CountDownLatch(1)
            val release = CountDownLatch(1)
            val threadFailure = AtomicReference<Throwable?>()
            val worker = Thread {
                try { SystemBackupSnapshot.withFile(snapshot) {
                    it.writeText("synthetic active handoff")
                    inUse.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    check(it.exists())
                } } catch (caught: Throwable) { threadFailure.set(caught) }
            }
            worker.start()
            try {
                expect(inUse.await(3, TimeUnit.SECONDS))
                expect(!SystemBackupSnapshot.discardWhenIdle(snapshot))
                expect(snapshot.exists())
                SystemBackupState.initialize(context)
                expect(snapshot.exists() && !SystemBackupState.enabled(context))
            } finally { release.countDown(); worker.join(6000) }
            expect(!worker.isAlive && threadFailure.get() == null && !snapshot.exists())
            expect(sentinel.readText() == "synthetic")
            return "PASS: $checks isolated backup snapshot checks; no transport or real history changes"
        } finally {
            root.walkTopDown().filter { it.isDirectory }.forEach { it.setWritable(true, true) }
            check(target.deleteSharedPreferences(preference))
            check(root.parentFile!!.canonicalFile == target.noBackupFilesDir.canonicalFile)
            check(root.deleteRecursively() && !root.exists())
        }
    }
}
