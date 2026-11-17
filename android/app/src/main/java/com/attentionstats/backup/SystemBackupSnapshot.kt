package com.attentionstats.backup

import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Owns only the allowlisted temporary snapshot; never walks history or pending restores. */
internal object SystemBackupSnapshot {
    private val lock = ReentrantLock()
    const val CLEANUP_STATUS = "Temporary backup snapshot could not be removed. History is unchanged; cleanup will retry when opened."
    class CleanupFailure : IOException("Temporary backup snapshot could not be removed")

    fun discard(file: File) = lock.withLock { remove(file) }

    /** Opening a screen must not wait for framework I/O or remove a snapshot in use. */
    fun discardWhenIdle(file: File): Boolean {
        if (!lock.tryLock()) return false
        try { remove(file); return true }
        finally { lock.unlock() }
    }

    private fun remove(file: File) {
        if (file.exists() && (!file.delete() || file.exists())) throw CleanupFailure()
    }

    /** Foreground crash cleanup cannot remove a snapshot while the framework is reading it. */
    fun <T> withFile(file: File, action: (File) -> T): T = lock.withLock {
        remove(file)
        var failure: Throwable? = null
        try { return action(file) }
        catch (caught: Throwable) { failure = caught; throw caught }
        finally {
            try { remove(file) }
            catch (cleanup: CleanupFailure) {
                failure?.let { cleanup.addSuppressed(it) }
                throw cleanup
            }
        }
    }
}
