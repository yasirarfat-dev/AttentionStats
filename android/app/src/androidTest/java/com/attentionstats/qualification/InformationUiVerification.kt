package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.attentionstats.MainActivity
import com.attentionstats.detection.SnapshotReader

/** Own-app disclosure/navigation only. No settings, rules, grants or history are written. */
@Suppress("DEPRECATION")
internal object InformationUiVerification {
    fun run(instrumentation: Instrumentation, mode: String): String {
        val context = instrumentation.targetContext
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val before = listOf("appearance", "system-backup").associateWith { context.getSharedPreferences(it, 0).all.toMap() }
        var checks = 0
        fun ownRoot(): AccessibilityNodeInfo? {
            if (Build.VERSION.SDK_INT >= 34) automation.clearCache()
            for (window in automation.windows) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isFocused) continue
                val root = window.root ?: continue
                if (root.packageName?.toString() == context.packageName) return root
                SnapshotReader.recycle(root)
            }
            return null
        }
        fun visit(label: String?, contains: Boolean = false, action: (AccessibilityNodeInfo) -> Boolean): Boolean {
            val root = ownRoot() ?: return false
            val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
            try {
                var seen = 0
                while (queue.isNotEmpty() && seen++ < 2_000) {
                    val node = queue.removeFirst()
                    try {
                        if (node.packageName?.toString() != context.packageName || node.isPassword || node.isEditable) continue
                        val match = if (label == null) node.isScrollable && node.isVisibleToUser else
                            listOf(node.text?.toString(), node.contentDescription?.toString()).any {
                                it != null && if (contains) it.contains(label, ignoreCase = true) else it.equals(label, ignoreCase = true)
                            }
                        if (match) {
                            if (label != null) node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                            if (node.isVisibleToUser && action(node)) return true
                        }
                        for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
                    } finally { if (node !== root) SnapshotReader.recycle(node) }
                }
                return false
            } finally { queue.forEach { if (it !== root) SnapshotReader.recycle(it) }; SnapshotReader.recycle(root) }
        }
        fun find(label: String, contains: Boolean = false, action: (AccessibilityNodeInfo) -> Boolean) {
            for (direction in listOf(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                repeat(14) {
                    if (visit(label, contains, action)) { SystemClock.sleep(180); return }
                    // Scroll the real own-app viewport. Some RN scroll containers expose
                    // accessibility scroll actions without moving the requested section.
                    val root = ownRoot()
                    if (root != null) {
                        val bounds = Rect().also { root.getBoundsInScreen(it) }
                        SnapshotReader.recycle(root)
                        val start = bounds.top + bounds.height() * .65f
                        val end = start + bounds.height() * (if (direction == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) -.28f else .28f)
                        // The platform rejects injected MotionEvents on the current phone.
                        // Use shell's touchscreen driver only after rechecking our focused window.
                        val focused = checkNotNull(ownRoot()) { "Own app left foreground; no input sent" }
                        SnapshotReader.recycle(focused)
                        val x = (bounds.left + bounds.width() * .125f).toInt()
                        val descriptor = automation.executeShellCommand("input touchscreen swipe $x ${start.toInt()} $x ${end.toInt()} 450")
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
                            check(it.readBytes().isEmpty()) { "Own scroll command failed" }
                        }
                    }
                    SystemClock.sleep(120)
                }
            }
            error("Own information control unavailable: $label")
        }
        fun click(label: String) = find(label) { node ->
            if (!node.isEnabled) false else if (node.isClickable) node.performAction(AccessibilityNodeInfo.ACTION_CLICK) else {
                var parent = node.parent
                try {
                    var depth = 0
                    while (parent != null && depth++ < 5) {
                        val current = parent
                        if (current.packageName?.toString() != context.packageName) return@find false
                        if (current.isClickable && current.isEnabled) return@find current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        parent = current.parent; SnapshotReader.recycle(current)
                    }
                    false
                } finally { SnapshotReader.recycle(parent) }
            }
        }
        fun help(title: String, fragment: String, report: String = title) {
            instrumentation.sendStatus(1, Bundle().apply { putString("stream", "Information UI checking: $report") })
            click("About $title")
            find(fragment, true) { true }; checks++
            click("About $title")
            val until = SystemClock.elapsedRealtime() + 1_500
            while (visit(fragment, true) { true } && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100)
            check(!visit(fragment, true) { true }) { "Own explanation did not collapse" }; checks++
            instrumentation.sendStatus(1, Bundle().apply { putString("stream", "Information UI: $report opens/closes") })
        }
        fun open(path: String) {
            context.startActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("attentionstats://$path")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            SystemClock.sleep(1_200)
            click(when (path) { "insights" -> "Insights"; "settings" -> "Settings"; "protect" -> "Protect"; else -> "Today" })
        }
        try {
            when (mode) {
                "today" -> {
                    open("today")
                    help("Today Stats", "This Session counts")
                    help("Viewing Details", "Encounters mean on screen")
                    help("Reels by Apps", "Today's recognized video")
                    help("Your Day", "Recognized short-video encounters")
                    help("Screen Time", "Recorded whole-app time")
                    click("Quick Actions"); help("Quick Actions", "Open a focus review")
                    click("Focus Now"); help("Focus Now", "Review apps, mode and duration")
                    click("Close focus chooser")
                }
                "insights" -> {
                    open("insights"); click("All Time")
                    help("Date Range", "Week and Month cover")
                    help("Video Encounters", "Totals cover the selected range")
                    click("Show App Breakdown"); help("Range by Video App", "These counts and recorded")
                    click("Hide App Breakdown"); help("Explore a Day", "Choose a day for every detail")
                    help("Day by Video App", "Video encounters and recorded")
                    help("Hourly Encounters", "Counts include recognized")
                    help("Day App Time & Opens", "Whole-app time overlaps")
                    help("Viewing Details", "Encounters mean on screen")
                    click("By App"); help("App History Dates", "App totals and daily rows")
                    help("Explore an App", "Search and sort recorded")
                    help("Apps in This Window", "Select an app for daily")
                    var selectedName: String? = null
                    find("Explore app history for ", true) { node ->
                        val label = node.contentDescription?.toString() ?: return@find false
                        if (!node.isClickable || !label.startsWith("Explore app history for ")) return@find false
                        selectedName = label.removePrefix("Explore app history for ").substringBefore(", recorded app time")
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    help(checkNotNull(selectedName), "Recorded app time and observed opens", "Selected App Summary")
                    help("Daily App History", "Dates use")
                    click("Back to app list")
                }
                "settings" -> {
                    open("settings")
                    help("Counting", "Choose the apps whose")
                    help("Permissions", "Recognizes supported")
                    help("Display & experience", "Choose whether the floating")
                    click("Built-in Reels auto-scroll")
                    find("Our Stop control only stops our own swipes", contains = true) { true }; checks++
                    click("Got it")
                    help("Data & Privacy", "Android-managed backup starts")
                    help("Appearance", "Choose Light, Dark")
                    help("Templates", "Choose Light, Dark or System")
                    help("Customization", "Text changes text")
                    click("Customize Layout & Numbers"); help("Layout & Numbers", "Adjust app-card corners")
                    click("Hide Layout Controls"); help("Help & Feedback", "Choose Email or a GitHub issue")
                }
                "protect" -> {
                    open("protect")
                    help("Focus", "Start a voluntary timed")
                    help("Your Groups & Time Slots", "Groups organize selected")
                    help("Short-video Boundaries", "Mindful Breaks offers")
                    click("Mindful Breaks"); help("Mindful Breaks", "A gentle pause after")
                    click("Back to Protect"); click("Short-video Blocks"); help("Short-video Blocks", "Daily limits count")
                    click("Back to Protect"); open("settings"); click("Live Counter")
                    help("Live Counter", "shows recognized encounters"); click("Back to Settings")
                }
                "native" -> {
                    open("settings"); click("Backup & Restore")
                    help("Backup Files", "Keep a portable copy")
                    help("Automatic Android Backup", "Starts enabled unless")
                    help("Manual Files", "Create a backup through")
                    click("Done"); click("Add a home-screen widget")
                    help("Example Gallery", "Choose a widget"); click("Back to Settings")
                }
                "editors" -> {
                    open("protect"); click("Scheduled Focus"); click("+ Add Time Slot")
                    help("Apps & Groups", "Group and individual choices")
                    help("What to Block", "Every screen in the selected")
                    help("When", "Times follow your phone")
                    help("From", "Enter hours 1–12")
                    help("Until", "Enter hours 1–12")
                    help("Review Time Slot", "New slots save Off")
                    click("Back to schedules"); click("By App Groups"); click("+ Create app group")
                    help("Installed Apps", "Choose one app or build a group")
                    click("Back to schedules")
                }
                "tour" -> {
                    open("settings"); click("Show the welcome tour"); click("Continue")
                    find("Tap (i) at the top", true) { true }; checks++
                    help("A Little Help, When You Need It", "Card information stays collapsed")
                }
                else -> error("Unsupported own information mode")
            }
            check(before.all { (name, values) -> context.getSharedPreferences(name, 0).all == values }) {
                "Help traversal changed saved appearance/backup choices"
            }
            checks++
            return "PASS: $checks actual own-app information checks; mode=$mode; appearance/backup unchanged; no source input or save"
        } finally { if (mode != "tour") open("today") }
    }
}
