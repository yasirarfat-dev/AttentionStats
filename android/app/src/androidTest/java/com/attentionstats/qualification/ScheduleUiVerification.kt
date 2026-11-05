package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.attentionstats.MainActivity
import com.attentionstats.appearance.AppearanceOwner
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.protection.ScheduleOwner
import com.attentionstats.reels.ReelsCoordinator
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real in-app editor traversal. Only task-owned rules are written; Graphite is explicitly requested. */
@Suppress("DEPRECATION")
internal object ScheduleUiVerification {
    fun run(instrumentation: Instrumentation): String {
        val context = instrumentation.targetContext
        val owner = ReelsCoordinator.get(context)
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun state(): JSONObject {
            val done = CountDownLatch(1); var raw: String? = null
            owner.scheduleSnapshot({ raw = it; done.countDown() }, { done.countDown() })
            check(done.await(15, TimeUnit.SECONDS)); return JSONObject(checkNotNull(raw))
        }
        fun plan(value: JSONObject) = ScheduleOwner.decode(JSONObject().put("groups", value.getJSONArray("groups"))
            .put("windows", value.getJSONArray("windows")).toString())
        fun ownRoot(): AccessibilityNodeInfo? {
            for (window in automation.windows) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isFocused) continue
                val root = window.root ?: continue
                if (root.packageName?.toString() == context.packageName) return root
                SnapshotReader.recycle(root)
            }
            return null
        }
        fun operate(label: String, value: String? = null): Boolean {
            val root = ownRoot() ?: return false
            val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
            var scroll: AccessibilityNodeInfo? = null
            try {
                var visited = 0
                while (queue.isNotEmpty() && visited++ < 900) {
                    val node = queue.removeFirst()
                    try {
                        if (node.packageName?.toString() != context.packageName) continue
                        if (node.isScrollable && node.isVisibleToUser && scroll == null) scroll = AccessibilityNodeInfo.obtain(node)
                        val match = node.text?.toString() == label || node.contentDescription?.toString() == label
                        if (match && (value != null || !node.isEditable)) {
                            node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                            if (!node.isVisibleToUser || !node.isEnabled) return false
                            if (value != null) {
                                if (!node.isEditable) continue
                                return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                                    Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) })
                            }
                            if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            var parent = node.parent
                            var depth = 0
                            try {
                                while (parent != null && depth++ < 6) {
                                    val current = parent
                                    if (current.packageName?.toString() != context.packageName) return false
                                    if (current.isClickable && current.isEnabled) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                    parent = current.parent; SnapshotReader.recycle(current)
                                }
                            } finally { SnapshotReader.recycle(parent) }
                        }
                        for (index in 0 until node.childCount) node.getChild(index)?.let { queue.add(it) }
                    } finally { if (node !== root) SnapshotReader.recycle(node) }
                }
                scroll?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                return false
            } finally { queue.forEach { if (it !== root) SnapshotReader.recycle(it) }; SnapshotReader.recycle(scroll); SnapshotReader.recycle(root) }
        }
        fun act(label: String, value: String? = null) {
            val deadline = SystemClock.elapsedRealtime() + 12_000
            while (SystemClock.elapsedRealtime() < deadline) {
                if (operate(label, value)) { SystemClock.sleep(450); return }
                SystemClock.sleep(250)
            }
            error("Owned editor control unavailable: $label")
        }
        fun open(path: String) {
            context.startActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("attentionstats://$path")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            SystemClock.sleep(1_000)
        }
        fun capture(name: String) {
            val root = checkNotNull(ownRoot()) { "Own screen required for image" }; SnapshotReader.recycle(root)
            val bitmap = checkNotNull(automation.takeScreenshot())
            try { File(context.cacheDir, "protection-ui-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
        }
        val initial = plan(state())
        val name = "Quiet hours ${UUID.randomUUID().toString().take(6)}"
        var checks = 0
        fun expect(value: Boolean) { check(value) { "schedule UI check ${checks + 1}" }; checks++ }
        try {
            open("settings")
            act("Settings")
            act("Graphite")
            expect(AppearanceOwner.snapshot(context).getString("accent") == "graphite")
            instrumentation.sendStatus(1, Bundle().apply { putString("stream", "Graphite selected on phone") })
            capture("appearance")
            open("protect/schedules")
            capture("empty")
            act("+ Create app group")
            act("Group name", name)
            act("Search apps", "YouTube")
            act("YouTube")
            act("Review group")
            capture("group-review")
            act("Save app group")
            val group = plan(state()).groups.single { it.name == name && initial.groups.none { old -> old.id == it.id } }
            expect(group.packages == setOf("com.google.android.youtube"))
            instrumentation.sendStatus(1, Bundle().apply { putString("stream", "App-group selection and save verified") })
            act("+ Add time slot")
            act(name)
            act("Short videos")
            capture("slot-editor")
            act("Save time slot · off")
            var windows = plan(state()).windows.filter { it.groupId == group.id }
            expect(windows.size == 1 && windows.single().startMinute == 300 && windows.single().endMinute == 660 &&
                windows.single().scope == org.attentionstats.protection.BlockScope.SHORT_VIDEOS && !windows.single().enabled)
            act("+ Add time slot")
            act(name)
            act("Whole app")
            act("Always")
            act("Save time slot · off")
            windows = plan(state()).windows.filter { it.groupId == group.id }
            expect(windows.size == 2 && windows.any { it.allDay && it.startMinute == 0 && it.endMinute == 0 } && windows.none { it.enabled })
            instrumentation.sendStatus(1, Bundle().apply { putString("stream", "Custom/selective and all-day/whole-app slots saved off") })
            capture("overview")
            act("$name, off")
            act("Back to schedules")
            expect(plan(state()).windows.filter { it.groupId == group.id }.none { it.enabled })
            act("Remove")
            act("Remove")
            expect(plan(state()).windows.count { it.groupId == group.id } == 1)
            open("protect/schedules?packageName=com.google.android.youtube")
            capture("per-app")
            expect(ownRoot()?.let { SnapshotReader.recycle(it); true } == true)
            val backup = instrumentation.startActivitySync(Intent(context, com.attentionstats.backup.BackupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            try {
                // Own informational page only; restore FLAG_SECURE immediately after the private visual capture.
                instrumentation.runOnMainSync { backup.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
                SystemClock.sleep(500); capture("backup")
            } finally {
                instrumentation.runOnMainSync { backup.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE); backup.finish() }
            }
            val widgetIds = android.appwidget.AppWidgetManager.getInstance(context)
                .getAppWidgetIds(android.content.ComponentName(context, com.attentionstats.widget.TodayWidget::class.java))
            if (widgetIds.isNotEmpty()) {
                val widget = instrumentation.startActivitySync(Intent(context, com.attentionstats.widget.WidgetConfigureActivity::class.java)
                    .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetIds.first()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                try { SystemClock.sleep(500); capture("widget") }
                finally { instrumentation.runOnMainSync { widget.finish() } }
            }
            return "PASS: $checks real schedule UI/persistence checks; Graphite selected; group search/review, scoped custom and all-day slots, save-off, cancelled activation, deletion and per-app route; task-owned rules removed"
        } finally {
            val latest = state(); val current = plan(latest)
            val owned = current.groups.filter { it.name == name && initial.groups.none { old -> old.id == it.id } }.map { it.id }.toSet()
            if (owned.isNotEmpty()) {
                val next = current.copy(groups = current.groups.filter { it.id !in owned }, windows = current.windows.filter { it.groupId !in owned })
                val done = CountDownLatch(1); var success = false
                owner.saveSchedules(ScheduleOwner.encode(next).toString(), latest.getString("revision").toLong(),
                    { success = true; done.countDown() }, { done.countDown() })
                check(done.await(15, TimeUnit.SECONDS) && success) { "task rule cleanup failed" }
            }
        }
    }
}
