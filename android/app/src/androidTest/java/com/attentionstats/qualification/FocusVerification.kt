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
import org.attentionstats.protection.AppGroup
import org.attentionstats.protection.BlockWindow
import org.attentionstats.protection.BlockScope
import org.attentionstats.protection.SchedulePlan

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
            val launchable = target.packageManager.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName }.filter { it !in reopened.excluded }.distinct()
            check(launchable.size >= 2)
            val allowed = launchable.first(); val blocked = launchable[1]
            expect(reopened.focus(history, setOf(allowed), 1, 3, null, true))
            val allowing = checkNotNull(reopened.activeFocus())
            expect(allowing.allowOnly && allowing.packages == setOf(allowed))
            expect(reopened.decision(allowed, SystemClock.elapsedRealtime()).isEmpty())
            expect(reopened.decision(blocked, SystemClock.elapsedRealtime()).isNotEmpty())
            expect(reopened.decision("com.android.settings", SystemClock.elapsedRealtime()).isEmpty())
            expect(JSONObject(reopened.snapshot(true)).getJSONObject("focus").getBoolean("allowOnly"))
            val again = ScheduleOwner(context); again.load(history)
            expect(again.activeFocus()?.allowOnly == true && again.activeFocus()?.id == allowing.id)
            expect(again.decision(blocked, SystemClock.elapsedRealtime()).isNotEmpty())
            again.allow(blocked, SystemClock.elapsedRealtime())
            expect(again.decision(blocked, SystemClock.elapsedRealtime()).isEmpty())
            expect(again.focus(history, null, 0, 4, allowing.id))
            expect(again.activeFocus() == null && again.targets().isEmpty())

            // Persist overlapping rules in this UUID-owned store only. Decision-time
            // arguments exercise allowance boundaries; the device clock never changes.
            val overlap = SchedulePlan(listOf(
                AppGroup("both", "Synthetic overlap", setOf(allowed, blocked)),
                AppGroup("blocked", "Synthetic second rule", setOf(blocked))
            ), listOf(
                BlockWindow("a", "both", 127, 0, 0, true, allDay = true),
                BlockWindow("b", "blocked", 127, 0, 0, true, allDay = true)
            ))
            expect(again.save(history, ScheduleOwner.encode(overlap).toString(), 5))
            expect(again.focus(history, setOf(blocked), 1, 6, null))
            val overlappingFocus = checkNotNull(again.activeFocus())
            val now = SystemClock.elapsedRealtime()
            expect(again.decision(blocked, now).map { it.id } == listOf("focus", "a", "b"))
            expect(again.decision(allowed, now).map { it.id } == listOf("a"))
            expect(again.decision(blocked, now, BlockScope.SHORT_VIDEOS).isEmpty())
            expect(again.decision(target.packageName, now).isEmpty() &&
                again.decision("com.android.settings", now).isEmpty())
            again.allow(blocked, now)
            expect(again.decision(blocked, now).isEmpty())
            expect(again.decision(allowed, now).map { it.id } == listOf("a"))
            expect(again.decision(blocked, now + 299_999).isEmpty())
            expect(again.decision(blocked, now + 300_000).map { it.id } == listOf("focus", "a", "b"))
            expect(again.decision(blocked, now + 300_001).size == 3)
            expect(again.plan.groups == overlap.groups && again.plan.windows == overlap.windows &&
                again.activeFocus()?.id == overlappingFocus.id)
            expect(!again.focus(history, null, 0, 6, overlappingFocus.id))
            expect(again.activeFocus()?.id == overlappingFocus.id)
            expect(again.focus(history, null, 0, 7, overlappingFocus.id))
            expect(again.activeFocus() == null && again.decision(blocked, now).map { it.id } == listOf("a", "b"))
            expect(again.plan.groups == overlap.groups && again.plan.windows == overlap.windows)

            // Allow Only membership must not waive an independent schedule.
            expect(again.focus(history, setOf(allowed), 1, 8, null, true))
            val inverseFocus = checkNotNull(again.activeFocus())
            expect(again.decision(allowed, now).map { it.id } == listOf("a"))
            expect(again.decision(blocked, now).map { it.id } == listOf("focus", "a", "b"))
            again.allow(blocked, now)
            expect(again.decision(blocked, now + 299_999).isEmpty())
            expect(again.decision(allowed, now).map { it.id } == listOf("a"))
            expect(again.decision(blocked, now + 300_000).size == 3)
            val persisted = ScheduleOwner(context); persisted.load(history)
            expect(persisted.activeFocus()?.id == inverseFocus.id && persisted.plan.groups == overlap.groups &&
                persisted.plan.windows == overlap.windows)
            expect(persisted.decision(blocked, now).size == 3) // Transient allowances do not survive reopening.
            expect(persisted.decision(allowed, now).map { it.id } == listOf("a"))
            expect(persisted.focus(history, null, 0, 9, inverseFocus.id))
            expect(persisted.decision(blocked, now).map { it.id } == listOf("a", "b") &&
                persisted.decision(allowed, now).map { it.id } == listOf("a"))
            expect(persisted.plan.focus == null && persisted.plan.groups == overlap.groups &&
                persisted.plan.windows == overlap.windows && persisted.revision == 10L)
            checks
        } finally {
            store?.close()
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keys.containsAlias(alias)) keys.deleteEntry(alias)
            check(folder.deleteRecursively())
        }
    }
}
