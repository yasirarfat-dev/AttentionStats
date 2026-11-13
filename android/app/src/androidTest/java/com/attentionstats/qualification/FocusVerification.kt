package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.ContextWrapper
import android.os.SystemClock
import com.attentionstats.protection.ScheduleOwner
import com.attentionstats.storage.HistoryStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** Real encrypted owner/persistence, isolated from the user's rules/history; no rendered blocks. */
internal object FocusVerification {
    fun run(instrumentation: Instrumentation): Int = runBlocking {
        val target = instrumentation.targetContext
        val folder = File(target.noBackupFilesDir, "focus-${UUID.randomUUID()}")
        check(folder.mkdir())
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = folder }
        val alias = "attentionstats.focus.${UUID.randomUUID()}"
        var store: HistoryStore? = null
        var checks = 0
        fun expect(value: Boolean) { check(value) { "focus check ${checks + 1}" }; checks++ }
        try {
            var history = HistoryStore.open(context, alias).also { store = it }
            val owner = ScheduleOwner(context)
            owner.load(history)
            expect(owner.activeFocus() == null)
            val packages = setOf("com.example.focus")
            expect(owner.focus(history, packages, 1, 0, null))
            val focus = checkNotNull(owner.activeFocus())
            expect(focus.packages == packages && owner.revision == 1L)
            expect(owner.decision(packages.first(), SystemClock.elapsedRealtime()).size == 1)
            expect(owner.targets() == packages)
            expect(owner.decision("com.android.settings", SystemClock.elapsedRealtime()).isEmpty())
            expect(ScheduleOwner.decode(ScheduleOwner.encode(owner.plan).toString()) == owner.plan)
            expect(!owner.focus(history, packages, 1, 0, null))
            expect(runCatching { owner.focus(history, packages, 1, 1, null) }.isFailure)
            expect(runCatching { owner.focus(history, null, 0, 1, "stale-id") }.isFailure)
            expect(owner.save(history, "{\"groups\":[],\"windows\":[]}", 1))
            expect(owner.activeFocus()?.id == focus.id) // Schedule edits cannot erase focus.
            history.close(); store = null
            history = HistoryStore.open(context, alias).also { store = it }
            val reopened = ScheduleOwner(context)
            reopened.load(history)
            expect(reopened.activeFocus()?.id == focus.id)
            expect(reopened.decision(packages.first(), SystemClock.elapsedRealtime()).size == 1)
            reopened.allow(packages.first(), SystemClock.elapsedRealtime())
            expect(reopened.decision(packages.first(), SystemClock.elapsedRealtime()).isEmpty())
            val snapshot = JSONObject(reopened.snapshot(false)).getJSONObject("focus")
            expect(snapshot.getString("id") == focus.id && snapshot.getString("remainingMs").toLong() in 1..60_000)
            expect(reopened.focus(history, null, 0, 2, focus.id))
            expect(reopened.activeFocus() == null && reopened.targets().isEmpty())
            expect(reopened.decision(packages.first(), SystemClock.elapsedRealtime()).isEmpty())
            expect(JSONObject(reopened.snapshot(true)).isNull("focus"))
            expect(runCatching { reopened.focus(history, setOf("com.android.settings"), 1, 3, null) }.isFailure)
            expect(runCatching { reopened.focus(history, packages, 181, 3, null) }.isFailure)
            expect(reopened.revision == 3L && reopened.plan.groups.isEmpty() && reopened.plan.windows.isEmpty())
            checks
        } finally {
            store?.close()
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keys.containsAlias(alias)) keys.deleteEntry(alias)
            check(folder.deleteRecursively())
        }
    }
}
