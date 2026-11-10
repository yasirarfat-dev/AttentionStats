package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.os.SystemClock
import com.attentionstats.autoscroll.AutoScrollSession
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.reels.ReelApp

/** Package-only source ownership and own HUD checks; no source navigation or content capture. */
internal object LiveSurfaceLifecycleVerification {
    fun run(instrumentation: Instrumentation): Int {
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val coordinator = ReelsCoordinator.get(instrumentation.targetContext)
        check(!AutoScrollSession.running) { "active convenience run preserved" }
        check(coordinator.serviceConnected && coordinator.reminder == null) { "service/reminder precondition" }
        fun owner(): String? {
            val root = automation.rootInActiveWindow ?: return null
            return try { root.packageName?.toString() } finally { SnapshotReader.recycle(root) }
        }
        val source = checkNotNull(ReelApp.byPackage(owner().orEmpty())) { "selected source must already be foreground" }
        check(coordinator.allowed(source)) { "source choice preserved" }
        fun visible() = LiveHudVerification.state(instrumentation, automation).header != null
        fun waitFor(predicate: () -> Boolean): Boolean {
            val until = SystemClock.elapsedRealtime() + 5_000
            do {
                if (predicate()) return true
                SystemClock.sleep(100)
            } while (SystemClock.elapsedRealtime() < until)
            return false
        }
        check(waitFor(::visible)) {
            "initial HUD unavailable; acquisition=${coordinator.acquisitionStatus}; no shade action performed"
        }
        var checks = 0
        fun expect(value: Boolean) { check(value) { "live surface lifecycle ${checks + 1}" }; checks++ }
        fun shade(expand: Boolean) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
                "cmd statusbar ${if (expand) "expand-notifications" else "collapse"}")).use { it.readBytes() }
        }
        var expanded = false
        try {
            repeat(3) {
                expanded = true; shade(true)
                expect(waitFor { owner() == "com.android.systemui" })
                expect(waitFor { !visible() })
                shade(false); expanded = false
                expect(waitFor { source.owns(owner()) })
                expect(waitFor(::visible))
                expect(coordinator.serviceConnected && coordinator.allowed(source))
            }
            return checks
        } finally {
            if (expanded) shade(false)
        }
    }
}
