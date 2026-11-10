package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.ContextWrapper
import android.util.AtomicFile
import com.attentionstats.backup.SystemBackupState
import java.io.File
import java.io.IOException
import java.util.UUID

/** Synthetic AtomicFile lifecycle in an isolated cache directory; no backup/restore transport. */
internal object PendingRestoreVerification {
    fun run(instrumentation: Instrumentation): Int {
        val target = instrumentation.targetContext
        val root = File(target.cacheDir, "pending-restore-test-${UUID.randomUUID()}")
        check(root.mkdir())
        val context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = root
        }
        val base = SystemBackupState.pending(context)
        val backup = File(base.path + ".bak")
        val next = File(base.path + ".new")
        val original = "synthetic committed archive".toByteArray()
        val replacement = "synthetic interrupted archive".toByteArray()
        var checks = 0
        fun expect(value: Boolean) { check(value) { "pending restore check ${checks + 1}" }; checks++ }
        fun read() = SystemBackupState.readPending(context) { it.readBytes() }
        try {
            expect(!SystemBackupState.hasPending(context))
            SystemBackupState.writePending(context, original)
            expect(SystemBackupState.hasPending(context))
            expect(read().contentEquals(original))

            // The actual platform abort path must retain the previous committed bytes.
            val owned = AtomicFile(base)
            val output = owned.startWrite()
            try { output.write(replacement) } finally { owned.failWrite(output) }
            expect(read().contentEquals(original))

            // Simulate a legacy interrupted replacement: only the recovery sibling survived.
            check(base.renameTo(backup))
            expect(!base.exists() && backup.isFile)
            expect(SystemBackupState.hasPending(context))
            expect(read().contentEquals(original))
            expect(base.isFile && !backup.exists())

            // An abandoned new write never replaces the committed recovery bytes on read.
            next.writeBytes(replacement)
            expect(SystemBackupState.hasPending(context))
            expect(read().contentEquals(original))
            SystemBackupState.clearPending(context)
            expect(!SystemBackupState.hasPending(context))

            // Even a staging-only crash state fences backup until explicitly discarded.
            next.writeBytes(replacement)
            expect(!base.exists() && SystemBackupState.hasPending(context))
            SystemBackupState.clearPending(context)
            expect(!next.exists() && !SystemBackupState.hasPending(context))

            base.writeBytes(original)
            backup.writeBytes(original)
            next.writeBytes(replacement)
            SystemBackupState.clearPending(context)
            expect(!base.exists() && !backup.exists() && !next.exists())
            SystemBackupState.clearPending(context)
            expect(!SystemBackupState.hasPending(context))

            // Predictable deletion failure: preserve a fence rather than acknowledge success.
            check(backup.mkdir())
            val retained = File(backup, "retained").apply { writeBytes(original) }
            var failed = false
            try { SystemBackupState.clearPending(context) } catch (_: IOException) { failed = true }
            expect(failed)
            expect(SystemBackupState.hasPending(context))
            expect(retained.readBytes().contentEquals(original))
            return checks
        } finally {
            original.fill(0); replacement.fill(0)
            check(root.parentFile?.canonicalFile == target.cacheDir.canonicalFile)
            check(root.deleteRecursively())
        }
    }
}
