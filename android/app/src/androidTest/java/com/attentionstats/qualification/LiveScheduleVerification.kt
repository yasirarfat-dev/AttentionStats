package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.protection.ScheduleOwner
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.protection.AppGroup
import org.attentionstats.protection.BlockWindow
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Task-owned whole-app rule trial on an installed calculator. Does not inspect its content. */
internal object LiveScheduleVerification {
    fun run(instrumentation: Instrumentation, focusOnly: Boolean = false): String {
        val context = instrumentation.targetContext
        val owner = ReelsCoordinator.get(context)
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun state(): JSONObject {
            val done = CountDownLatch(1); var raw: String? = null
            owner.scheduleSnapshot({ raw = it; done.countDown() }, { done.countDown() })
            check(done.await(15, TimeUnit.SECONDS))
            return JSONObject(checkNotNull(raw))
        }
        fun plan(value: JSONObject) = ScheduleOwner.decode(JSONObject().put("groups", value.getJSONArray("groups"))
            .put("windows", value.getJSONArray("windows")).toString())
        fun save(value: org.attentionstats.protection.SchedulePlan, revision: Long) {
            val done = CountDownLatch(1); var success = false
            owner.saveSchedules(ScheduleOwner.encode(value).toString(), revision,
                { success = true; done.countDown() }, { done.countDown() })
            check(done.await(15, TimeUnit.SECONDS) && success) { "schedule save failed" }
        }
        fun allowedButton(click: Boolean): Boolean {
            for (window in automation.windows) {
                if (window.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
                val root = window.root ?: continue
                try {
                    if (root.packageName?.toString() != context.packageName) continue
                    val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
                    try {
                        var visited = 0
                        while (queue.isNotEmpty() && visited++ < 120) {
                            val node = queue.removeFirst()
                            try {
                                if (node.text?.toString() == "Allow 5 minutes" && node.isVisibleToUser) {
                                    if (click) check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                                    return true
                                }
                                for (index in 0 until node.childCount) node.getChild(index)?.let { queue.add(it) }
                            } finally { if (node !== root) SnapshotReader.recycle(node) }
                        }
                    } finally { queue.forEach { if (it !== root) SnapshotReader.recycle(it) } }
                } finally { SnapshotReader.recycle(root) }
            }
            return false
        }
        val pkg = listOf("com.sec.android.app.popupcalculator", "com.google.android.calculator", "com.android.calculator2")
            .firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }
        check(pkg != null) { "safe calculator unavailable" }
        val before = state()
        check(before.getBoolean("accessibility")) { "accessibility unavailable" }
        val initial = plan(before)
        if (focusOnly) {
            val focus = before.optJSONObject("focus")
            val packages = focus?.getJSONArray("packages")
            check(packages != null && packages.length() == 1 && packages.getString(0) == pkg) { "expected explicit calculator-only focus" }
            check(initial.matching(pkg, java.time.ZonedDateTime.now(), emptySet()).isEmpty()) { "other schedule would mask focus" }
        }
        val group = AppGroup(UUID.randomUUID().toString(), "Schedule verification", setOf(pkg))
        val window = BlockWindow(UUID.randomUUID().toString(), group.id, 127, 0, 0, true, allDay = true)
        try {
            if (!focusOnly) save(initial.copy(groups = initial.groups + group, windows = initial.windows + window), before.getString("revision").toLong())
            context.startActivity(checkNotNull(context.packageManager.getLaunchIntentForPackage(pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (!allowedButton(false) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(250)
            check(allowedButton(true)) { "whole-app cover did not appear" }
            SystemClock.sleep(2_000)
            check(!allowedButton(false)) { "allowance did not dismiss cover" }
            val root = checkNotNull(automation.rootInActiveWindow)
            try { check(root.packageName?.toString() == pkg) { "allowance left selected app" } }
            finally { SnapshotReader.recycle(root) }
            if (focusOnly) return "PASS: actual calculator-only focus cover and Allow 5 minutes; own overlay controls only, target content unread"
            return "PASS: installed non-social app covered by all-day group rule; immediate allowance removes cover and preserves app access; task-owned rule removed"
        } finally {
            if (!focusOnly) {
                val latest = state(); val current = plan(latest)
                save(current.copy(groups = current.groups.filter { it.id != group.id }, windows = current.windows.filter { it.id != window.id }),
                    latest.getString("revision").toLong())
            }
        }
    }
}
