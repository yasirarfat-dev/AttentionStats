package com.attentionstats.backup

import java.io.File
import java.io.IOException

/** Treat AtomicFile's recovery/staging siblings as pending until reviewed or discarded. */
internal object PendingRestoreFiles {
    private fun members(base: File) = listOf(base, File(base.path + ".bak"), File(base.path + ".new"))

    fun exists(base: File): Boolean = members(base).any { it.exists() }

    /** File.delete() can fail without throwing. Never acknowledge an incomplete discard. */
    fun discard(base: File) {
        members(base).forEach { file ->
            if (file.exists() && !file.delete()) throw IOException("Pending restore could not be removed")
        }
        if (exists(base)) throw IOException("Pending restore still exists")
    }
}
