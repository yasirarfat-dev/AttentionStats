package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.backup.BackupAgent
import android.content.ContextWrapper
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.attentionstats.backup.ArchiveEnvelope
import com.attentionstats.backup.SystemBackupAgent
import com.attentionstats.backup.SystemBackupState
import java.io.File
import java.util.UUID

/** Calls the real restore callback; every file/preference is UUID-owned. No transport or live DB. */
internal object RestoreIngressVerification {
    fun run(instrumentation: Instrumentation): String {
        val target = instrumentation.targetContext
        val originalPreferences = target.getSharedPreferences("system-backup", 0).all.toMap()
        val token = UUID.randomUUID().toString()
        val root = File(target.noBackupFilesDir, "restore-ingress-$token")
        check(root.mkdir())
        val preference = "restore-ingress-$token"
        val scoped = object : ContextWrapper(target) {
            override fun getFilesDir() = File(root, "files").also { check(it.mkdirs() || it.isDirectory) }
            override fun getNoBackupFilesDir() = File(root, "private").also { check(it.mkdirs() || it.isDirectory) }
            override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences(preference, mode)
        }
        val agent = object : SystemBackupAgent() { init { attachBaseContext(scoped) } }
        val destination = File(scoped.filesDir, SystemBackupState.RELATIVE_FILE)
        val input = File(root, "input")
        val sentinel = File(root, "sentinel").also { it.writeText("synthetic unrelated file") }
        val plain = "synthetic quarantine payload; logical import validation is a separate boundary".toByteArray()
        val valid = ArchiveEnvelope.write(plain, ArchiveEnvelope.packageCredential(), profile = ArchiveEnvelope.PUBLIC_PACKAGE_PROFILE)
        var checks = 0
        fun expect(value: Boolean) { check(value) { "restore ingress check ${checks + 1}" }; checks++ }
        fun deliver(bytes: ByteArray, size: Long = bytes.size.toLong(), path: File = destination,
            type: Int = BackupAgent.TYPE_FILE, rejected: Boolean = false, unread: Boolean = false) {
            input.writeBytes(bytes)
            ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                val result = runCatching { agent.onRestoreFile(descriptor, size, path, type, 0, 0) }
                expect(result.isFailure == rejected)
                expect(descriptor.fileDescriptor.valid()) // Framework retains ownership, including errors.
                if (unread) expect(Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_CUR) == 0L)
                if (!rejected && type == BackupAgent.TYPE_FILE)
                    expect(Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_CUR) == size)
            }
        }
        fun preservedPending() {
            expect(SystemBackupState.readPending(scoped) { it.readBytes().contentEquals(valid) })
            expect(!SystemBackupState.enabled(scoped))
            expect(!destination.exists() && sentinel.readText() == "synthetic unrelated file")
        }
        try {
            // Delivery before first foreground leaves automatic-backup preferences uninitialized.
            deliver(valid + byteArrayOf(41, 42), size = valid.size.toLong())
            preservedPending()
            expect(!scoped.getSharedPreferences("system-backup", 0).contains("enabled"))
            val status = SystemBackupState.status(scoped)
            fun reject(bytes: ByteArray = valid, size: Long = bytes.size.toLong(), path: File = destination,
                type: Int = BackupAgent.TYPE_FILE, unread: Boolean = false) {
                deliver(bytes, size, path, type, rejected = true, unread = unread)
                preservedPending()
                expect(SystemBackupState.status(scoped) == status)
            }
            for (size in listOf(-1L, 0L, 64L, ArchiveEnvelope.MAX_CIPHER + 1L, Long.MAX_VALUE))
                reject(size = size, unread = true)
            reject(path = File(scoped.filesDir, "unexpected"), unread = true)
            reject(path = File(scoped.filesDir, "../sentinel"), unread = true)
            val link = File(scoped.filesDir, "outside")
            Os.symlink(root.absolutePath, link.absolutePath)
            reject(path = File(link, "history.attentionbackup"), unread = true)
            check(link.delete())
            reject(type = BackupAgent.TYPE_DIRECTORY, unread = true)
            reject(type = 99, unread = true)
            reject(bytes = valid.copyOf(valid.size - 1), size = valid.size.toLong())
            reject(bytes = valid.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
            reject(bytes = valid + byteArrayOf(0))
            val secretProfile = ArchiveEnvelope.write(plain, ArchiveEnvelope.packageCredential())
            try { reject(bytes = secretProfile) } finally { secretProfile.fill(0) }
            // Directory callbacks are no-ops, even at an unrelated path, and consume no bytes.
            deliver(valid, size = 0, path = sentinel, type = BackupAgent.TYPE_DIRECTORY, unread = true)
            preservedPending()
            SystemBackupState.clearPending(scoped)
            expect(!SystemBackupState.hasPending(scoped))
            expect(listOf("", ".bak", ".new").none { File(SystemBackupState.pending(scoped).path + it).exists() })
            expect(target.getSharedPreferences("system-backup", 0).all == originalPreferences)
        } finally {
            plain.fill(0); valid.fill(0)
            check(target.deleteSharedPreferences(preference))
            val link = File(scoped.filesDir, "outside")
            if (java.nio.file.Files.isSymbolicLink(link.toPath())) check(link.delete())
            check(root.canonicalFile.parentFile == target.noBackupFilesDir.canonicalFile)
            check(root.deleteRecursively() && !root.exists())
        }
        expect(target.getSharedPreferences("system-backup", 0).all == originalPreferences)
        return "PASS: $checks isolated restore ingress checks; rejected entries preserve quarantine and descriptor ownership; live history untouched"
    }
}
