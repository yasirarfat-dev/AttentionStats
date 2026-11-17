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
        find: (String, (AccessibilityNodeInfo) -> Boolean) -> Unit, viewport: () -> Rect, customClock: Boolean = false): Int {
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
            val output = java.io.File(context.cacheDir, "${if (customClock) "editor-clock" else "editor-review"}-${context.resources.configuration.fontScale}-$name.png")
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
            if (customClock) {
                fun field(label: String, value: String) {
                    find(label) { node -> node.isEditable && node.contentDescription?.toString() == label &&
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                        }) }; checks++
                }
                fun period(label: String, value: String) {
                    click("$label $value"); checks++
                }
                fun saveEnabled(enabled: Boolean) {
                    find("Save time slot · off") { node -> node.isClickable && node.isEnabled == enabled &&
                        Rect().also(node::getBoundsInScreen).let { !it.isEmpty && viewport().contains(it) } }; checks++
                }
                fun unchanged() { check(state().first == original.copy(groups = original.groups + group)); checks++ }
                fun review(label: String) {
                    visible("Review Time Slot"); visible(label); visible("${group.name} · Whole App")
                    visible("Every day · Saves Off"); saveEnabled(true)
                }
                // Default daytime clock is valid; invalid drafts must remain unsaveable.
                review("5:00 AM – 11:00 AM")
                for ((label, bad, restore) in listOf(Triple("From hour", "0", "5"), Triple("From hour", "13", "5"),
                    Triple("From minute", "60", "00"), Triple("Until minute", "", "00"))) {
                    field(label, bad); visible("Check your time slot"); saveEnabled(false); unchanged(); field(label, restore)
                }
                field("Until hour", "5"); saveEnabled(false); unchanged(); field("Until hour", "11")
                click("All days"); visible("Choose at least one day."); saveEnabled(false); unchanged(); click("All days")
                review("5:00 AM – 11:00 AM"); capture("daytime")
                click("Save time slot · off")
                waitFor { schedules.plan.windows.count { it.groupId == token } == 1 }
                val owned = schedules.plan.windows.single { it.groupId == token }
                fun assertSlot(start: Int, end: Int, allow: Boolean = false) {
                    val slot = schedules.plan.windows.single { it.id == owned.id }
                    check(!slot.enabled && !slot.allDay && slot.days == 127 && slot.scope.key == "app" &&
                        slot.startMinute == start && slot.endMinute == end && slot.allowOnly == allow); checks++
                }
                fun reopen(label: String) {
                    click("Edit time slot for ${group.name}: $label, Every day, Off"); filterPicker()
                }
                assertSlot(300, 660)
                reopen("5:00 AM – 11:00 AM")
                field("From hour", "12"); field("Until hour", "12"); period("Until", "PM")
                review("12:00 AM – 12:00 PM"); capture("midnight-noon"); click("Save time slot · off")
                waitFor { schedules.plan.windows.single { it.id == owned.id }.endMinute == 720 }; assertSlot(0, 720)
                reopen("12:00 AM – 12:00 PM")
                field("From hour", "11"); field("From minute", "45"); period("From", "PM")
                field("Until hour", "12"); field("Until minute", "15"); period("Until", "AM")
                review("11:45 PM – 12:15 AM · next day"); visible("Ends Next Day"); capture("overnight")
                click("Save time slot · off")
                waitFor { schedules.plan.windows.single { it.id == owned.id }.startMinute == 1425 }; assertSlot(1425, 15)
                reopen("11:45 PM – 12:15 AM · next day")
                visible("11:45 PM – 12:15 AM · next day"); visible("Ends Next Day")
                click("Allow Only"); visible("${group.name} · Allow Only · Other Apps Blocked")
                saveEnabled(true); capture("allow-only-off"); click("Save time slot · off")
                waitFor { schedules.plan.windows.single { it.id == owned.id }.allowOnly }; assertSlot(1425, 15, true)
                // Never enable Allow Only, even briefly, on the shared phone.
                click("Remove ${group.name}"); visible("This also removes all time slots for the group.")
                click("Remove")
                waitFor { schedules.plan.groups.none { it.id == token } && schedules.plan.windows.none { it.groupId == token } }
                check(schedules.plan == original); checks++
            } else {
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
            }
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
