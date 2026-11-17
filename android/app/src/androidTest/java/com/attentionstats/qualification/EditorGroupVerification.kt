package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.protection.ScheduleOwner
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.protection.SchedulePlan
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual group creation/edit/discard/removal; never creates a window or focus. */
@Suppress("DEPRECATION")
internal object EditorGroupVerification {
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
            check(done.await(15, TimeUnit.SECONDS)); return checkNotNull(captured)
        }
        val original = state().first
        check(original.groups.size < 30)
        val packages = listOf("com.sec.android.app.popupcalculator", "com.google.android.calculator", "com.android.calculator2",
            "com.sec.android.app.clockpackage", "com.google.android.deskclock", "com.android.deskclock")
            .filter { context.packageManager.getLaunchIntentForPackage(it) != null && it !in schedules.excluded }
        check(packages.size >= 2) { "Two safe installed utility apps required" }
        val first = packages[0]; val second = packages[1]
        fun appName(pkg: String) = context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
        val firstName = appName(first); val secondName = appName(second)
        check(firstName != secondName)
        val token = UUID.randomUUID().toString()
        val name = "UI $token"; val renamed = "$name edited"
        var ownedId: String? = null
        val journal = java.io.File(context.cacheDir, "editor-group-$token.json")
        check(!journal.exists()); journal.writeText(ScheduleOwner.encode(original).toString())
        var checks = 0; var failure: Throwable? = null
        fun preserved(plan: SchedulePlan) = plan.copy(groups = plan.groups.filterNot {
            it.id == ownedId || it.name == name || it.name == renamed }) == original
        fun waitFor(condition: (SchedulePlan) -> Boolean) {
            val until = SystemClock.elapsedRealtime() + 12_000
            do {
                val current = state().first
                check(preserved(current)) { "Unowned plan or saved focus changed" }
                if (condition(current)) { checks += 2; return }
                SystemClock.sleep(100)
            } while (SystemClock.elapsedRealtime() < until)
            error("Owned group state did not settle")
        }
        fun field(label: String, value: String) {
            find(label) { node -> node.isEditable && node.contentDescription?.toString() == label &&
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                }) }; checks++
        }
        fun saveEnabled(enabled: Boolean) {
            find("Save app group") { node -> node.isClickable && node.isEnabled == enabled &&
                Rect().also(node::getBoundsInScreen).let { !it.isEmpty && viewport().contains(it) } }; checks++
        }
        fun checked(label: String, expected: Boolean) {
            find(label) { it.contentDescription?.toString() == label && it.isCheckable && it.isChecked == expected }; checks++
        }
        fun capture(label: String) {
            fun own() = automation.rootInActiveWindow?.let {
                try { it.packageName?.toString() == context.packageName } finally { SnapshotReader.recycle(it) }
            } == true
            val output = java.io.File(context.cacheDir, "editor-group-${context.resources.configuration.fontScale}-$label.png")
            check(!output.exists() && own())
            val bounds = viewport(); val bitmap = checkNotNull(automation.takeScreenshot())
            try {
                check(own())
                val crop = android.graphics.Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width(), bounds.height())
                try { output.outputStream().use { check(crop.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) } }
                finally { if (crop !== bitmap) crop.recycle() }
            } finally { bitmap.recycle() }
        }
        try {
            open("protect"); click("Scheduled Focus"); click("By App Groups"); click("+ Create app group")
            field("Search apps", firstName); saveEnabled(false)
            field("Group name", "   "); click(firstName); checked(firstName, true); saveEnabled(false)
            field("Group name", "  $name  "); saveEnabled(true); capture("create")
            click("Save app group")
            waitFor { it.groups.count { group -> group.name == name && group.packages == setOf(first) } == 1 }
            ownedId = state().first.groups.single { it.name == name }.id
            check(original.groups.none { it.id == ownedId }); checks++
            click("$name, 1 selected app"); field("Search apps", firstName); checked(firstName, true)
            click(firstName); checked(firstName, false); saveEnabled(false)
            check(state().first.groups.single { it.id == ownedId }.packages == setOf(first)); checks++
            click("Back to schedules")
            click("$name, 1 selected app"); field("Search apps", firstName); checked(firstName, true)
            field("Group name", renamed); field("Search apps", secondName); checked(secondName, false)
            click(secondName); checked(secondName, true); saveEnabled(true); capture("edit")
            click("Save app group")
            waitFor { it.groups.single { group -> group.id == ownedId }.let { group -> group.name == renamed && group.packages == setOf(first, second) } }
            click("$renamed, 2 selected apps"); field("Search apps", firstName); checked(firstName, true)
            click(firstName); saveEnabled(true); click("Save app group")
            waitFor { it.groups.single { group -> group.id == ownedId }.packages == setOf(second) }
            click("Remove $renamed"); click("Back to schedules")
            check(state().first.groups.any { it.id == ownedId }); checks++
            click("Remove $renamed"); find("Remove this rule?") { true }; checks++; capture("remove")
            click("Remove"); waitFor { it == original }
        } catch (error: Throwable) { failure = error; throw error }
        finally {
            try {
                val current = state()
                val candidates = current.first.groups.filter { it.id == ownedId || it.name == name || it.name == renamed }
                check(candidates.size <= 1 && candidates.all { it.name in setOf(name, renamed) &&
                    it.packages.isNotEmpty() && it.packages.all { pkg -> pkg in setOf(first, second) } }) { "Owned group changed; no blind cleanup" }
                if (candidates.isNotEmpty()) {
                    val id = candidates.single().id
                    check(original.groups.none { it.id == id } && current.first.windows.none { it.groupId == id })
                    val done = CountDownLatch(1); var success = false
                    owner.saveSchedules(ScheduleOwner.encode(current.first.copy(groups = current.first.groups.filterNot { it.id == id })).toString(),
                        current.second, { success = true; done.countDown() }, { done.countDown() })
                    check(done.await(15, TimeUnit.SECONDS) && success)
                }
                check(state().first == original); checks++
                check(journal.delete())
                instrumentation.sendStatus(1, Bundle().apply { putString("stream", "Owned group cleanup: unowned plan and complete saved focus unchanged") })
            } catch (cleanup: Throwable) { if (failure == null) throw cleanup else failure.addSuppressed(cleanup) }
        }
        return checks
    }
}
