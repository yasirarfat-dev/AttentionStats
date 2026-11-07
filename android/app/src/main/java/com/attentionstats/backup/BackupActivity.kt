package com.attentionstats.backup

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import android.widget.Switch
import android.provider.Settings
import com.attentionstats.reels.ReelsCoordinator
import java.util.concurrent.atomic.AtomicBoolean

/** User-driven SAF transport. Package-derived key is public; storage access controls provide privacy. */
@Suppress("DEPRECATION")
class BackupActivity : Activity() {
    private val coordinator get() = ReelsCoordinator.get(this)
    private val cancelled = AtomicBoolean(false)
    private lateinit var page: com.attentionstats.appearance.NativePage
    private lateinit var status: TextView
    private var encrypted: ByteArray? = null
    private var operation = false
    override fun onCreate(savedInstanceState: Bundle?) {
        com.attentionstats.appearance.AppearanceOwner.prepare(this)
        super.onCreate(savedInstanceState)
        try { SystemBackupState.initialize(this) } catch (_: RuntimeException) { }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        page = com.attentionstats.appearance.NativePage(this)
        text("Backup & restore", 24f)
        text("Keep a portable copy of your history and choices without remembering a backup key. Choose a private destination using Android's file picker; a cloud provider you choose may upload the file.")
        text("Backups use this app's public package name as their key. Anyone who obtains a file can read or modify it using that public key. Keep files in private storage and protect your cloud account. No secret backup password is required.")
        text("Snapshots currently support up to 20 MiB / 100,000 records. Restore checks the file and previews replacement of current history. Tracking, reminders, blocks and schedules stay off afterward until you choose to enable them. System permissions are never imported.")
        status = text(SystemBackupState.status(this))
        text("Automatic Android backup", 21f)
        text("Automatic backup starts enabled unless you previously turned it off. Android uses the backup account already configured on this phone, usually roughly nightly after at least 24 hours, while idle with a suitable network. Timing is not guaranteed. Android backup must be enabled in device settings. No password to remember. Android 9 or newer, a screen lock and an encrypted backup transport are required. This protects recovery; it does not sync live history between devices.")
        @Suppress("DEPRECATION")
        var changingAutomatic = false
        val automatic = Switch(this).apply {
            text = "Allow automatic Android backup"
            isChecked = SystemBackupState.enabled(this@BackupActivity)
            setPadding(0, 16, 0, 16)
            setOnCheckedChangeListener { _, enabled ->
                if (changingAutomatic) return@setOnCheckedChangeListener
                changingAutomatic = true
                try {
                    SystemBackupState.configure(this@BackupActivity, enabled)
                    status.text = if (enabled) SystemBackupState.status(this@BackupActivity) else "Automatic backup is off. Existing provider copies may remain."
                } catch (_: Exception) { isChecked = !enabled; message("Could not change automatic backup. Check your screen lock and Android backup settings.") }
                finally { changingAutomatic = false }
            }
        }
        page.toggle(automatic)
        button("Request Android backup") {
            try { SystemBackupState.request(this); status.text = SystemBackupState.status(this) }
            catch (_: Exception) { message("Enable automatic backup, set a screen lock and review or discard any pending restore first.") }
        }
        button("Android backup settings") {
            val actions = listOf("android.settings.BACKUP_SETTINGS", Settings.ACTION_PRIVACY_SETTINGS, Settings.ACTION_SETTINGS)
            for (action in actions) try { startActivity(Intent(action)); break } catch (_: RuntimeException) { }
        }
        if (SystemBackupState.pending(this).isFile) {
            text("Android delivered a backup. Review it before replacing current history.")
            button("Review Android restore") {
                operation = true
                Thread {
                    val bytes = try { SystemBackupState.pending(this).inputStream().use(::readArchive) }
                        catch (_: Exception) { runOnUiThread { operation = false; message("Could not read the restored backup. Current history is unchanged.") }; return@Thread }
                    runOnUiThread { preview(bytes, true) }
                }.start()
            }
            button("Discard Android restore") { SystemBackupState.clearPending(this); status.text = "Pending restore discarded. Current history is unchanged." }
        }
        text("Manual files", 21f)
        button("Create backup") { createBackup() }
        button("Restore backup") { restore() }
        text("Android manages automatic cloud backup using your device backup settings. This app does not connect to the Drive API or choose an account for you. Manual exports rely on the privacy of the destination you choose.")
        button("Done") { finish() }
    }
    override fun onResume() {
        super.onResume()
        if (::page.isInitialized) {
            page.refresh()
            // Returning from Android settings may have satisfied the screen-lock prerequisite.
            if (!operation) {
                val previous = SystemBackupState.status(this)
                try { SystemBackupState.initialize(this) } catch (_: RuntimeException) { }
                val current = SystemBackupState.status(this)
                if (current != previous) status.text = current
            }
        }
    }
    private fun text(value: String, size: Float = 15f): TextView = page.text(value, size)
    private fun button(value: String, action: () -> Unit) = page.button(value, filled = value == "Create backup") { if (!operation) action() }
    private fun createBackup() {
        AlertDialog.Builder(this).setTitle("Keep this backup private")
            .setMessage("No password to remember. The app package name is the public backup key, so anyone with the file can open it. Your selected storage provider controls access. Continue only with a destination you trust.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Choose destination") { _, _ ->
                operation = true; status.text = "Creating a backup snapshot…"
                coordinator.exportArchive(cancelled, { bytes -> runOnUiThread {
                    if (isFinishing || isDestroyed) { bytes.fill(0); return@runOnUiThread }
                    clearBytes(); encrypted = bytes
                    try {
                        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, "attention-stats.attentionbackup"), EXPORT)
                    } catch (_: RuntimeException) { clearBytes(); operation = false; status.text = "No document provider available." }
                } }, { code -> runOnUiThread { operation = false; status.text = if (code == "ARCHIVE_TOO_LARGE")
                    "History exceeds the current backup size or record limit. No partial backup was created and no history was removed."
                    else "Could not export. History is unchanged; try again later." } })
            }.show()
    }
    private fun restore() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), IMPORT)
        } catch (_: RuntimeException) { message("No document provider available.") }
    }
    @Deprecated("Android activity result callback required by the native picker flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != EXPORT && requestCode != IMPORT) return
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) { clearBytes(); operation = false; status.text = "Cancelled. History is unchanged."; return }
        operation = true
        if (requestCode == EXPORT) {
            val bytes = encrypted ?: run { operation = false; message("Snapshot expired. Create a new backup."); return }
            Thread {
                val saved = runCatching {
                    check(!cancelled.get())
                    checkNotNull(contentResolver.openOutputStream(uri, "w")).use { stream ->
                        var offset = 0
                        while (offset < bytes.size) {
                            check(!cancelled.get()); val count = minOf(8192, bytes.size - offset)
                            stream.write(bytes, offset, count); offset += count
                        }
                        stream.flush()
                    }
                }.isSuccess
                bytes.fill(0)
                runOnUiThread { encrypted = null; operation = false
                    status.text = if (saved) "Backup file written and closed. Remote/cloud durability is not confirmed; verify a restore before relying on it."
                    else "Could not finish writing. A partial external file may remain; remove it using your file provider. History is unchanged." }
            }.start()
        } else {
            status.text = "Checking backup and validating a private staging store…"
            Thread {
                val bytes = try {
                    checkNotNull(contentResolver.openInputStream(uri)).use(::readArchive)
                } catch (_: Exception) { runOnUiThread { operation = false; status.text = "Could not read this archive. History is unchanged." }; return@Thread }
                runOnUiThread { preview(bytes) }
            }.start()
        }
    }
    private fun readArchive(input: java.io.InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        try {
            while (true) {
                check(!cancelled.get())
                val count = input.read(buffer); if (count < 0) break
                require(output.size().toLong() + count <= ArchiveEnvelope.MAX_CIPHER)
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        } finally { buffer.fill(0) }
    }
    private fun preview(bytes: ByteArray, systemRestore: Boolean = false) {
        operation = true
        status.text = "Checking backup and validating a private staging store…"
        coordinator.previewArchive(bytes, cancelled, { token, rows -> runOnUiThread {
            operation = false
            if (isFinishing || isDestroyed) { coordinator.cancelArchive(); return@runOnUiThread }
            status.text = "Archive verified: $rows records. Current history has not changed."
            AlertDialog.Builder(this).setTitle("Replace current history?")
                .setMessage("This backup has $rows records. Its public package key does not prove who created it. Restore only files you trust. Replacement removes current history. Observation and protection will stay off. Create a backup of current data first if you want to keep it.")
                .setNegativeButton("Cancel") { _, _ -> coordinator.cancelArchive() }
                .setNeutralButton("Back up current data first") { _, _ -> coordinator.cancelArchive(); createBackup() }
                .setPositiveButton("Replace history") { _, _ ->
                    operation = true
                    coordinator.restoreArchive(token, cancelled, { runOnUiThread { if (systemRestore) SystemBackupState.clearPending(this); operation = false; status.text = "History restored. Tracking and protection are off; review your choices before re-enabling them." } },
                        { code -> runOnUiThread { operation = false; status.text = if (code == "STALE_PREVIEW") "History changed since preview. Select the archive again to review a fresh replacement." else "Restore did not complete. Existing history is preserved." } })
                }.setOnCancelListener { coordinator.cancelArchive() }.show()
        } }, { runOnUiThread { operation = false; status.text = "Backup rejected: damaged file, unsupported format or limit. Older recovery-key archives are not supported by this flow. Existing history is unchanged." } })
    }
    private fun clearBytes() { encrypted?.fill(0); encrypted = null }
    private fun message(value: String) { if (!isFinishing) AlertDialog.Builder(this).setMessage(value).setPositiveButton("Okay", null).show() }
    override fun onDestroy() {
        cancelled.set(true); clearBytes(); coordinator.cancelArchive()
        super.onDestroy()
    }
    companion object { private const val EXPORT = 70; private const val IMPORT = 71 }
}
