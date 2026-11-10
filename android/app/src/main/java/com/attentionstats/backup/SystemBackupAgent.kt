package com.attentionstats.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import com.attentionstats.reels.ReelsCoordinator
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Framework transport only. Never walks app storage or copies the live database/device keys. */
open class SystemBackupAgent : BackupAgent() {
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput, newState: ParcelFileDescriptor) = Unit
    override fun onRestore(data: BackupDataInput, appVersionCode: Int, newState: ParcelFileDescriptor) = Unit

    override fun onFullBackup(data: FullBackupDataOutput) {
        if (SystemBackupState.hasPending(this)) {
            SystemBackupState.record(this, "Review or discard the delivered restore before backing up new history.")
            throw IOException("Pending restore requires review")
        }
        if (!SystemBackupState.enabled(this)) return
        if (android.os.Build.VERSION.SDK_INT < 28 || !SystemBackupState.supported(this) || data.transportFlags and FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED == 0 ||
            data.transportFlags and FLAG_DEVICE_TO_DEVICE_TRANSFER != 0) {
            SystemBackupState.record(this, "Waiting for Android backup with screen-lock encryption.")
            throw IOException("Protected cloud transport required")
        }
        val cancelled = AtomicBoolean(false)
        val result = AtomicReference<ByteArray?>()
        val exportFailure = AtomicReference<String?>()
        val done = CountDownLatch(1)
        val file = File(filesDir, SystemBackupState.RELATIVE_FILE)
        try {
            ReelsCoordinator.get(this).exportArchive(cancelled, { bytes ->
                synchronized(result) {
                    if (cancelled.get()) bytes.fill(0) else result.set(bytes)
                }
                done.countDown()
            }, { code -> exportFailure.set(code); done.countDown() })
            if (!done.await(25, TimeUnit.SECONDS)) throw IOException("Snapshot timed out")
            val bytes = result.get() ?: throw IOException("Snapshot unavailable or too large")
            if (data.quota > 0 && bytes.size.toLong() + 2048 > data.quota) throw IOException("Backup quota exceeded")
            SystemBackupState.withBackupAdmission(this) {
                check(SystemBackupState.enabled(this))
                check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
                file.outputStream().use { it.write(bytes) }
                fullBackupFile(file, data)
                SystemBackupState.record(this, "Snapshot handed to Android. Cloud upload completion is not confirmed.")
            }
        } catch (_: Exception) {
            SystemBackupState.record(this, if (exportFailure.get() == "ARCHIVE_TOO_LARGE")
                "History exceeds the current backup size or record limit. No backup was offered and no history was removed."
                else "Backup did not finish. History is unchanged; Android may retry.")
            throw IOException("Backup snapshot unavailable")
        } finally {
            synchronized(result) { cancelled.set(true); result.getAndSet(null)?.fill(0) }
            file.delete()
        }
    }

    override fun onRestoreFile(data: ParcelFileDescriptor, size: Long, destination: File, type: Int, mode: Long, mtime: Long) {
        if (type == TYPE_DIRECTORY && size == 0L) return
        if (type != TYPE_FILE || destination.canonicalFile != File(filesDir, SystemBackupState.RELATIVE_FILE).canonicalFile ||
            size !in 65..ArchiveEnvelope.MAX_CIPHER.toLong()) throw IOException("Unsupported restore entry")
        // The framework owns this descriptor; read exactly this entry without closing its stream.
        val input = FileInputStream(data.fileDescriptor)
        val bytes = ByteArray(size.toInt())
        try {
            var offset = 0
            while (offset < bytes.size) {
                val count = input.read(bytes, offset, bytes.size - offset)
                if (count <= 0) throw IOException("Incomplete restore")
                offset += count
            }
            ArchiveEnvelope.read(bytes, ArchiveEnvelope.packageCredential(),
                expectedProfile = ArchiveEnvelope.PUBLIC_PACKAGE_PROFILE).fill(0)
            SystemBackupState.writePending(this, bytes)
            SystemBackupState.record(this, "Android delivered a backup. Open Backup & restore to review it.")
        } finally { bytes.fill(0) }
    }

    override fun onQuotaExceeded(backupDataBytes: Long, quotaBytes: Long) {
        SystemBackupState.record(this, "Android backup quota exceeded. No truncated backup was offered.")
    }
}
