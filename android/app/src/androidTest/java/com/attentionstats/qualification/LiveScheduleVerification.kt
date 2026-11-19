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
    fun run(instrumentation: Instrumentation, focusOnly: Boolean = false, allowanceExpiry: Boolean = false,
        recoveryAccess: Boolean = false, mutationLive: Boolean = false, boundary: String? = null): String {
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
        fun allowedButton(click: Boolean, label: String = "Allow 5 min"): Boolean {
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
                                if (node.text?.toString() == label && node.isVisibleToUser) {
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
        if (allowanceExpiry || recoveryAccess || mutationLive || boundary != null) {
            check(!focusOnly && owner.serviceConnected && !AutoScrollSession.running)
            check(context.getSystemService(android.os.PowerManager::class.java).isInteractive &&
                !context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) { "Unlocked interactive phone required" }
            check(schedules.activeFocus() == null && schedules.decision(pkg, SystemClock.elapsedRealtime()).isEmpty()) {
                "Existing focus/rule preserved; would mask allowance trial"
            }
            check(initial.groups.size < 30 && initial.windows.size <= 98)
            if (allowanceExpiry) check(android.provider.Settings.System.getLong(context.contentResolver, "screen_off_timeout") >= 360_000)
        }
        if (focusOnly) {
            val focus = before.optJSONObject("focus")
            val packages = focus?.getJSONArray("packages")
            check(packages != null && packages.length() == 1 && packages.getString(0) == pkg) { "expected explicit calculator-only focus" }
            check(initial.matching(pkg, java.time.ZonedDateTime.now(), emptySet()).isEmpty()) { "other schedule would mask focus" }
        }
        check(boundary == null || boundary in setOf("block", "allow-only"))
        val clock = if (boundary == "allow-only") listOf("com.sec.android.app.clockpackage", "com.google.android.deskclock", "com.android.deskclock")
            .firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null && it !in schedules.excluded }
            .also { check(it != null) { "Safe allowed Clock unavailable" } } else null
        val start = java.time.ZonedDateTime.now().plusMinutes(1).withSecond(0).withNano(0).let {
            if (it.toInstant().toEpochMilli() - System.currentTimeMillis() < 20_000) it.plusMinutes(1) else it
        }
        val end = start.plusMinutes(1)
        val wallAnchor = System.currentTimeMillis(); val elapsedAnchor = SystemClock.elapsedRealtime()
        if (boundary != null) {
            check(initial.windows.none { it.enabled }) { "Existing enabled schedules preserved; would mask boundary trial" }
            check(android.provider.Settings.System.getLong(context.contentResolver, "screen_off_timeout") >= 240_000)
            check(pkg !in schedules.excluded && (clock == null || schedules.decision(clock, elapsedAnchor).isEmpty()))
        }
        val group = AppGroup(UUID.randomUUID().toString(), "Schedule verification", setOf(clock ?: pkg))
        val window = if (boundary == null) BlockWindow(UUID.randomUUID().toString(), group.id, 127, 0, 0, true, allDay = true)
            else BlockWindow(UUID.randomUUID().toString(), group.id, 127, start.hour * 60 + start.minute,
                end.hour * 60 + end.minute, true, allowOnly = clock != null)
        val second = window.copy(id = UUID.randomUUID().toString())
        val ownedWindows = if (allowanceExpiry || recoveryAccess || mutationLive) listOf(window, second) else listOf(window)
        var added = false
        var primaryFailure: Throwable? = null
        fun foregroundPackage(): String? {
            val root = automation.windows.firstOrNull {
                it.isFocused && it.type == AccessibilityWindowInfo.TYPE_APPLICATION
            }?.root ?: return null
            return try { root.packageName?.toString() } finally { SnapshotReader.recycle(root) }
        }
        fun calculatorForeground() = foregroundPackage() == pkg
        fun awaitState(condition: () -> Boolean) {
            val until = SystemClock.elapsedRealtime() + 10_000
            while (!condition() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100)
            check(condition()) { "Recovery foreground/cover state not reached" }
        }
        try {
            if (!focusOnly) {
                added = true
                save(initial.copy(groups = initial.groups + group, windows = initial.windows + ownedWindows), before.getString("revision").toLong())
            }
            context.startActivity(checkNotNull(context.packageManager.getLaunchIntentForPackage(pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            if (boundary != null) {
                fun invariant() {
                    check(owner.serviceConnected && owner.grantEnabled() && !AutoScrollSession.running)
                    check(context.getSystemService(android.os.PowerManager::class.java).isInteractive &&
                        !context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
                    check(java.time.ZoneId.systemDefault() == start.zone)
                    check(kotlin.math.abs((System.currentTimeMillis() - wallAnchor) - (SystemClock.elapsedRealtime() - elapsedAnchor)) < 2_000) { "Clock changed" }
                    check(schedules.plan.focus == savedFocus && schedules.plan.groups == initial.groups + group &&
                        schedules.plan.windows == initial.windows + window) { "Configuration changed during boundary trial" }
                }
                fun waitUntil(wall: Long, covered: Boolean) {
                    while (System.currentTimeMillis() < wall) {
                        invariant(); check(calculatorForeground() && allowedButton(false) == covered) { "Early cover transition/foreground loss" }
                        SystemClock.sleep(100)
                    }
                }
                awaitState { calculatorForeground() && !allowedButton(false) }
                check(System.currentTimeMillis() < start.toInstant().toEpochMilli() - 5_000)
                instrumentation.sendStatus(1, Bundle().apply { putString("stream", "BOUNDARY_WAIT: $boundary; one-minute slot; pre-start Calculator uncovered") })
                waitUntil(start.toInstant().toEpochMilli() - 200, false)
                awaitState { calculatorForeground() && allowedButton(false) }
                val startLate = System.currentTimeMillis() - start.toInstant().toEpochMilli()
                check(startLate in 0..10_000)
                check(schedules.decision(pkg, SystemClock.elapsedRealtime()).map { it.id } == listOf(window.id))
                instrumentation.sendStatus(1, Bundle().apply { putString("stream", "BOUNDARY_ACTIVE: $boundary; actual cover; start lateness=${startLate}ms") })
                if (clock != null) {
                    context.startActivity(checkNotNull(context.packageManager.getLaunchIntentForPackage(clock)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    awaitState { foregroundPackage() == clock && !allowedButton(false) }
                    val settled = SystemClock.elapsedRealtime() + 2_000
                    while (SystemClock.elapsedRealtime() < settled) {
                        invariant(); check(foregroundPackage() == clock && !allowedButton(false))
                        check(schedules.decision(clock, SystemClock.elapsedRealtime()).isEmpty()); SystemClock.sleep(100)
                    }
                    context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    awaitState { foregroundPackage() == "com.android.settings" && !allowedButton(false) }
                    check(schedules.decision("com.android.settings", SystemClock.elapsedRealtime()).isEmpty())
                    context.startActivity(checkNotNull(context.packageManager.getLaunchIntentForPackage(pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    awaitState { calculatorForeground() && allowedButton(false) }
                }
                waitUntil(end.toInstant().toEpochMilli() - 200, true)
                awaitState { calculatorForeground() && !allowedButton(false) }
                val endLate = System.currentTimeMillis() - end.toInstant().toEpochMilli()
                check(endLate in 0..10_000)
                check(schedules.decision(pkg, SystemClock.elapsedRealtime()).isEmpty())
                val settled = SystemClock.elapsedRealtime() + 2_000
                while (SystemClock.elapsedRealtime() < settled) {
                    invariant(); check(calculatorForeground() && !allowedButton(false)); SystemClock.sleep(100)
                }
                return "PASS: actual one-minute group slot $boundary; pre-start uncovered, automatic cover start lateness=${startLate}ms, automatic expiry lateness=${endLate}ms without allowance/navigation; " +
                    (if (clock != null) "allowed Clock and recovery Settings uncovered; Calculator reblocked; " else "") +
                    "saved focus/unowned plan preserved; only owned rule removed"
            }
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (!allowedButton(false) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(250)
            if (mutationLive) {
                val ownedIds = ownedWindows.map { it.id }.toSet()
                var stage = 0
                fun assertRendered(expected: Set<String>) {
                    instrumentation.sendStatus(1, Bundle().apply {
                        putString("stream", "MUTATION_STAGE: ${++stage}; expected reasons=${expected.size}")
                    })
                    try { awaitState { calculatorForeground() && allowedButton(false) == expected.isNotEmpty() } }
                    catch (failure: IllegalStateException) {
                        error("Mutation stage=$stage foregroundCalculator=${calculatorForeground()} cover=${allowedButton(false)} reasons=${schedules.decision(pkg, SystemClock.elapsedRealtime()).size} bound=${owner.serviceConnected}")
                    }
                    // Observe the settled state, not just the first renderer callback.
                    val until = SystemClock.elapsedRealtime() + 2_000
                    do {
                        check(owner.serviceConnected && owner.grantEnabled() && !AutoScrollSession.running)
                        check(calculatorForeground() && allowedButton(false) == expected.isNotEmpty())
                        check(schedules.plan.focus == savedFocus && schedules.plan.groups.contains(group))
                        check(schedules.decision(pkg, SystemClock.elapsedRealtime()).map { it.id }.toSet() == expected)
                        SystemClock.sleep(100)
                    } while (SystemClock.elapsedRealtime() < until)
                }
                fun changeOwned(next: List<BlockWindow>) {
                    val latest = state(); val current = plan(latest)
                    check(current.groups.single { it.id == group.id } == group)
                    check(current.windows.filter { it.id in ownedIds }.all { item ->
                        ownedWindows.any { it == item || it.copy(enabled = false) == item }
                    }) { "Owned rule changed unexpectedly" }
                    val retained = current.windows.filter { it.id !in ownedIds }
                    save(current.copy(windows = retained + next), latest.getString("revision").toLong())
                    check(schedules.plan.windows.filter { it.id !in ownedIds } == retained)
                    check(schedules.plan.windows.filter { it.id in ownedIds } == next)
                }
                assertRendered(ownedIds)
                changeOwned(listOf(window.copy(enabled = false), second))
                assertRendered(setOf(second.id))
                changeOwned(ownedWindows.map { it.copy(enabled = false) })
                assertRendered(emptySet())
                changeOwned(ownedWindows)
                assertRendered(ownedIds)
                changeOwned(listOf(second))
                assertRendered(setOf(second.id))
                changeOwned(emptyList())
                assertRendered(emptySet())
                return "PASS: actual overlapping-schedule live disable/re-enable/removal; six settled Calculator cover/reason states; foreground retained without allowance; saved focus preserved; only owned rules removed"
            }
            if (recoveryAccess) {
                fun assertRules() {
                    check(owner.serviceConnected && owner.grantEnabled() && !AutoScrollSession.running)
                    check(schedules.plan.focus == savedFocus && schedules.plan.groups.contains(group) &&
                        schedules.plan.windows.containsAll(ownedWindows))
                    check(schedules.decision(pkg, SystemClock.elapsedRealtime()).map { it.id }.toSet() == ownedWindows.map { it.id }.toSet())
                }
                fun returnToBlock() {
                    context.startActivity(checkNotNull(context.packageManager.getLaunchIntentForPackage(pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    awaitState { calculatorForeground() && allowedButton(false) }
                    assertRules()
                }
                awaitState { calculatorForeground() && allowedButton(false) }
                assertRules()
                check(allowedButton(true, "Open Attention Stats")) { "Recovery button unavailable" }
                awaitState { foregroundPackage() == context.packageName && !allowedButton(false) }
                SystemClock.sleep(2_000)
                check(foregroundPackage() == context.packageName && !allowedButton(false))
                check(schedules.decision(context.packageName, SystemClock.elapsedRealtime()).isEmpty())
                returnToBlock()
                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                awaitState { foregroundPackage() == "com.android.settings" && !allowedButton(false) }
                SystemClock.sleep(2_000)
                check(foregroundPackage() == "com.android.settings" && !allowedButton(false))
                check(schedules.decision("com.android.settings", SystemClock.elapsedRealtime()).isEmpty())
                returnToBlock()
                // Real system Home navigation; never traverse launcher nodes or read labels.
                check(automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME))
                awaitState { foregroundPackage()?.let { it in schedules.excluded && it != context.packageName && it != "com.android.settings" } == true && !allowedButton(false) }
                val home = checkNotNull(foregroundPackage())
                SystemClock.sleep(2_000)
                check(foregroundPackage() == home && !allowedButton(false) && schedules.decision(home, SystemClock.elapsedRealtime()).isEmpty())
                returnToBlock()
                return "PASS: actual overlapping-schedule recovery access; cover Open Attention Stats, Android Settings and system Home remain uncovered; three Calculator returns restore both rule reasons; target content unread; saved focus preserved; only owned rules removed"
            }
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
            if (focusOnly) return "PASS: actual calculator-only focus cover and Allow 5 min; own overlay controls only, target content unread"
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
                        current.windows.none { item -> item.id in ownedIds && ownedWindows.none { it == item || (mutationLive && it.copy(enabled = false) == item) } }) { "Owned rule changed; no destructive cleanup" }
                    if (current.groups.any { it.id == group.id } || current.windows.any { it.id in ownedIds }) {
                        save(current.copy(groups = current.groups.filter { it.id != group.id }, windows = current.windows.filter { it.id !in ownedIds }),
                            latest.getString("revision").toLong())
                    }
                    check(schedules.plan.groups.none { it.id == group.id } && schedules.plan.windows.none { it.id in ownedIds })
                    check(schedules.plan.focus == savedFocus)
                }
                if (allowanceExpiry || recoveryAccess || mutationLive || boundary != null) {
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
