package com.attentionstats.backup

import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Synthetic filesystem checks; does not invoke Android backup or touch app history. */
fun main() {
    val folder = Files.createTempDirectory("attentionstats-pending-restore-").toFile()
    val base = File(folder, "restore")
    var checks = 0
    fun expect(value: Boolean) { check(value); checks++ }
    try {
        expect(!PendingRestoreFiles.exists(base))
        for (suffix in listOf("", ".bak", ".new")) {
            val member = File(base.path + suffix)
            member.writeText("synthetic archive")
            expect(PendingRestoreFiles.exists(base))
            PendingRestoreFiles.discard(base)
            expect(!member.exists() && !PendingRestoreFiles.exists(base))
        }
        for (suffix in listOf("", ".bak", ".new")) File(base.path + suffix).writeText("synthetic archive")
        PendingRestoreFiles.discard(base)
        expect(!PendingRestoreFiles.exists(base))
        PendingRestoreFiles.discard(base)
        expect(!PendingRestoreFiles.exists(base))

        // A nonempty directory makes deletion fail predictably on Windows and Unix.
        val blocked = File(base.path + ".bak")
        check(blocked.mkdir())
        File(blocked, "retained").writeText("synthetic data")
        var rejected = false
        try { PendingRestoreFiles.discard(base) } catch (_: IOException) { rejected = true }
        expect(rejected)
        expect(PendingRestoreFiles.exists(base))
        expect(File(blocked, "retained").readText() == "synthetic data")
        println("PASS: $checks pending-restore filesystem checks (host only)")
    } finally { check(folder.deleteRecursively()) }
}
