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

/** Own-app UI qualification; editor-review temporarily owns one group and its slots only. */
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
        val scanSteps = kotlin.math.ceil(14 * context.resources.configuration.fontScale.toDouble()).toInt().coerceIn(14, 42)
        fun ownRoot(): AccessibilityNodeInfo? {
            if (Build.VERSION.SDK_INT >= 34) automation.clearCache()
            for (window in automation.windows) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isFocused) continue
                val root = window.root ?: continue
                if (root.packageName?.toString() == context.packageName) return root
                SnapshotReader.recycle(root)
            }
            // A focused own-app active root is also used by the current history runner.
            val active = automation.rootInActiveWindow ?: return null
            if (active.packageName?.toString() == context.packageName) return active
            SnapshotReader.recycle(active)
            return null
        }
        fun visit(label: String?, contains: Boolean = false, includeEditable: Boolean = false, action: (AccessibilityNodeInfo) -> Boolean): Boolean {
            val root = ownRoot() ?: return false
            val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
            try {
                var seen = 0
                while (queue.isNotEmpty() && seen++ < 2_000) {
                    val node = queue.removeFirst()
                    try {
                        if (node.packageName?.toString() != context.packageName || node.isPassword || (node.isEditable && !includeEditable)) continue
                        val match = if (label == null) node.isScrollable && node.isVisibleToUser else
                            listOf(node.text?.toString(), node.contentDescription?.toString()).any {
                                it != null && if (contains) it.contains(label, ignoreCase = true) else it.equals(label, ignoreCase = true)
                            }
                        if (match) {
                            // Discovery must not move the viewport between matching and tapping.
                            if (node.isVisibleToUser && action(node)) return true
                        }
                        for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
                    } finally { if (node !== root) SnapshotReader.recycle(node) }
                }
                return false
            } finally { queue.forEach { if (it !== root) SnapshotReader.recycle(it) }; SnapshotReader.recycle(root) }
        }
        fun viewport(forTab: Boolean = false): Rect {
            val metrics = context.resources.displayMetrics
            fun inset(name: String): Int = context.resources.getIdentifier(name, "dimen", "android").let {
                if (it == 0) 0 else context.resources.getDimensionPixelSize(it)
            }
            val result = Rect(0, inset("status_bar_height"), metrics.widthPixels, metrics.heightPixels - inset("navigation_bar_height"))
            if (!forTab) visit("Today") { node ->
                if (!node.isClickable) false else {
                    val tab = Rect().also(node::getBoundsInScreen)
                    // Tab buttons sit inside eight dp of top padding in the persistent bar.
                    if (tab.top > metrics.heightPixels / 2) result.bottom = minOf(result.bottom, tab.top - (8 * metrics.density).toInt())
                    true
                }
            }
            if (mode == "editor-keyboard") automation.windows.filter {
                it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
            }.forEach { window ->
                val bounds = Rect().also(window::getBoundsInScreen)
                if (!bounds.isEmpty) result.bottom = minOf(result.bottom, bounds.top)
            }
            return result
        }
        fun find(label: String, contains: Boolean = false, includeEditable: Boolean = false, action: (AccessibilityNodeInfo) -> Boolean) {
            for (direction in listOf(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                repeat(scanSteps) {
                    if (visit(label, contains, includeEditable, action)) { SystemClock.sleep(180); return }
                    // Scroll the real own-app viewport. Some RN scroll containers expose
                    // accessibility scroll actions without moving the requested section.
                    val root = ownRoot()
                    if (root != null) {
                        val bounds = viewport()
                        SnapshotReader.recycle(root)
                        val start = bounds.top + bounds.height() * .65f
                        val end = start + bounds.height() * (if (direction == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) -.28f else .28f)
                        // The platform rejects injected MotionEvents on the current phone.
                        // Use shell's touchscreen driver only after rechecking our focused window.
                        val focused = checkNotNull(ownRoot()) { "Own app left foreground; no input sent" }
                        SnapshotReader.recycle(focused)
                        val x = (bounds.left + 8 * context.resources.displayMetrics.density).toInt()
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
            check(mode != "deletion" || !label.startsWith("Erase everything")) {
                "Deletion review must never activate the final erase action"
            }
            if (!node.isEnabled || !node.isClickable) false else {
                // A discovered control can still be moving after the preceding swipe.
                // Wait before reading its coordinates so the touch reaches the settled
                // control rather than the location it occupied during scrolling.
                automation.waitForIdle(500, 5_000)
                SystemClock.sleep(250)
                if (!node.refresh() || !node.isVisibleToUser) return@find false
                val bounds = Rect().also(node::getBoundsInScreen)
                val root = ownRoot()
                if (root == null) false else {
                    SnapshotReader.recycle(root)
                    val viewport = viewport(label in setOf("Today", "Insights", "Protect", "Settings"))
                    // Use the same bounded touchscreen route as the history runner.
                    // Reject clipped controls before touching the owned window.
                    // Accessibility can clip the reported rectangle itself to the scroll edge.
                    // Our fixed-size information controls are square; a truncated rectangle
                    // must be scrolled fully into view before input.
                    val clippedInfo = label.startsWith("About ") && kotlin.math.abs(bounds.width() - bounds.height()) > 2
                    // This Kit tonal button has a 52dp minimum height. Android can
                    // report only its clipped strip as the visible rectangle.
                    val clippedCreateGroup = label == "+ Create app group" &&
                        bounds.height() < (52 * context.resources.displayMetrics.density).toInt() - 2
                    val clippedReviewAction = mode in setOf("editor-review", "editor-clock", "editor-group") &&
                        (label in setOf("Save time slot · off", "Save app group", "Enable time slot", "Remove") || label.startsWith("Add time slot for ")) &&
                        bounds.height() < (52 * context.resources.displayMetrics.density).toInt() - 2
                    if (bounds.isEmpty || clippedInfo || clippedCreateGroup || clippedReviewAction || !viewport.contains(bounds)) false else {
                        val focused = checkNotNull(ownRoot()) { "Own app left foreground; no input sent" }
                        SnapshotReader.recycle(focused)
                        instrumentation.sendStatus(1, Bundle().apply { putString("stream", "Own information tap: $label; $bounds") })
                        val descriptor = automation.executeShellCommand("input touchscreen tap ${bounds.centerX()} ${bounds.centerY()}")
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
                            check(it.readBytes().isEmpty()) { "Own tap command failed" }
                        }
                        automation.waitForIdle(500, 5_000)
                        true
                    }
                }
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
            SystemClock.sleep(2_000)
            // This review starts with a direct Settings route. A selected tab's
            // accessible node need not expose the same actionable label as an unselected tab.
            if (mode == "deletion" && path == "settings") return
            click(when (path) { "insights" -> "Insights"; "settings" -> "Settings"; "protect" -> "Protect"; else -> "Today" })
        }
        var primaryFailure: Throwable? = null
        try {
            when (mode) {
                "editor-review", "editor-clock" -> {
                    checks += EditorReviewVerification.run(instrumentation, { open(it) }, { click(it) },
                        { label, action -> find(label, contains = true, includeEditable = true, action = action) }, { viewport() }, customClock = mode == "editor-clock")
                }
                "editor-group" -> {
                    checks += EditorGroupVerification.run(instrumentation, { open(it) }, { click(it) },
                        { label, action -> find(label, contains = true, includeEditable = true, action = action) }, { viewport() })
                }
                "counting-health" -> {
                    val owner = com.attentionstats.reels.ReelsCoordinator.get(context)
                    check(owner.grantSelected()) { "Existing selected grant required; no grant added" }
                    check(owner.serviceConnected) { "Existing bound service required" }
                    fun capture(name: String) {
                        val output = java.io.File(context.cacheDir, "counting-health-$name.png")
                        check(!output.exists()) { "Unowned health capture already exists" }
                        val beforeRoot = checkNotNull(ownRoot())
                        SnapshotReader.recycle(beforeRoot)
                        val bitmap = checkNotNull(automation.takeScreenshot())
                        try {
                            val afterRoot = checkNotNull(ownRoot())
                            SnapshotReader.recycle(afterRoot)
                            output.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
                        } finally { bitmap.recycle() }
                    }
                    // Inject only the volatile status field; never kill/unbind or change saved consent/history.
                    instrumentation.runOnMainSync { owner.serviceConnected = false }
                    try {
                        open("today")
                        find("Counting is disconnected") { true }; checks++
                        find("Reconnect Accessibility") { true }; checks++
                        check(!visit("Finish setup") { true }); checks++
                        capture("today")
                        open("settings")
                        find("Disconnected") { true }; checks++
                        find("Disconnected. Turn this service off, then on in Android Settings.") { true }; checks++
                        capture("settings")
                    } finally {
                        instrumentation.runOnMainSync { owner.serviceConnected = true }
                    }
                    open("today")
                    val until = SystemClock.elapsedRealtime() + 8_000
                    while (visit("Counting is disconnected") { true } && SystemClock.elapsedRealtime() < until) SystemClock.sleep(200)
                    check(!visit("Counting is disconnected") { true }); checks++
                }
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
                    automation.waitForIdle(500, 5_000)
                    help("Backup Files", "Keep a portable copy")
                    help("Automatic Android Backup", "Starts enabled unless")
                    help("Manual Files", "Create a backup through")
                    click("Done"); automation.waitForIdle(500, 5_000)
                    click("Add a home-screen widget"); automation.waitForIdle(500, 5_000)
                    help("Example Gallery", "Choose a widget"); click("Back to Settings")
                }
                "editor-keyboard" -> {
                    check(context.getSharedPreferences("focus-defaults", 0).getBoolean("seeded-v1", false)) {
                        "Existing seeded configuration required; no default groups created"
                    }
                    val owner = com.attentionstats.reels.ReelsCoordinator.get(context)
                    val schedules = owner.javaClass.getDeclaredField("schedules").apply { isAccessible = true }
                        .get(owner) as com.attentionstats.protection.ScheduleOwner
                    check(owner.serviceConnected && !com.attentionstats.autoscroll.AutoScrollSession.running)
                    val savedPlan = schedules.plan
                    val savedRevision = schedules.revision
                    fun keyboard() = automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                    fun waitKeyboard(visible: Boolean) {
                        val until = SystemClock.elapsedRealtime() + 8_000
                        while (keyboard() != visible && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100)
                        check(keyboard() == visible) { "Expected keyboard visible=$visible" }; checks++
                    }
                    fun back() {
                        val root = checkNotNull(ownRoot()) { "Own editor left foreground; no Back" }
                        SnapshotReader.recycle(root)
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input keyevent KEYCODE_BACK")).use {
                            check(it.readBytes().isEmpty())
                        }
                        SystemClock.sleep(500)
                    }
                    fun field(label: String, value: String) {
                        instrumentation.sendStatus(1, Bundle().apply { putString("stream", "Editor keyboard field: $label") })
                        find(label, includeEditable = true) { node ->
                            if (!node.isEditable || !node.isEnabled) false else {
                                // Match the existing button protocol: settle the real scroll,
                                // then reacquire geometry and reject clipped input strips.
                                automation.waitForIdle(500, 5_000)
                                SystemClock.sleep(250)
                                if (!node.refresh() || !node.isVisibleToUser) return@find false
                                val bounds = Rect().also(node::getBoundsInScreen)
                                val minimum = if (label.endsWith("hour") || label.endsWith("minute")) 64 else 52
                                if (bounds.isEmpty || bounds.height() < minimum * context.resources.displayMetrics.density - 2 ||
                                    !viewport().contains(bounds)) false else {
                                    val focused = checkNotNull(ownRoot())
                                    SnapshotReader.recycle(focused)
                                    android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input touchscreen tap ${bounds.centerX()} ${bounds.centerY()}")).use {
                                        check(it.readBytes().isEmpty())
                                    }
                                    true
                                }
                            }
                        }
                        waitKeyboard(true)
                        find(label, includeEditable = true) { node ->
                            node.isEditable && node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                            })
                        }
                        val until = SystemClock.elapsedRealtime() + 5_000
                        fun readable() = visit(label, includeEditable = true) { node ->
                            val bounds = Rect().also(node::getBoundsInScreen)
                            node.isFocused && node.text?.toString() == value && viewport().contains(bounds)
                        }
                        while (!readable() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100)
                        if (!readable()) {
                            var focused = false; var valueMatches = false; var fieldBounds = Rect()
                            visit(label, includeEditable = true) { node ->
                                focused = node.isFocused; valueMatches = node.text?.toString() == value
                                node.getBoundsInScreen(fieldBounds); true
                            }
                            val root = checkNotNull(ownRoot()); SnapshotReader.recycle(root)
                            val bounds = viewport(true)
                            val bitmap = checkNotNull(automation.takeScreenshot())
                            try {
                                val after = checkNotNull(ownRoot()); SnapshotReader.recycle(after)
                                val crop = android.graphics.Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width(), bounds.height())
                                try {
                                    val output = java.io.File(context.cacheDir, "editor-keyboard-field-failure.png")
                                    check(!output.exists())
                                    output.outputStream().use { check(crop.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
                                } finally { if (crop !== bitmap) crop.recycle() }
                            } finally { bitmap.recycle() }
                            error("Focused editor field: $label; focused=$focused valueMatches=$valueMatches bounds=$fieldBounds viewport=${viewport()} keyboard=${keyboard()}")
                        }
                        checks++
                        waitKeyboard(true)
                    }
                    fun capture(name: String) {
                        val output = java.io.File(context.cacheDir, "editor-keyboard-$name.png")
                        check(!output.exists()) { "Unowned editor capture exists" }
                        val root = checkNotNull(ownRoot()); SnapshotReader.recycle(root)
                        // Persist only the own-app area above the IME; never traverse keyboard nodes.
                        val bounds = viewport(true)
                        val bitmap = checkNotNull(automation.takeScreenshot())
                        try {
                            val after = checkNotNull(ownRoot()); SnapshotReader.recycle(after)
                            val cropped = android.graphics.Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width(), bounds.height())
                            try { output.outputStream().use { check(cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) } }
                            finally { if (cropped !== bitmap) cropped.recycle() }
                        } finally { bitmap.recycle() }
                    }
                    try {
                        open("protect"); click("Scheduled Focus"); click("By App Groups"); click("+ Create app group")
                        field("Group name", "Keyboard verification draft")
                        capture("group-name")
                        back(); waitKeyboard(false)
                        find("Group name", includeEditable = true) { it.text?.toString() == "Keyboard verification draft" }; checks++
                        field("Search apps", "attentionstats-ui-empty-catalog-8ea7")
                        capture("group-search")
                        // Assert keyboard Back immediately. Searching for a lower result
                        // may drag this ScrollView, whose on-drag policy dismisses the IME.
                        back(); waitKeyboard(false)
                        find("No matching apps") { true }; checks++
                        back(); find("Space for your day") { true }; checks++
                        click("+ Create app group")
                        find("Group name", includeEditable = true) {
                            // Android may expose the empty TextInput's placeholder as its text.
                            it.isShowingHintText || it.text.isNullOrEmpty() ||
                                it.text?.toString() == "For example, Distracting apps"
                        }; checks++
                        click("Back to schedules"); click("By Time Slots"); click("+ Add Time Slot")
                        field("Search Focus Apps", "attentionstats-ui-empty-catalog-8ea7")
                        back(); waitKeyboard(false)
                        find("No Matching Apps or Groups") { true }; checks++
                        val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun", "All days")
                        fun assertDays(mask: Int) {
                            dayLabels.forEachIndexed { index, label ->
                                find(label) { node ->
                                    node.isCheckable && node.isChecked == if (index == 7) mask == 127 else (mask and (1 shl index)) != 0
                                }; checks++
                            }
                        }
                        assertDays(127)
                        click("All days"); assertDays(0)
                        find("Choose at least one day.") { true }; checks++
                        click("Wed"); assertDays(4)
                        click("All days"); assertDays(127)
                        dayLabels.take(7).forEachIndexed { index, label ->
                            click(label); assertDays(127 xor (1 shl index))
                            click(label); assertDays(127)
                        }
                        val cells = dayLabels.map { label ->
                            var bounds = Rect()
                            find(label) { node ->
                                node.getBoundsInScreen(bounds)
                                viewport().contains(bounds)
                            }
                            bounds
                        }
                        check(cells.maxOf { it.width() } - cells.minOf { it.width() } <= 2)
                        check(cells.take(4).map { it.top }.distinct().size == 1 && cells.drop(4).map { it.top }.distinct().size == 1)
                        check(cells[4].top > cells[0].bottom && cells.all { it.height() >= 48 * context.resources.displayMetrics.density - 2 })
                        checks += 3
                        capture("days-grid")
                        field("From hour", "09"); back(); waitKeyboard(false)
                        field("From minute", "17"); back(); waitKeyboard(false)
                        field("Until hour", "10"); back(); waitKeyboard(false)
                        field("Until minute", "43"); capture("until-minute")
                        back(); waitKeyboard(false)
                        find("Review Time Slot") { true }; checks++
                        capture("slot-review")
                        back(); find("Space for your day") { true }; checks++
                    } finally {
                        check(schedules.plan == savedPlan && schedules.revision == savedRevision) {
                            "Unsaved editor journey changed saved rules or focus"
                        }
                        checks++
                    }
                }
                "editors" -> {
                    open("protect"); click("Scheduled Focus"); click("+ Add Time Slot")
                    help("Apps & Groups", "Group and individual choices")
                    // The required full alphabetical catalog can exceed this runner's
                    // bounded outer-page scan. Exercise its actual draft search to reach
                    // the remaining controls without changing catalog presentation/selection.
                    find("Search Focus Apps", includeEditable = true) { node ->
                        node.isEditable && node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "attentionstats-ui-empty-catalog-8ea7")
                        })
                    }; checks++
                    find("No Matching Apps or Groups") { true }; checks++
                    help("What to Block", "Every screen in the selected")
                    help("When", "Times follow your phone")
                    help("From", "Enter hours 1–12")
                    help("Until", "Enter hours 1–12")
                    help("Review Time Slot", "New slots save Off")
                    click("Back to schedules"); click("By App Groups"); click("+ Create app group")
                    help("Installed Apps", "Choose one app or build a group")
                    click("Back to schedules")
                }
                "deletion" -> {
                    // A failed earlier owned review can leave its RN modal mounted across deep links.
                    if (visit("Keep my history") { it.isClickable }) click("Keep my history")
                    val coordinator = com.attentionstats.reels.ReelsCoordinator.get(context)
                    val schedules = coordinator.javaClass.getDeclaredField("schedules").apply { isAccessible = true }
                        .get(coordinator) as com.attentionstats.protection.ScheduleOwner
                    val plan = schedules.plan
                    val planRevision = schedules.revision
                    fun savedState(): String {
                        val done = java.util.concurrent.CountDownLatch(1)
                        val value = java.util.concurrent.atomic.AtomicReference<String>()
                        val failure = java.util.concurrent.atomic.AtomicReference<String>()
                        coordinator.snapshot({ value.set(it); done.countDown() }, { failure.set(it); done.countDown() })
                        check(done.await(10, java.util.concurrent.TimeUnit.SECONDS) && failure.get() == null)
                        val snapshot = org.json.JSONObject(checkNotNull(value.get()))
                        return org.json.JSONArray().put(snapshot.getString("revision"))
                            .put(snapshot.getString("generation")).put(snapshot.getJSONArray("sources"))
                            .put(snapshot.getBoolean("overlayEnabled")).toString()
                    }
                    open("settings")
                    val saved = savedState()
                    fun expect(label: String, action: (AccessibilityNodeInfo) -> Boolean = { true }) {
                        find(label, contains = true, includeEditable = true, action = action); checks++
                    }
                    fun begin() {
                        click("Erase history, Permanent. Three confirmations.")
                        expect("Before erasing history")
                        expect("Copies already held by your backup provider")
                    }
                    fun review() {
                        click("Review history deletion")
                        expect("Review permanent deletion")
                        expect("There is no local undo")
                    }
                    fun cancel() {
                        click("Keep my history")
                        check(!visit("Keep my history") { true }); checks++
                        check(savedState() == saved); checks++
                        check(schedules.plan == plan && schedules.revision == planRevision); checks++
                    }
                    begin(); cancel()
                    begin(); review(); cancel()
                    begin(); review(); click("Proceed to final erase confirmation")
                    expect("Type DELETE to erase your history")
                    expect("Erase everything") { !it.isEnabled }
                    expect("Type DELETE to confirm") { node ->
                        node.isEditable && node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "WRONG")
                        })
                    }
                    SystemClock.sleep(10_500)
                    expect("Erase everything") { !it.isEnabled }
                    expect("Type DELETE to confirm") { node ->
                        node.isEditable && node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "DELETE")
                        })
                    }
                    expect("Erase everything") { it.isEnabled }
                    cancel()
                    begin(); review(); click("Proceed to final erase confirmation")
                    expect("Type DELETE to confirm") { it.text.isNullOrEmpty() || (Build.VERSION.SDK_INT >= 26 && it.isShowingHintText) }
                    expect("Erase everything") { !it.isEnabled }
                    click("Keep my history")
                    check(savedState() == saved); checks++
                    check(schedules.plan == plan && schedules.revision == planRevision); checks++
                }
                "tour" -> {
                    open("settings"); click("Show the welcome tour")
                    val capturedSteps = mutableSetOf<Int>()
                    fun atTop(step: Int) {
                        val until = SystemClock.elapsedRealtime() + 10_000
                        while (!visit("Step $step of 3") { true } && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100)
                        val capture = java.io.File(context.cacheDir, "surface-tour-${context.resources.configuration.fontScale}-step$step.png")
                        if (capturedSteps.add(step)) check(!capture.exists()) { "Unowned tour capture already exists" }
                        val beforeCapture = checkNotNull(ownRoot()) { "Own tour left foreground; no capture" }
                        SnapshotReader.recycle(beforeCapture)
                        val bitmap = checkNotNull(automation.takeScreenshot())
                        try {
                            val afterCapture = checkNotNull(ownRoot()) { "Own tour left foreground; capture discarded" }
                            SnapshotReader.recycle(afterCapture)
                            capture.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
                        } finally { bitmap.recycle() }
                        check(visit("Step $step of 3") { true }) { "Tour step $step top marker unavailable before test scrolling" }
                        checks++
                        instrumentation.sendStatus(1, Bundle().apply { putString("stream", "Information UI: tour step $step starts at top") })
                    }
                    atTop(1); click("Continue"); atTop(2)
                    find("Tap (i) at the top", true) { true }; checks++
                    help("A Little Help, When You Need It", "Card information stays collapsed")
                    click("Set it up"); atTop(3)
                    check(visit("How counting works") { true }); checks++
                    click("Back"); atTop(2)
                    val focused = checkNotNull(ownRoot()) { "Own app left foreground; no Back sent" }
                    SnapshotReader.recycle(focused)
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input keyevent KEYCODE_BACK")).use {
                        check(it.readBytes().isEmpty())
                    }
                    automation.waitForIdle(500, 5_000); atTop(1)
                }
                else -> error("Unsupported own information mode")
            }
            check(before.all { (name, values) -> context.getSharedPreferences(name, 0).all == values }) {
                "Help traversal changed saved appearance/backup choices"
            }
            checks++
            return "PASS: $checks actual own-app information checks; mode=$mode; appearance/backup unchanged; no source input; " +
                if (mode in setOf("editor-review", "editor-clock", "editor-group")) "owned rules removed; unowned plan/focus preserved" else "no save"
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            if (mode != "tour") {
                try {
                    if (mode == "deletion" && visit("Keep my history") { it.isClickable }) click("Keep my history")
                    open("today")
                } catch (cleanup: Throwable) {
                    val failure = primaryFailure
                    if (failure == null) throw cleanup else failure.addSuppressed(cleanup)
                }
            }
        }
    }
}
