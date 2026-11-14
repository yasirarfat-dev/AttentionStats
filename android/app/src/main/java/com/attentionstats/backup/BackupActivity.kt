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
        page.section("Backup Files", "Keep a portable copy of your history and choices using Android's file picker. Your chosen provider may upload it. Files use the app's public package name as their key: anyone with a file can read or modify it. Keep files private and protect your cloud account. Snapshots support up to 20 MiB / 100,000 records. Restore validates and previews replacement; tracking and protection stay off afterward until enabled. System permissions are not imported.")
        status = text(SystemBackupState.status(this))
        page.section("Automatic Android Backup", "Starts enabled unless you saved an off choice. Android uses the backup account configured on this phone, typically roughly nightly when idle and eligible; timing is not guaranteed. Android backup must be enabled in device settings. Android 9+, screen lock and an encrypted transport are required. This is recovery, not live history synchronization. The app does not connect to the Drive API or choose an account.")
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
        if (SystemBackupState.hasPending(this)) {
            text("Android delivered a backup. Review it before replacing current history.")
            button("Review Android restore") {
                operation = true
                Thread {
                    val bytes = try { SystemBackupState.readPending(this, ::readArchive) }
                        catch (_: Exception) { runOnUiThread { operation = false; message("Could not read the restored backup. Current history is unchanged.") }; return@Thread }
                    runOnUiThread { preview(bytes, true) }
                }.start()
            }
            button("Discard Android restore") {
                try {
                    SystemBackupState.clearPending(this)
                    status.text = "Pending restore discarded. Current history is unchanged."
                } catch (_: Exception) {
                    status.text = "Could not discard the delivered restore. Automatic backup remains blocked; try again. Current history is unchanged."
                }
            }
        }
        page.section("Manual Files", "Create a backup through Android's file picker, or restore a file after its replacement preview. Exports rely on the destination's access controls; the public app key is not secret protection. Disabling automatic backup does not remove existing provider copies.")
        button("Create backup") { createBackup() }
        button("Restore backup") { restore() }
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
                    coordinator.restoreArchive(token, cancelled, { runOnUiThread {
                        val discarded = !systemRestore || runCatching { SystemBackupState.clearPending(this) }.isSuccess
                        operation = false
                        status.text = "History restored. Tracking and protection are off; review your choices before re-enabling them." +
                            if (discarded) "" else " The delivered backup could not be discarded. Automatic backup remains blocked; discard it before requesting a new backup."
                    } },
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
