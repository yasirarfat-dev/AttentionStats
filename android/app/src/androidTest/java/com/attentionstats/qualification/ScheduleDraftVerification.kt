package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.attentionstats.MainActivity
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.protection.ScheduleOwner
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.reels.ReelApp
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Read-only persistence check using existing groups; never clicks Save or changes memberships. */
@Suppress("DEPRECATION")
internal object ScheduleDraftVerification {
    fun run(instrumentation: Instrumentation): String {
        val context = instrumentation.targetContext
        val owner = ReelsCoordinator.get(context)
        fun snapshot(): JSONObject {
            val done = CountDownLatch(1)
            var value: String? = null
            owner.scheduleSnapshot({ value = it; done.countDown() }, { done.countDown() })
            check(done.await(15, TimeUnit.SECONDS))
            return JSONObject(checkNotNull(value))
        }
        fun plan(value: JSONObject) = ScheduleOwner.decode(JSONObject()
            .put("groups", value.getJSONArray("groups")).put("windows", value.getJSONArray("windows")).toString())
        val before = snapshot()
        val initial = plan(before)
        val choices = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .distinctBy { it.activityInfo.packageName }
        // Below the bridge's catalog bound, source membership is unaffected by recovery exclusions.
        if (choices.size > 500) return "DEFERRED: installed catalog exceeds trial bound; no configuration changed"
        val installed = choices.map { it.activityInfo.packageName }.toSet()
        val supported = initial.groups.firstOrNull { group -> group.packages.all { it in installed && it in ReelApp.allPackages } }
        val unsupported = initial.groups.firstOrNull { group -> group.packages.any { it !in installed || it !in ReelApp.allPackages } }
        if (supported == null || unsupported == null ||
            initial.groups.count { it.name == supported.name } != 1 || initial.groups.count { it.name == unsupported.name } != 1) {
            return "DEFERRED: existing uniquely named supported and unsupported groups required; no configuration changed"
        }
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun root(): AccessibilityNodeInfo? {
            val node = automation.rootInActiveWindow ?: return null
            if (node.packageName?.toString() == context.packageName) return node
            SnapshotReader.recycle(node); return null
        }
        fun visit(label: String?, action: (AccessibilityNodeInfo) -> Boolean): Boolean {
            val window = root() ?: return false
            val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(window)
            try {
                var count = 0
                while (queue.isNotEmpty() && count++ < 900) {
                    val node = queue.removeFirst()
                    try {
                        if (node.packageName?.toString() != context.packageName || node.isEditable) continue
                        if (label == null && node.isScrollable && node.isVisibleToUser) return action(node)
                        if (label != null && (node.text?.toString() == label || node.contentDescription?.toString() == label)) {
                            var control = AccessibilityNodeInfo.obtain(node)
                            try {
                                var depth = 0
                                while (!control.isClickable && control.className?.toString() !in setOf("android.widget.Button", "android.widget.RadioButton") && depth++ < 5) {
                                    val parent = control.parent ?: break
                                    SnapshotReader.recycle(control); control = parent
                                }
                                control.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                                control.refresh()
                                if (control.isVisibleToUser) return action(control)
                            } finally { SnapshotReader.recycle(control) }
                        }
                        for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
                    } finally { if (node !== window) SnapshotReader.recycle(node) }
                }
                return false
            } finally {
                queue.forEach { if (it !== window) SnapshotReader.recycle(it) }
                SnapshotReader.recycle(window)
            }
        }
        fun find(label: String, action: (AccessibilityNodeInfo) -> Boolean) {
            // Search both directions; React Native can remove offscreen descendants.
            for (direction in listOf(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                repeat(12) {
                    if (visit(label, action)) { SystemClock.sleep(250); return }
                    visit(null) { it.performAction(direction) }; SystemClock.sleep(180)
                }
            }
            error("Owned draft control unavailable")
        }
        fun click(label: String) = find(label) { it.isEnabled && it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        var checks = 0
        fun expect(label: String, enabled: Boolean? = null, selected: Boolean? = null) {
            find(label) {
                check(enabled == null || it.isEnabled == enabled) { "draft enabled state ${checks + 1}" }
                check(selected == null || it.isSelected == selected) { "draft selected state ${checks + 1}" }
                true
            }
            checks++
        }
        var draftOpen = false
        try {
            context.startActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("attentionstats://protect/schedules")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            SystemClock.sleep(1_500)
            click("+ Add time slot"); draftOpen = true
            click(supported.name); click("Short videos")
            expect("Short videos", selected = true)
            expect("Save time slot · off", enabled = true)
            click(unsupported.name)
            expect("Short videos", selected = true)
            expect("Save time slot · off", enabled = false)
            click(supported.name)
            expect("Short videos", selected = true)
            expect("Save time slot · off", enabled = true)
            click(unsupported.name); click("Whole app")
            expect("Whole app", selected = true)
            expect("Save time slot · off", enabled = true)
            click("Back to schedules"); draftOpen = false
            val after = snapshot()
            check(plan(after) == initial && after.getString("revision") == before.getString("revision"))
            checks++
            return "PASS: $checks actual unsaved schedule draft checks; selective intent, unsupported save fence, explicit whole-app choice and cancellation; revision/configuration unchanged"
        } finally {
            if (draftOpen) {
                // Own Activity only. Android Back cancels the local draft without persistence.
                check(root()?.let { SnapshotReader.recycle(it); true } == true)
                automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                SystemClock.sleep(300)
            }
            val after = snapshot()
            check(plan(after) == initial && after.getString("revision") == before.getString("revision")) { "Schedule configuration changed during unsaved trial" }
        }
    }
}
