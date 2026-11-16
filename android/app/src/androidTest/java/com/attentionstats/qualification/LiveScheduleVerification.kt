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
import com.attentionstats.autoscroll.AutoScrollSession
import android.os.Bundle
import org.attentionstats.protection.AppGroup
import org.attentionstats.protection.BlockWindow
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Task-owned whole-app rule trial on an installed calculator. Does not inspect its content. */
internal object LiveScheduleVerification {
    fun run(instrumentation: Instrumentation, focusOnly: Boolean = false, allowanceExpiry: Boolean = false): String {
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
        val schedules = ReelsCoordinator::class.java.getDeclaredField("schedules").apply {
            isAccessible = true
        }.get(owner) as ScheduleOwner
        val savedFocus = schedules.plan.focus
        if (allowanceExpiry) {
            check(!focusOnly && owner.serviceConnected && !AutoScrollSession.running)
            check(schedules.activeFocus() == null && schedules.decision(pkg, SystemClock.elapsedRealtime()).isEmpty()) {
                "Existing focus/rule preserved; would mask allowance trial"
            }
            check(initial.groups.size < 30 && initial.windows.size <= 98)
            check(android.provider.Settings.System.getLong(context.contentResolver, "screen_off_timeout") >= 360_000)
        }
        if (focusOnly) {
            val focus = before.optJSONObject("focus")
            val packages = focus?.getJSONArray("packages")
            check(packages != null && packages.length() == 1 && packages.getString(0) == pkg) { "expected explicit calculator-only focus" }
            check(initial.matching(pkg, java.time.ZonedDateTime.now(), emptySet()).isEmpty()) { "other schedule would mask focus" }
        }
        val group = AppGroup(UUID.randomUUID().toString(), "Schedule verification", setOf(pkg))
        val window = BlockWindow(UUID.randomUUID().toString(), group.id, 127, 0, 0, true, allDay = true)
        val second = window.copy(id = UUID.randomUUID().toString())
        val ownedWindows = if (allowanceExpiry) listOf(window, second) else listOf(window)
        var added = false
        var primaryFailure: Throwable? = null
        fun calculatorForeground(): Boolean {
            val root = automation.windows.firstOrNull {
                it.isFocused && it.type == AccessibilityWindowInfo.TYPE_APPLICATION
            }?.root ?: return false
            return try { root.packageName?.toString() == pkg } finally { SnapshotReader.recycle(root) }
        }
        try {
            if (!focusOnly) {
                added = true
                save(initial.copy(groups = initial.groups + group, windows = initial.windows + ownedWindows), before.getString("revision").toLong())
            }
            context.startActivity(checkNotNull(context.packageManager.getLaunchIntentForPackage(pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (!allowedButton(false) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(250)
            val allowanceBefore = SystemClock.elapsedRealtime()
            check(allowedButton(true)) { "whole-app cover did not appear" }
            SystemClock.sleep(2_000)
            check(!allowedButton(false)) { "allowance did not dismiss cover" }
            val root = checkNotNull(automation.rootInActiveWindow)
            try { check(root.packageName?.toString() == pkg) { "allowance left selected app" } }
            finally { SnapshotReader.recycle(root) }
            if (allowanceExpiry) {
                val allowanceObserved = SystemClock.elapsedRealtime()
                instrumentation.sendStatus(1, Bundle().apply {
                    putString("stream", "ALLOWANCE_EXPIRY_WAIT: two task-owned overlapping schedules; actual button dismissed cover; real five-minute wait")
                })
                var nextReport = allowanceBefore + 30_000
                val earliest = allowanceBefore + 300_000
                while (SystemClock.elapsedRealtime() < earliest - 500) {
                    check(owner.serviceConnected && owner.grantEnabled() && calculatorForeground()) { "Allowance trial interrupted" }
                    check(!allowedButton(false)) { "Allowance cover returned early" }
                    check(schedules.plan.focus == savedFocus && schedules.plan.groups.contains(group) &&
                        schedules.plan.windows.containsAll(ownedWindows)) { "Trial configuration changed" }
                    if (SystemClock.elapsedRealtime() >= nextReport) {
                        instrumentation.sendStatus(1, Bundle().apply { putString("stream", "ALLOWANCE_EXPIRY_WAIT: ${(SystemClock.elapsedRealtime() - allowanceBefore) / 1000}s; foreground and cover absence verified") })
                        nextReport += 30_000
                    }
                    SystemClock.sleep(250)
                }
                val latest = allowanceObserved + 310_000
                while (!allowedButton(false) && SystemClock.elapsedRealtime() < latest) {
                    check(owner.serviceConnected && owner.grantEnabled() && calculatorForeground()) { "Allowance expiry interrupted" }
                    SystemClock.sleep(100)
                }
                check(allowedButton(false)) { "Allowance expired but cover did not return" }
                val returnAfter = SystemClock.elapsedRealtime() - allowanceBefore
                check(returnAfter >= 300_000 && calculatorForeground()) { "Allowance boundary/foreground mismatch" }
                check(schedules.decision(pkg, SystemClock.elapsedRealtime()).map { it.id }.toSet() == ownedWindows.map { it.id }.toSet())
                check(schedules.plan.focus == savedFocus)
                return "PASS: actual five-minute Calculator allowance expiry with overlapping schedules; cover absent until boundary and returned after ${returnAfter}ms; target content unread; existing focus preserved; only owned rules removed"
            }
            if (focusOnly) return "PASS: actual calculator-only focus cover and Allow 5 minutes; own overlay controls only, target content unread"
            return "PASS: installed non-social app covered by all-day group rule; immediate allowance removes cover and preserves app access; task-owned rule removed"
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            try {
                if (added) {
                    val latest = state(); val current = plan(latest)
                    // Retain all concurrent unowned rule changes and the native saved focus.
                    val ownedIds = ownedWindows.map { it.id }.toSet()
                    check(current.groups.none { it.id == group.id && it != group } &&
                        current.windows.none { it.id in ownedIds && it !in ownedWindows }) { "Owned rule changed; no destructive cleanup" }
                    if (current.groups.any { it.id == group.id } || current.windows.any { it.id in ownedIds }) {
                        save(current.copy(groups = current.groups.filter { it.id != group.id }, windows = current.windows.filter { it.id !in ownedIds }),
                            latest.getString("revision").toLong())
                    }
                    check(schedules.plan.groups.none { it.id == group.id } && schedules.plan.windows.none { it.id in ownedIds })
                    check(schedules.plan.focus == savedFocus)
                }
                if (allowanceExpiry) {
                    context.startActivity(Intent(context, com.attentionstats.MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                        .setData(android.net.Uri.parse("attentionstats://today")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } catch (cleanup: Throwable) {
                val failure = primaryFailure
                if (failure == null) throw cleanup else failure.addSuppressed(cleanup)
            }
        }
    }
}
