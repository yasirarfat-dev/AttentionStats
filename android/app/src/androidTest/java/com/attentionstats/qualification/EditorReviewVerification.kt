package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.attentionstats.protection.ScheduleOwner
import com.attentionstats.reels.ReelsCoordinator
import com.attentionstats.detection.SnapshotReader
import org.attentionstats.protection.AppGroup
import org.attentionstats.protection.SchedulePlan
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual RN review/save/edit/activation/removal. Never creates or ends focus. */
@Suppress("DEPRECATION")
internal object EditorReviewVerification {
    fun run(instrumentation: Instrumentation, open: (String) -> Unit, click: (String) -> Unit,
        find: (String, (AccessibilityNodeInfo) -> Boolean) -> Unit, viewport: () -> Rect): Int {
        val context = instrumentation.targetContext
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val owner = ReelsCoordinator.get(context)
        val schedules = owner.javaClass.getDeclaredField("schedules").apply { isAccessible = true }.get(owner) as ScheduleOwner
        check(owner.serviceConnected && !com.attentionstats.autoscroll.AutoScrollSession.running)
        check(context.getSharedPreferences("focus-defaults", 0).getBoolean("seeded-v1", false))
        check(context.getSystemService(android.os.PowerManager::class.java).isInteractive &&
            !context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
        fun state(): Pair<SchedulePlan, Long> {
            val done = CountDownLatch(1); var captured: Pair<SchedulePlan, Long>? = null
            owner.scheduleSnapshot({ captured = schedules.plan to schedules.revision; done.countDown() }, { done.countDown() })
            check(done.await(15, TimeUnit.SECONDS))
            return checkNotNull(captured)
        }
        fun save(plan: SchedulePlan, expected: Long) {
            val done = CountDownLatch(1); var success = false
            owner.saveSchedules(ScheduleOwner.encode(plan).toString(), expected,
                { success = true; done.countDown() }, { done.countDown() })
            check(done.await(15, TimeUnit.SECONDS) && success)
        }
        val baseline = state()
        val original = baseline.first
        check(original.groups.size < 30 && original.windows.size < 100)
        val pkg = listOf("com.sec.android.app.popupcalculator", "com.google.android.calculator", "com.android.calculator2")
            .first { context.packageManager.getLaunchIntentForPackage(it) != null && it !in schedules.excluded }
        val token = UUID.randomUUID().toString()
        val group = AppGroup(token, "UI ${token.take(8)}", setOf(pkg))
        var added = false
        var checks = 0
        var failure: Throwable? = null
        fun preserved(): Boolean = schedules.plan.copy(
            groups = schedules.plan.groups.filterNot { it.id == token },
            windows = schedules.plan.windows.filterNot { it.groupId == token }) == original
        fun waitFor(condition: () -> Boolean) {
            val until = SystemClock.elapsedRealtime() + 12_000
            while (!condition() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100)
            check(condition()) { "Owned editor state did not settle" }; checks++
            check(preserved()) { "Unowned plan/focus changed" }; checks++
        }
        fun visible(label: String) {
            find(label) { node ->
                val bounds = Rect().also(node::getBoundsInScreen)
                val actionHeight = if (label in setOf("Save time slot · off", "Enable time slot", "Remove")) 52 else 0
                !bounds.isEmpty && bounds.height() >= actionHeight * context.resources.displayMetrics.density - 2 && viewport().contains(bounds)
            }; checks++
        }
        fun filterPicker() {
            find("Search Focus Apps") { node -> node.isEditable && node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, group.name) }) }
        }
        fun capture(name: String) {
            val output = java.io.File(context.cacheDir, "editor-review-${context.resources.configuration.fontScale}-$name.png")
            check(!output.exists())
            check(automation.rootInActiveWindow?.let { root ->
                try { root.packageName?.toString() == context.packageName } finally { SnapshotReader.recycle(root) }
            } == true)
            val bounds = viewport()
            val bitmap = checkNotNull(automation.takeScreenshot())
            try {
                check(automation.rootInActiveWindow?.let { root ->
                    try { root.packageName?.toString() == context.packageName } finally { SnapshotReader.recycle(root) }
                } == true) { "Own app left foreground; capture discarded" }
                val crop = android.graphics.Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width(), bounds.height())
                try { output.outputStream().use { check(crop.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) } }
                finally { if (crop !== bitmap) crop.recycle() }
            } finally { bitmap.recycle() }
        }
        try {
            val journal = java.io.File(context.cacheDir, "editor-review-$token.json")
            check(!journal.exists()); journal.writeText(ScheduleOwner.encode(original).toString())
            added = true; save(original.copy(groups = original.groups + group), baseline.second)
            open("protect"); click("Scheduled Focus"); click("By App Groups")
            click("Add time slot for ${group.name}")
            // Filter only presentation; initial group selection remains selected.
            filterPicker()
            click("Always")
            visible("Review Time Slot"); visible("All day"); visible("Every day · Saves Off")
            visible("${group.name} · Whole App"); visible("Save time slot · off"); capture("save-off")
            click("Save time slot · off")
            waitFor { schedules.plan.windows.count { it.groupId == token } == 1 }
            val slot = schedules.plan.windows.single { it.groupId == token }
            check(!slot.enabled && slot.allDay && slot.days == 127 && !slot.allowOnly && slot.scope.key == "app"); checks++
            visible(group.name)
            click("Edit time slot for ${group.name}: All day, Every day, Off")
            visible("Edit time slot")
            filterPicker()
            // Editing retains selected weekdays/timing and re-saves Off.
            find("All days") { it.isCheckable && it.isChecked }; checks++
            click("Mon")
            visible("Save time slot · off"); click("Save time slot · off")
            waitFor { schedules.plan.windows.single { it.id == slot.id }.days == 126 }
            check(!schedules.plan.windows.single { it.id == slot.id }.enabled); checks++
            // Switch to slots and operate only the unique owned switch; cancel first.
            click("By Time Slots"); click(group.name)
            visible("Ready for a little space?"); visible("Enable time slot"); capture("activation")
            click("Back to schedules")
            check(!schedules.plan.windows.single { it.id == slot.id }.enabled); checks++
            click(group.name); click("Enable time slot")
            waitFor { schedules.plan.windows.single { it.id == slot.id }.enabled }
            click(group.name)
            waitFor { !schedules.plan.windows.single { it.id == slot.id }.enabled }
            click("By App Groups"); click("Remove ${group.name}")
            visible("Remove this rule?"); visible("This also removes all time slots for the group.")
            visible("Remove"); capture("removal")
            click("Remove")
            waitFor { schedules.plan.groups.none { it.id == token } && schedules.plan.windows.none { it.groupId == token } }
            check(schedules.plan == original); checks++
        } catch (error: Throwable) { failure = error; throw error }
        finally {
            if (added) try {
                val currentState = state()
                val current = currentState.first
                check(current.groups.filter { it.id == token }.all { it == group }) { "Owned group changed; no blind cleanup" }
                if (current.groups.any { it.id == token }) save(current.copy(
                    groups = current.groups.filterNot { it.id == token }, windows = current.windows.filterNot { it.groupId == token }), currentState.second)
                check(preserved()); checks++
                instrumentation.sendStatus(1, Bundle().apply { putString("stream", "Owned editor cleanup: unowned plan and saved focus unchanged") })
                check(java.io.File(context.cacheDir, "editor-review-$token.json").delete())
            } catch (cleanup: Throwable) { if (failure == null) throw cleanup else failure.addSuppressed(cleanup) }
        }
        return checks
    }
}
