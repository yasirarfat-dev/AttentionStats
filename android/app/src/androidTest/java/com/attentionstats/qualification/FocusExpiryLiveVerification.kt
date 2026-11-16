package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.attentionstats.MainActivity
import com.attentionstats.autoscroll.AutoScrollSession
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.protection.ScheduleOwner
import com.attentionstats.reels.ReelsCoordinator
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Operator-only real one-minute native deadline/renderer trial. Calculator content is never read. */
internal object FocusExpiryLiveVerification {
    fun run(instrumentation: Instrumentation): String {
        val context = instrumentation.targetContext
        val owner = ReelsCoordinator.get(context)
        // Debug and preview mangle the internal getter differently. This exact own-app
        // field is read only by the operator test; no production API or provider reflection.
        val schedules = ReelsCoordinator::class.java.getDeclaredField("schedules").apply {
            isAccessible = true
        }.get(owner) as ScheduleOwner
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun state(): JSONObject {
            val done = CountDownLatch(1); var raw: String? = null
            owner.scheduleSnapshot({ raw = it; done.countDown() }, { done.countDown() })
            check(done.await(15, TimeUnit.SECONDS)) { "focus snapshot timeout" }
            return JSONObject(checkNotNull(raw) { "focus snapshot unavailable" })
        }
        val createdId = AtomicReference<String?>(null)
        fun change(packages: Set<String>?, expected: Long, stopId: String?) {
            val done = CountDownLatch(1); var success = false
            owner.changeFocus(packages, if (packages == null) 0 else 1, expected, stopId, {
                if (packages != null) createdId.set(checkNotNull(schedules.plan.focus).id)
                success = true; done.countDown()
            }, { done.countDown() })
            check(done.await(15, TimeUnit.SECONDS) && success) { "focus change not acknowledged" }
        }
        fun cover(): Boolean {
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
                                if (node.isVisibleToUser && node.text?.toString() == "Allow 5 minutes") return true
                                for (index in 0 until node.childCount) node.getChild(index)?.let { queue.add(it) }
                            } finally { if (node !== root) SnapshotReader.recycle(node) }
                        }
                    } finally { queue.forEach { if (it !== root) SnapshotReader.recycle(it) } }
                } finally { SnapshotReader.recycle(root) }
            }
            return false
        }
        fun calculatorForeground(pkg: String): Boolean {
            val root = automation.windows.firstOrNull {
                it.isFocused && it.type == AccessibilityWindowInfo.TYPE_APPLICATION
            }?.root ?: return false
            return try { root.packageName?.toString() == pkg } finally { SnapshotReader.recycle(root) }
        }
        fun ownLabel(label: String): Boolean {
            val root = automation.windows.firstOrNull {
                it.isFocused && it.type == AccessibilityWindowInfo.TYPE_APPLICATION
            }?.root ?: return false
            try {
                if (root.packageName?.toString() != context.packageName) return false
                val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
                try {
                    var visited = 0
                    while (queue.isNotEmpty() && visited++ < 400) {
                        val node = queue.removeFirst()
                        try {
                            if (node.isVisibleToUser && (node.text?.toString() == label || node.contentDescription?.toString() == label)) return true
                            for (index in 0 until node.childCount) node.getChild(index)?.let { queue.add(it) }
                        } finally { if (node !== root) SnapshotReader.recycle(node) }
                    }
                } finally { queue.forEach { if (it !== root) SnapshotReader.recycle(it) } }
                return false
            } finally { SnapshotReader.recycle(root) }
        }
        fun focusUi(label: String): Boolean {
            context.startActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("attentionstats://protect/focus")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val until = SystemClock.elapsedRealtime() + 12_000
            do {
                if (ownLabel(label)) return true
                SystemClock.sleep(200)
            } while (SystemClock.elapsedRealtime() < until)
            return false
        }
        val before = state()
        check(before.getBoolean("accessibility") && owner.serviceConnected && !AutoScrollSession.running)
        val originalPlan = schedules.plan
        check(originalPlan.focus == null) { "Existing saved focus preserved" }
        val pkg = checkNotNull(listOf("com.sec.android.app.popupcalculator", "com.google.android.calculator", "com.android.calculator2")
            .firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }) { "Safe calculator unavailable" }
        check(schedules.decision(pkg, SystemClock.elapsedRealtime()).isEmpty()) { "Other rule/allowance would mask trial" }
        var checks = 0
        fun expect(value: Boolean) { check(value) { "focus expiry ${checks + 1}" }; checks++ }
        var primaryFailure: Throwable? = null
        try {
            change(setOf(pkg), before.getString("revision").toLong(), null)
            val focus = checkNotNull(schedules.activeFocus())
            expect(focus.id == createdId.get() && focus.packages == setOf(pkg) && !focus.allowOnly)
            expect(focusUi("Active Focus"))
            context.startActivity(checkNotNull(context.packageManager.getLaunchIntentForPackage(pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val appearedBy = SystemClock.elapsedRealtime() + 10_000
            while (!cover() && SystemClock.elapsedRealtime() < appearedBy) SystemClock.sleep(100)
            expect(cover() && schedules.decision(pkg, SystemClock.elapsedRealtime()).singleOrNull()?.id == "focus")
            expect(schedules.decision(context.packageName, SystemClock.elapsedRealtime()).isEmpty() &&
                schedules.decision("com.android.settings", SystemClock.elapsedRealtime()).isEmpty())
            instrumentation.sendStatus(1, Bundle().apply {
                putString("stream", "FOCUS_EXPIRY_COVER_READY: native one-minute debug protocol; actual own cover; no target content reads or allowance")
            })
            var midpoint = false
            while (SystemClock.elapsedRealtime() < focus.elapsedDeadline) {
                check(owner.serviceConnected && owner.grantEnabled() && calculatorForeground(pkg)) { "Focus expiry interrupted" }
                if (!midpoint && SystemClock.elapsedRealtime() >= focus.elapsedStart + 30_000) {
                    expect(cover() && schedules.activeFocus()?.id == focus.id)
                    midpoint = true
                    instrumentation.sendStatus(1, Bundle().apply { putString("stream", "FOCUS_EXPIRY_MIDPOINT: actual cover retained before deadline") })
                }
                SystemClock.sleep(250)
            }
            check(midpoint) { "Midpoint was not observed" }
            expect(schedules.activeFocus() == null && state().isNull("focus") &&
                schedules.decision(pkg, SystemClock.elapsedRealtime()).isEmpty())
            val releasedBy = SystemClock.elapsedRealtime() + 10_000
            while (cover() && SystemClock.elapsedRealtime() < releasedBy) SystemClock.sleep(100)
            expect(!cover())
            expect(calculatorForeground(pkg))
            SystemClock.sleep(2_000)
            expect(!cover() && schedules.activeFocus() == null)
            expect(focusUi("Duration") && !ownLabel("Active Focus") && !ownLabel("End focus now"))
            expect(schedules.plan.groups == originalPlan.groups && schedules.plan.windows == originalPlan.windows)
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            try {
                try {
                    // Remove only the exact created session; retain concurrent rule/user changes.
                    val owned = createdId.get()
                    if (owned != null && schedules.plan.focus?.id == owned) {
                        val latest = state()
                        change(null, latest.getString("revision").toLong(), owned)
                    }
                } finally {
                    context.startActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                        .setData(Uri.parse("attentionstats://today")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } catch (cleanup: Throwable) {
                val failure = primaryFailure
                if (failure == null) throw cleanup else failure.addSuppressed(cleanup)
            }
        }
        expect(schedules.plan == originalPlan && state().isNull("focus"))
        return "PASS: $checks actual native one-minute focus expiry/cover release/recovery exclusion checks; unchanged groups/windows; only owned session removed; calculator content unread"
    }
}
