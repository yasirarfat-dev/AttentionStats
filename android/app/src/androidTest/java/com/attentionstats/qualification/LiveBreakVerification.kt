package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.detection.AdapterRegistry
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.ReelRoute
import org.attentionstats.reels.Surface
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit operator trial: bounded viewer gestures, own break controls, restored reminder choices. */
internal object LiveBreakVerification {
    fun run(instrumentation: Instrumentation, timeOnly: Boolean = false, absenceOnly: Boolean = false): String {
        val context = instrumentation.targetContext
        val owner = ReelsCoordinator.get(context)
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun await(call: ((String) -> Unit, (String) -> Unit) -> Unit): JSONObject {
            val done = CountDownLatch(1); var raw: String? = null
            call({ raw = it; done.countDown() }, { done.countDown() })
            check(done.await(15, TimeUnit.SECONDS)) { "native operation timeout" }
            return JSONObject(checkNotNull(raw) { "native operation unavailable" })
        }
        fun snapshot() = await { success, failure -> owner.diarySnapshot(1, success, failure) }
        val original = snapshot()
        fun configure(action: String, enabled: Boolean, count: Int, minutes: Int) {
            val revision = snapshot().getString("revision").toLong()
            await { success, failure -> owner.configureDiary(action, enabled, count, minutes, revision, success, failure) }
        }
        fun ownButton(label: String, click: Boolean = false): Boolean {
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
                                if (node.isVisibleToUser && node.text?.toString() == label) {
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
        // Caller first opens an admitted dedicated viewer; never guess where a swipe would land.
        var initialApp: ReelApp? = null
        val initialDeadline = SystemClock.elapsedRealtime() + 10_000
        while (initialApp == null && SystemClock.elapsedRealtime() < initialDeadline) {
            val root = automation.rootInActiveWindow
            if (root != null) try { initialApp = ReelApp.byPackage(root.packageName?.toString() ?: "") }
                finally { SnapshotReader.recycle(root) }
            if (initialApp == null) for (window in automation.windows) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isFocused) continue
                val candidate = window.root ?: continue
                try { initialApp = ReelApp.byPackage(candidate.packageName?.toString() ?: "") }
                finally { SnapshotReader.recycle(candidate) }
            }
            if (initialApp == null) SystemClock.sleep(200)
        }
        val app = checkNotNull(initialApp) { "supported viewer not foreground" }
        // Instrumentation can restart the target before Android rebinds its selected service.
        // Wait for that existing grant; never enable a source or change a service selection here.
        val serviceDeadline = SystemClock.elapsedRealtime() + 10_000
        while (!owner.serviceConnected && SystemClock.elapsedRealtime() < serviceDeadline) SystemClock.sleep(100)
        check(owner.serviceConnected && owner.allowed(app)) { "source/service unavailable" }
        check(owner.reminder == null) { "existing reminder preserved" }
        check(!com.attentionstats.autoscroll.AutoScrollSession.running) { "existing auto-scroll run preserved" }
        check(app !in owner.blockChoices()) { "existing viewer block preserved; disable it before this trial" }
        val schedules = await { success, failure -> owner.scheduleSnapshot(success, failure) }
        val plan = com.attentionstats.protection.ScheduleOwner.decode(JSONObject()
            .put("groups", schedules.getJSONArray("groups")).put("windows", schedules.getJSONArray("windows")).toString())
        check(plan.windows.none { window ->
            window.enabled && plan.groups.any { it.id == window.groupId && it.packages.any(app::owns) }
        }) { "existing source schedules preserved; choose a source without enabled slots" }
        fun nextItem(): Boolean {
            var owned: AccessibilityNodeInfo? = null
            for (window in automation.windows) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isFocused) continue
                val candidate = window.root ?: continue
                if (app.owns(candidate.packageName?.toString())) { owned = candidate; break }
                SnapshotReader.recycle(candidate)
            }
            val active = owned ?: return false
            val captured = try {
                if (active.packageName?.toString() == context.packageName) return false // A break can attach between polling and this read.
                check(app.owns(active.packageName?.toString())) { "viewer ownership changed" }
                SnapshotReader.read(active, app.packages)
            } finally { SnapshotReader.recycle(active) }
            val frame = AdapterRegistry.of(app).read(captured)
            check(frame.surface == Surface.REELS && frame.route == ReelRoute.VIEWER) {
                "qualified viewer required; ${frame.surface}/${frame.route}; scan=${owner.acquisitionStatus}"
            }
            val box = checkNotNull(frame.page)
            val downAt = SystemClock.uptimeMillis()
            for (step in 0..12) {
                val action = when (step) { 0 -> MotionEvent.ACTION_DOWN; 12 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
                val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action,
                    box.left + box.width * .4f, box.top + box.height * (.78f - .53f * step / 12), 0)
                try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
                SystemClock.sleep(25)
            }
            SystemClock.sleep(1_600)
            return true
        }
        var cycles = 0
        try {
            if (absenceOnly) {
                val power = context.getSystemService(android.os.PowerManager::class.java)
                val keyguard = context.getSystemService(android.app.KeyguardManager::class.java)
                fun available() = power.isInteractive && !keyguard.isKeyguardLocked && owner.serviceConnected && owner.allowed(app)
                fun focusedPackage(): String? {
                    for (window in automation.windows) {
                        if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isFocused) continue
                        val root = window.root ?: continue
                        try { return root.packageName?.toString() } finally { SnapshotReader.recycle(root) }
                    }
                    return null
                }
                fun viewer(): Boolean {
                    for (window in automation.windows) {
                        if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isFocused) continue
                        val root = window.root ?: continue
                        try {
                            if (!app.owns(root.packageName?.toString())) return false
                            val captured = SnapshotReader.read(root, app.packages)
                            val frame = AdapterRegistry.of(app).read(captured)
                            return captured.complete && !captured.typing && frame.surface == Surface.REELS && frame.route == ReelRoute.VIEWER
                        } finally { SnapshotReader.recycle(root) }
                    }
                    return false
                }
                fun waitFor(timeout: Long, predicate: () -> Boolean) {
                    val until = SystemClock.elapsedRealtime() + timeout
                    while (!predicate()) {
                        check(available()) { "absence trial interrupted; no wake or permission changes attempted" }
                        check(SystemClock.elapsedRealtime() < until) { "absence trial condition timed out; scan=${owner.acquisitionStatus}" }
                        SystemClock.sleep(250)
                    }
                    check(available())
                }
                fun status(message: String) = instrumentation.sendStatus(1, android.os.Bundle().apply { putString("stream", message) })
                val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
                val home = checkNotNull(context.packageManager.resolveActivity(homeIntent, 0)?.activityInfo?.packageName)
                check(!app.owns(home) && home != context.packageName) { "distinct launcher required" }
                check(available() && viewer() && owner.liveOverlay?.app == app) { "unlocked qualified viewer/HUD required" }
                check(!LiveHudVerification.state(instrumentation, automation).paused) { "existing counting pause preserved" }
                val sourcePackage = checkNotNull(focusedPackage())
                val resume = checkNotNull(context.packageManager.getLaunchIntentForPackage(sourcePackage))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                fun away(duration: Long): Long {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input keyevent 3")).use { it.readBytes() }
                    waitFor(8_000) { focusedPackage() == home && LiveHudVerification.state(instrumentation, automation).header == null }
                    val until = SystemClock.elapsedRealtime() + duration
                    var nextReport = SystemClock.elapsedRealtime() + 60_000
                    while (SystemClock.elapsedRealtime() < until) {
                        check(available() && focusedPackage() == home && owner.reminder == null) { "absence interrupted; launcher, service, unlock and pending state required" }
                        if (SystemClock.elapsedRealtime() >= nextReport) {
                            status("Real absence: another elapsed minute on launcher; still unlocked and service connected")
                            nextReport += 60_000
                        }
                        SystemClock.sleep(500)
                    }
                    val returned = SystemClock.elapsedRealtime()
                    context.startActivity(resume)
                    waitFor(8_000) { viewer() && owner.liveOverlay?.app == app && LiveHudVerification.state(instrumentation, automation).header != null }
                    return returned
                }
                configure("break", true, 3, 1)
                configure("reminder", true, 1000, 1)
                waitFor(45_000) { (owner.liveOverlay?.breakProgress ?: -1f) in .4f.. .65f }
                check(owner.reminder == null)
                val before = checkNotNull(owner.liveOverlay).breakProgress
                away(10_000)
                waitFor(5_000) { (owner.liveOverlay?.breakProgress ?: -1f) >= before }
                check(checkNotNull(owner.liveOverlay).breakProgress < before + .15f && owner.reminder == null) { "short absence charged or lost previous progress" }
                status("PASS: ten-second real absence retains prior progress without charging launcher time")
                val returned = away(310_000)
                // No rule edits, dismissal, screen lock or service restart between the two absences.
                waitFor(5_000) { (owner.liveOverlay?.breakProgress ?: -1f) in 0f.. .15f }
                check(owner.reminder == null && !ownButton("I'm done for now")) { "old reminder survived long absence" }
                status("PASS: 310-second real absence resets progress on qualified viewer return")
                waitFor(95_000) {
                    if (ownButton("I'm done for now")) true
                    else { check(viewer()) { "qualified viewer lost after return" }; false }
                }
                val pending = owner.reminder
                check(pending?.app == app && pending.reason == "time" && pending.durationMs >= 60_000 && pending.count < 1000)
                check(SystemClock.elapsedRealtime() - returned >= 59_000) { "old progress or absence time reached next trigger" }
                check(ownButton("Keep watching", true)) { "post-absence Keep watching unavailable" }
                waitFor(5_000) { owner.reminder == null && !ownButton("I'm done for now") }
                return "PASS: real 10-second retention, 310-second absence reset and fresh minute-trigger/render/dismissal; no source swipes; original preferences restored"
            }
            if (timeOnly) {
                val power = context.getSystemService(android.os.PowerManager::class.java)
                val keyguard = context.getSystemService(android.app.KeyguardManager::class.java)
                check(power.isInteractive && !keyguard.isKeyguardLocked) { "unlocked phone required" }
                check(owner.liveOverlay?.app == app) { "existing qualified source HUD required" }
                configure("break", true, 3, 1)
                configure("reminder", true, 1000, 1)
                var started = SystemClock.elapsedRealtime()
                repeat(2) { cycle ->
                    val deadline = started + 95_000
                    while (!ownButton("I'm done for now") && SystemClock.elapsedRealtime() < deadline) {
                        check(power.isInteractive && !keyguard.isKeyguardLocked && owner.serviceConnected && owner.allowed(app)) {
                            "time trial interrupted; no wake or permission changes attempted"
                        }
                        SystemClock.sleep(250)
                    }
                    val pending = owner.reminder
                    check(ownButton("I'm done for now") && pending?.app == app && pending.reason == "time" &&
                        pending.durationMs >= 60_000 && pending.count < 1000) {
                        "live minute trigger missing or wrong reason; cycle=${cycle + 1}; scan=${owner.acquisitionStatus}"
                    }
                    check(SystemClock.elapsedRealtime() - started >= 59_000) { "time trigger arrived early after reset" }
                    instrumentation.sendStatus(1, android.os.Bundle().apply {
                        putString("stream", "Live minute trigger ${cycle + 1}: native time reason and actual reminder visible; no setup swipes")
                    })
                    SystemClock.sleep(15_000)
                    check(ownButton("Keep watching", true)) { "delayed time-reminder Continue unavailable" }
                    started = SystemClock.elapsedRealtime()
                    SystemClock.sleep(4_000)
                    check(owner.reminder == null && !ownButton("I'm done for now")) { "time reminder survived dismissal" }
                    cycles++
                }
                return "PASS: $cycles real-minute reminder/render/delayed-dismissal cycles; no source gestures; reminder preferences restored"
            }
            configure("reminder", true, 5, 240)
            configure("break", true, 3, 15)
            repeat(3) { cycle ->
                val before = await { success, failure -> owner.snapshot(success, failure) }
                    .getJSONObject("session").getString("count").toLong()
                var attempts = 0
                var gestures = 0
                while (!ownButton("I'm done for now") && attempts < 16) {
                    attempts++
                    if (nextItem()) gestures++ else SystemClock.sleep(500)
                }
                val after = await { success, failure -> owner.snapshot(success, failure) }
                    .getJSONObject("session").getString("count").toLong()
                val pending = owner.reminder
                // Report only trial deltas and bounded state, never personal totals or source labels.
                val diagnostic = "cycle=${cycle + 1}; gestures=$gestures; delta=${after - before}; " +
                    "pending=${pending != null}; progress=${owner.liveOverlay?.breakProgress}; " +
                    "connected=${owner.serviceConnected}; scan=${owner.acquisitionStatus}"
                check(ownButton("I'm done for now")) {
                    "break missing; $diagnostic"
                }
                check(pending != null && pending.app == app && pending.reason == "count" && pending.count >= 5) {
                    "displayed break lacks the configured count trigger; $diagnostic"
                }
                instrumentation.sendStatus(1, android.os.Bundle().apply { putString("stream", "Live break trigger: $diagnostic") })
                if (cycle < 2) {
                    SystemClock.sleep(15_000) // Reproduce interaction well after the countdown completes.
                    check(ownButton("Keep watching", true)) { "delayed Continue unavailable" }
                } else check(ownButton("I'm done for now", true))
                SystemClock.sleep(4_000)
                check(!ownButton("I'm done for now")) { "dismissed cover remained or reappeared" }
                cycles++
                instrumentation.sendStatus(1, android.os.Bundle().apply { putString("stream", "Live break cycle $cycles passed") })
            }
            return "PASS: $cycles live break cycles; two delayed Keep watching choices and I'm done remove the cover without reappearing; reminder preferences restored"
        } finally {
            configure("break", true, original.getInt("breakSeconds"), original.getInt("reminderMinutes"))
            configure("reminder", original.getBoolean("reminderEnabled"), original.getInt("reminderCount"), original.getInt("reminderMinutes"))
            val restored = snapshot()
            for (key in listOf("breakSeconds", "reminderEnabled", "reminderCount", "reminderMinutes"))
                check(restored.get(key) == original.get(key)) { "reminder preference restoration failed: $key" }
        }
    }
}
