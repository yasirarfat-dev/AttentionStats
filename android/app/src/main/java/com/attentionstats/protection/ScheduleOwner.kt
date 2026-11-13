package com.attentionstats.protection

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import org.attentionstats.protection.FocusSession
import android.view.inputmethod.InputMethodManager
import com.attentionstats.storage.HistoryStore
import com.attentionstats.storage.ProtectionConfiguration
import androidx.room3.withWriteTransaction
import org.attentionstats.protection.AppGroup
import org.attentionstats.protection.BlockWindow
import org.attentionstats.protection.BlockScope
import org.attentionstats.protection.SchedulePlan
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

/** Called on the coordinator queue for storage; service reads only immutable published plans. */
internal class ScheduleOwner(private val context: Context) {
    @Volatile var plan = SchedulePlan()
        private set
    @Volatile var revision = 0L
        private set
    private val allowances = ConcurrentHashMap<String, Long>()
    private val bootEpoch by lazy { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }
    val excluded: Set<String> by lazy {
        buildSet {
            addAll(setOf(context.packageName, "attention.stats", "com.attentionstats.debug", "android",
                "com.android.settings", "com.android.systemui", "com.android.phone", "com.android.server.telecom",
                "com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer",
                "com.android.permissioncontroller", "com.google.android.permissioncontroller",
                "com.android.packageinstaller", "com.google.android.packageinstaller"))
            listOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), Intent(Intent.ACTION_DIAL)).forEach { intent ->
                context.packageManager.queryIntentActivities(intent, 0).forEach { add(it.activityInfo.packageName) }
            }
            context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.forEach { add(it.packageName) }
        }
    }

    @Volatile private var launchable: Set<String> = emptySet()
    fun refreshLaunchable() {
        launchable = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }.filter { it !in excluded }.toSet()
    }
    suspend fun load(history: HistoryStore) {
        refreshLaunchable()
        val stored = history.database.data().protection() ?: ProtectionConfiguration()
        val next = decode(stored.document)
        next.validate(excluded)
        if (revision != stored.revision || plan != next) allowances.clear()
        plan = next; revision = stored.revision
    }

    /** Only the user-facing schedule read seeds defaults, never isolated owner loads or collection startup. */
    suspend fun ensureDefaultGroups(history: HistoryStore) {
        val defaults = context.getSharedPreferences("focus-defaults", Context.MODE_PRIVATE)
        if (defaults.getBoolean("seeded-v1", false)) return
        var stored = history.database.data().protection() ?: ProtectionConfiguration()
        if (!defaults.getBoolean("seeded-v1", false)) {
            val current = decode(stored.document)
            val additions = DefaultGroups.create(context, excluded).filter { candidate -> current.groups.none { it.id == candidate.id || it.name.equals(candidate.name, true) } }
                .take((30 - current.groups.size).coerceAtLeast(0))
            var seeded = current
            additions.forEach { candidate ->
                val next = seeded.copy(groups = seeded.groups + candidate)
                if (encode(next).toString().length <= 131_072) seeded = next
            }
            if (seeded != current) {
                seeded.validate(excluded)
                stored = stored.copy(revision = Math.addExact(stored.revision, 1), document = encode(seeded).toString())
                history.database.withWriteTransaction {
                    history.database.data().putProtection(stored)
                    history.database.data().advanceConfigurationRevision()
                }
            }
            check(defaults.edit().putBoolean("seeded-v1", true).commit())
        }
        load(history)
    }

    suspend fun save(history: HistoryStore, document: String, expected: Long): Boolean {
        val next = decode(document).copy(focus = plan.focus)
        next.validate(excluded)
        val dao = history.database.data()
        val current = dao.protection() ?: ProtectionConfiguration()
        if (expected != current.revision) return false
        val encoded = encode(next).toString().also { require(it.length <= 131_072) }
        history.database.withWriteTransaction {
            dao.putProtection(ProtectionConfiguration(revision = Math.addExact(current.revision, 1), document = encoded))
            dao.advanceConfigurationRevision()
        }
        load(history)
        return true
    }

    fun decision(packageName: String, elapsed: Long, scope: BlockScope = BlockScope.APP): List<BlockWindow> {
        if ((allowances[packageName] ?: 0) > elapsed) return emptyList()
        val timed = if (scope == BlockScope.APP && packageName !in excluded &&
            packageName in focusTargets())
            listOf(BlockWindow("focus", "focus", 127, 0, 0, true, allDay = true)) else emptyList()
        return timed + plan.matching(packageName, ZonedDateTime.now(), excluded, scope, launchable)
    }
    fun allow(packageName: String, elapsed: Long) { allowances[packageName] = elapsed + 5 * 60_000 }
    fun reset() { plan = SchedulePlan(); revision = 0; allowances.clear() }

    private fun boot() = bootEpoch
    private fun remaining(focus: FocusSession) = focus.remaining(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot())
    fun activeFocus() = plan.focus?.takeIf { remaining(it) > 0 }
    private fun focusTargets(): Set<String> = activeFocus()?.let { if (it.allowOnly) launchable - it.packages else it.packages } ?: emptySet()
    fun targets() = plan.targets(launchable) + focusTargets()

    suspend fun focus(history: HistoryStore, packages: Set<String>?, minutes: Int, expected: Long, stopId: String?, allowOnly: Boolean = false): Boolean {
        val current = history.database.data().protection() ?: ProtectionConfiguration()
        if (current.revision != expected) return false
        val nextFocus = if (packages == null) {
            require(plan.focus?.id == stopId)
            null
        } else {
            require(activeFocus() == null && minutes in 1..180 && boot() >= 0)
            val wall = System.currentTimeMillis(); val elapsed = SystemClock.elapsedRealtime()
            FocusSession(java.util.UUID.randomUUID().toString(), packages, wall,
                Math.addExact(wall, minutes * 60_000L), elapsed, Math.addExact(elapsed, minutes * 60_000L), boot(), allowOnly)
        }
        val next = plan.copy(focus = nextFocus)
        next.validate(excluded)
        val encoded = encode(next).toString().also { require(it.length <= 131_072) }
        history.database.withWriteTransaction {
            history.database.data().putProtection(ProtectionConfiguration(revision = Math.addExact(current.revision, 1), document = encoded))
            history.database.data().advanceConfigurationRevision()
        }
        load(history)
        return true
    }

    fun snapshot(accessibility: Boolean): String = encode(plan).put("revision", revision.toString())
        .put("accessibility", accessibility).apply {
            val focus = activeFocus()
            put("focus", if (focus == null) JSONObject.NULL else JSONObject()
                .put("id", focus.id).put("packages", JSONArray(focus.packages.sorted())).put("allowOnly", focus.allowOnly)
                .put("durationMs", (focus.deadlineMs - focus.startedMs).toString()).put("remainingMs", remaining(focus).toString()).put("deadlineMs", focus.deadlineMs.toString()))
        }.toString()

    companion object {
        fun decode(document: String): SchedulePlan {
            require(document.length <= 131_072)
            val json = JSONObject(document)
            val keys = json.keys().asSequence().toSet()
            require(keys == setOf("groups", "windows") || keys == setOf("groups", "windows", "focus"))
            val focus = if (!json.has("focus") || json.isNull("focus")) null else json.getJSONObject("focus").let { row ->
                val keys = row.keys().asSequence().toSet()
                val legacy = setOf("id", "packages", "startedMs", "deadlineMs", "elapsedStart", "elapsedDeadline", "boot")
                require(keys == legacy || keys == legacy + "allowOnly")
                if ("allowOnly" in keys) require(row.get("allowOnly") is Boolean)
                val members = row.getJSONArray("packages")
                require(members.length() in 1..100)
                val targets = (0 until members.length()).map { members.getString(it) }
                require(targets.toSet().size == targets.size)
                fun long(key: String): Long = row.getString(key).let { require(it.matches(Regex("0|[1-9][0-9]*"))); it.toLong() }
                require(row.get("boot") is Int)
                FocusSession(row.getString("id"), targets.toSet(), long("startedMs"), long("deadlineMs"),
                    long("elapsedStart"), long("elapsedDeadline"), row.getInt("boot"), row.optBoolean("allowOnly", false))
            }
            val groups = json.getJSONArray("groups")
            val windows = json.getJSONArray("windows")
            require(groups.length() <= 30 && windows.length() <= 100)
            fun integer(row: JSONObject, key: String): Int {
                val number = row.get(key)
                require(number is Int) // No fractional, string or overflowing coercion.
                return number
            }
            return SchedulePlan((0 until groups.length()).map { i ->
                val row = groups.getJSONObject(i)
                require(row.keys().asSequence().toSet() == setOf("id", "name", "packages"))
                val packages = row.getJSONArray("packages")
                require(packages.length() in 1..100)
                val members = (0 until packages.length()).map { packages.getString(it) }
                require(members.toSet().size == members.size)
                AppGroup(row.getString("id"), row.getString("name"), members.toSet())
            }, (0 until windows.length()).map { i ->
                val row = windows.getJSONObject(i)
                val keys = row.keys().asSequence().toSet()
                val legacy = setOf("id", "groupId", "days", "startMinute", "endMinute", "enabled")
                require(keys == legacy || keys == legacy + setOf("scope", "allDay") || keys == legacy + setOf("scope", "allDay", "allowOnly"))
                if ("allowOnly" in keys) require(row.get("allowOnly") is Boolean)
                require(row.get("enabled") is Boolean)
                val scope = if ("scope" in keys) BlockScope.entries.first { it.key == row.getString("scope") } else BlockScope.APP
                val allDay = if ("allDay" in keys) { require(row.get("allDay") is Boolean); row.getBoolean("allDay") } else false
                if (scope == BlockScope.SHORT_VIDEOS) {
                    val group = (0 until groups.length()).map { groups.getJSONObject(it) }.first { it.getString("id") == row.getString("groupId") }
                    val members = group.getJSONArray("packages")
                    require((0 until members.length()).all { members.getString(it) in org.attentionstats.reels.ReelApp.allPackages })
                }
                BlockWindow(row.getString("id"), row.getString("groupId"), integer(row, "days"),
                    integer(row, "startMinute"), integer(row, "endMinute"), row.getBoolean("enabled"), scope, allDay, row.optBoolean("allowOnly", false))
            }, focus)
        }
        fun encode(plan: SchedulePlan): JSONObject = JSONObject().put("groups", JSONArray().apply {
            plan.groups.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("packages", JSONArray(it.packages.sorted()))) }
        }).put("windows", JSONArray().apply {
            plan.windows.forEach { put(JSONObject().put("id", it.id).put("groupId", it.groupId).put("days", it.days)
                .put("startMinute", it.startMinute).put("endMinute", it.endMinute).put("enabled", it.enabled)
                .put("scope", it.scope.key).put("allDay", it.allDay).put("allowOnly", it.allowOnly)) }
        }).apply { plan.focus?.let { focus -> put("focus", JSONObject().put("id", focus.id)
            .put("packages", JSONArray(focus.packages.sorted())).put("startedMs", focus.startedMs.toString())
            .put("deadlineMs", focus.deadlineMs.toString()).put("elapsedStart", focus.elapsedStart.toString())
            .put("elapsedDeadline", focus.elapsedDeadline.toString()).put("boot", focus.boot).put("allowOnly", focus.allowOnly)) } }
    }
}
