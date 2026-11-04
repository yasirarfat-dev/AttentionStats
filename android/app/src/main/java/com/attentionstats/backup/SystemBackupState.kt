package com.attentionstats.backup

import android.app.KeyguardManager
import android.app.backup.BackupManager
import android.content.Context
import java.io.File

/** Device-local consent/status. These preferences and pending restores are never backed up. */
internal object SystemBackupState {
    const val RELATIVE_FILE = "system-backup/history.attentionbackup"
    private fun preferences(context: Context) = context.getSharedPreferences("system-backup", Context.MODE_PRIVATE)
    fun enabled(context: Context) = preferences(context).getBoolean("enabled", false)
    fun supported(context: Context) = android.os.Build.VERSION.SDK_INT >= 28 &&
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    fun configure(context: Context, enabled: Boolean) {
        require(!enabled || supported(context))
        check(preferences(context).edit().putBoolean("enabled", enabled).commit())
        if (enabled) request(context)
    }
    fun request(context: Context) {
        check(enabled(context) && supported(context))
        BackupManager(context).dataChanged()
        record(context, "Requested. Android controls timing; an upload is not confirmed.")
    }
    fun record(context: Context, status: String) {
        check(preferences(context).edit().putString("status", status).commit())
    }
    fun status(context: Context): String = preferences(context).getString("status", "No backup attempt recorded.")!!
    fun pending(context: Context) = File(context.noBackupFilesDir, "system-restore.attentionbackup")
    fun clearPending(context: Context) { pending(context).delete() }
}
