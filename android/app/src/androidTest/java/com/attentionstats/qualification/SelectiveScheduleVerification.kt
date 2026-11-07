package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.protection.ScheduleOwner
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.detection.AdapterRegistry
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.ReelRoute
import org.attentionstats.reels.Surface
import org.attentionstats.protection.AppGroup
import org.attentionstats.protection.BlockScope
import org.attentionstats.protection.BlockWindow
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit operator trial on an already-consented Instagram viewer; no source content retained. */
internal object SelectiveScheduleVerification {
    fun run(instrumentation: Instrumentation): String {
        val context = instrumentation.targetContext
        val owner = ReelsCoordinator.get(context)
        val app = checkNotNull(ReelApp.byKey("instagram"))
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun state(): JSONObject {
            val done = CountDownLatch(1); var raw: String? = null
            owner.scheduleSnapshot({ raw = it; done.countDown() }, { done.countDown() })
            check(done.await(15, TimeUnit.SECONDS)) { "schedule read timed out" }
            return JSONObject(checkNotNull(raw) { "schedule unavailable" })
        }
        fun plan(value: JSONObject) = ScheduleOwner.decode(JSONObject()
            .put("groups", value.getJSONArray("groups")).put("windows", value.getJSONArray("windows")).toString())
        fun save(value: org.attentionstats.protection.SchedulePlan, revision: Long) {
            val done = CountDownLatch(1); var success = false
            owner.saveSchedules(ScheduleOwner.encode(value).toString(), revision,
                { success = true; done.countDown() }, { done.countDown() })
            check(done.await(15, TimeUnit.SECONDS) && success) { "schedule save failed" }
        }
        fun sourceRoot(): AccessibilityNodeInfo? {
            for (window in automation.windows) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isFocused) continue
                val node = window.root ?: continue
                if (app.owns(node.packageName?.toString())) return node
                SnapshotReader.recycle(node)
            }
            return null
        }
        fun frame(): org.attentionstats.detection.AdapterFrame {
            val root = checkNotNull(sourceRoot()) { "selected source not foreground" }
            try {
                val snapshot = SnapshotReader.read(root, app.packages)
                check(snapshot.complete && !snapshot.typing) { "incomplete or editable context" }
                return AdapterRegistry.of(app).read(snapshot)
            } finally { SnapshotReader.recycle(root) }
        }
        fun button(label: String, click: Boolean = false): Boolean {
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
        fun waitCover() {
            val until = SystemClock.elapsedRealtime() + 10_000
            while (!button("Allow 5 minutes") && SystemClock.elapsedRealtime() < until) SystemClock.sleep(200)
            check(button("Short videos are on a pause") && button("Allow 5 minutes")) { "selective cover missing" }
        }
        fun absentFor(duration: Long) {
            val until = SystemClock.elapsedRealtime() + duration
            while (SystemClock.elapsedRealtime() < until) {
                check(!button("Allow 5 minutes")) { "selective cover remained or reappeared" }
                SystemClock.sleep(200)
            }
        }
        fun reenterViewer() {
            // Only the fixed, previously observed Reels navigation control may be selected.
            val source = checkNotNull(sourceRoot())
            try {
                val pkg = checkNotNull(source.packageName).toString()
                val controls = source.findAccessibilityNodeInfosByViewId("$pkg:id/clips_tab")
                try {
                    val tab = controls.singleOrNull { it.isVisibleToUser && it.isClickable && !it.isEditable }
                    check(tab != null && tab.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "fixed Reels tab unavailable" }
                } finally { controls.forEach { SnapshotReader.recycle(it) } }
            } finally { SnapshotReader.recycle(source) }
            waitCover()
        }
        val before = state()
        val until = SystemClock.elapsedRealtime() + 10_000
        while (!owner.serviceConnected && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100)
        check(owner.serviceConnected && owner.allowed(app)) {
            "source/service unavailable; connected=${owner.serviceConnected}; allowed=${owner.allowed(app)}; grant=${owner.grantEnabled()}"
        }
        check(app !in owner.blockChoices()) { "existing source block preserved" }
        val initial = plan(before)
        check(initial.windows.none { window -> window.enabled && initial.groups.any {
            it.id == window.groupId && it.packages.any(app::owns)
        } }) { "existing source schedules preserved" }
        val first = frame()
        check(first.surface == Surface.REELS && first.route == ReelRoute.VIEWER) { "admitted dedicated viewer required" }
        check(!button("Allow 5 minutes")) { "existing cover preserved" }
        val root = checkNotNull(sourceRoot())
        val pkg = try { checkNotNull(root.packageName).toString() } finally { SnapshotReader.recycle(root) }
        val group = AppGroup(UUID.randomUUID().toString(), "Selective schedule verification", setOf(pkg))
        val window = BlockWindow(UUID.randomUUID().toString(), group.id, 127, 0, 0, true,
            scope = BlockScope.SHORT_VIDEOS, allDay = true)
        var grantedAllowance = false
        try {
            save(initial.copy(groups = initial.groups + group, windows = initial.windows + window), before.getString("revision").toLong())
            waitCover()
            check(button("Okay", true))
            SystemClock.sleep(2_000)
            val left = frame()
            check(left.surface != Surface.REELS || left.route != ReelRoute.VIEWER) { "Okay did not leave viewer" }
            absentFor(2_000)
            reenterViewer()
            check(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            SystemClock.sleep(2_000)
            val outside = frame()
            check(outside.surface != Surface.REELS || outside.route != ReelRoute.VIEWER) { "Back did not leave viewer" }
            absentFor(2_000)
            reenterViewer()
            check(button("Allow 5 minutes", true))
            grantedAllowance = true
            absentFor(4_000)
            val allowed = frame()
            check(allowed.surface == Surface.REELS && allowed.route == ReelRoute.VIEWER) { "allowance did not preserve viewer" }
        } finally {
            val latest = state(); val current = plan(latest)
            save(current.copy(groups = current.groups.filter { it.id != group.id },
                windows = current.windows.filter { it.id != window.id }), latest.getString("revision").toLong())
            // The preceding covers establish that no earlier active allowance was in effect.
            if (grantedAllowance) owner.allowFor(app, 0)
        }
        val final = plan(state())
        check(final.groups.none { it.id == group.id } && final.windows.none { it.id == window.id }) { "task-owned rule cleanup failed" }
        return "PASS: selective viewer cover; Okay and Back release within same source; fixed-tab reentries block; five-minute allowance preserves viewer for four seconds; task-owned rules and allowance removed"
    }
}
