package com.attentionstats.backup

import android.app.KeyguardManager
import android.app.backup.BackupManager
import android.content.Context
import java.io.File

/** Device-local backup choice/status. These preferences and pending restores are never backed up. */
internal object SystemBackupState {
    const val RELATIVE_FILE = "system-backup/history.attentionbackup"
    private val pendingLock = Any()
    private fun preferences(context: Context) = context.getSharedPreferences("system-backup", Context.MODE_PRIVATE)
    fun enabled(context: Context) = preferences(context).getBoolean("enabled", false)
    fun supported(context: Context) = android.os.Build.VERSION.SDK_INT >= 28 &&
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    /** Called on foreground entry, never during pre-launch framework restore. Preserve explicit off. */
    @Synchronized
    fun initialize(context: Context) {
        val prefs = preferences(context)
        if (!prefs.contains("enabled")) check(prefs.edit().putBoolean("enabled", true).commit())
        if (!enabled(context)) return
        if (hasPending(context)) {
            record(context, "Review or discard the delivered restore before backing up new history.")
            return
        }
        if (!supported(context)) {
            record(context, "Automatic backup is on, waiting for Android 9 or newer and a secure screen lock.")
            return
        }
        val now = System.currentTimeMillis()
        val last = prefs.getLong("requestedAt", 0L)
        if (last == 0L || now < last || now - last >= 24 * 60 * 60 * 1000L) {
            try { request(context) }
            catch (_: RuntimeException) { record(context, "Automatic backup is on. Android could not accept a request; the app will retry when opened.") }
        }
    }
    fun configure(context: Context, enabled: Boolean) {
        check(preferences(context).edit().putBoolean("enabled", enabled).putLong("requestedAt", 0L).commit())
        if (enabled) initialize(context)
        else record(context, "Automatic backup is off. Existing provider copies may remain.")
    }
    fun request(context: Context) {
        withBackupAdmission(context) {
            check(enabled(context) && supported(context))
            BackupManager(context).dataChanged()
            check(preferences(context).edit().putLong("requestedAt", System.currentTimeMillis()).commit())
            record(context, "Requested. Android controls timing; an upload is not confirmed.")
        }
    }
    fun record(context: Context, status: String) {
        check(preferences(context).edit().putString("status", status).commit())
    }
    fun status(context: Context): String = preferences(context).getString("status", "No backup attempt recorded.")!!
    fun pending(context: Context) = File(context.noBackupFilesDir, "system-restore.attentionbackup")
    fun hasPending(context: Context) = synchronized(pendingLock) { PendingRestoreFiles.exists(pending(context)) }
    /** Never await coordinator export here: restore delivery must remain free during snapshot work. */
    fun <T> withBackupAdmission(context: Context, action: () -> T): T = synchronized(pendingLock) {
        check(!PendingRestoreFiles.exists(pending(context))) { "Review or discard the pending restore first" }
        action()
    }
    fun <T> readPending(context: Context, read: (java.io.InputStream) -> T): T = synchronized(pendingLock) {
        android.util.AtomicFile(pending(context)).openRead().use(read)
    }
    fun writePending(context: Context, bytes: ByteArray) = synchronized(pendingLock) {
        val owned = android.util.AtomicFile(pending(context))
        val output = owned.startWrite()
        try { output.write(bytes); owned.finishWrite(output) }
        catch (failure: Exception) { owned.failWrite(output); throw failure }
    }
    fun clearPending(context: Context) = synchronized(pendingLock) { PendingRestoreFiles.discard(pending(context)) }
}
