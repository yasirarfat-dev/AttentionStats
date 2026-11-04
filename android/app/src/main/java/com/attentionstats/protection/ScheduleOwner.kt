package com.attentionstats.protection

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import com.attentionstats.storage.HistoryStore
import com.attentionstats.storage.ProtectionConfiguration
import androidx.room3.withWriteTransaction
import org.attentionstats.protection.AppGroup
import org.attentionstats.protection.BlockWindow
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

    suspend fun load(history: HistoryStore) {
        val stored = history.database.data().protection() ?: ProtectionConfiguration()
        val next = decode(stored.document)
        next.validate(excluded)
        if (revision != stored.revision || plan != next) allowances.clear()
        plan = next; revision = stored.revision
    }

    suspend fun save(history: HistoryStore, document: String, expected: Long): Boolean {
        val next = decode(document)
        next.validate(excluded)
        val dao = history.database.data()
        val current = dao.protection() ?: ProtectionConfiguration()
        if (expected != current.revision) return false
        history.database.withWriteTransaction {
            dao.putProtection(ProtectionConfiguration(revision = Math.addExact(current.revision, 1), document = encode(next).toString()))
            dao.advanceConfigurationRevision()
        }
        load(history)
        return true
    }

    fun decision(packageName: String, elapsed: Long): List<BlockWindow> {
        if ((allowances[packageName] ?: 0) > elapsed) return emptyList()
        return plan.matching(packageName, ZonedDateTime.now(), excluded)
    }
    fun allow(packageName: String, elapsed: Long) { allowances[packageName] = elapsed + 5 * 60_000 }
    fun reset() { plan = SchedulePlan(); revision = 0; allowances.clear() }

    fun snapshot(accessibility: Boolean): String = encode(plan).put("revision", revision.toString())
        .put("accessibility", accessibility).toString()

    companion object {
        fun decode(document: String): SchedulePlan {
            require(document.length <= 131_072)
            val json = JSONObject(document)
            require(json.keys().asSequence().toSet() == setOf("groups", "windows"))
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
                require(row.keys().asSequence().toSet() == setOf("id", "groupId", "days", "startMinute", "endMinute", "enabled"))
                require(row.get("enabled") is Boolean)
                BlockWindow(row.getString("id"), row.getString("groupId"), integer(row, "days"),
                    integer(row, "startMinute"), integer(row, "endMinute"), row.getBoolean("enabled"))
            })
        }
        fun encode(plan: SchedulePlan): JSONObject = JSONObject().put("groups", JSONArray().apply {
            plan.groups.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("packages", JSONArray(it.packages.sorted()))) }
        }).put("windows", JSONArray().apply {
            plan.windows.forEach { put(JSONObject().put("id", it.id).put("groupId", it.groupId).put("days", it.days)
                .put("startMinute", it.startMinute).put("endMinute", it.endMinute).put("enabled", it.enabled)) }
        })
    }
}
