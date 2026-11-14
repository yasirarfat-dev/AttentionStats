package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.attentionstats.MainActivity
import com.attentionstats.MainApplication
import com.attentionstats.bridge.HistoryFailureFixture
import java.io.File

/** Injected read failures only; no store writes, grant changes or real corruption. */
@Suppress("DEPRECATION")
internal object HistoryFailureVerification {
    fun run(instrumentation: Instrumentation, staleOnly: Boolean = false): String {
        val context = instrumentation.targetContext
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val before = listOf("appearance", "system-backup").associateWith { context.getSharedPreferences(it, 0).all.toMap() }
        var checks = 0
        fun visit(predicate: (AccessibilityNodeInfo) -> Boolean): Boolean {
            if (Build.VERSION.SDK_INT >= 34) automation.clearCache()
            val root = automation.rootInActiveWindow ?: return false
            if (root.packageName?.toString() != context.packageName) { root.recycle(); return false }
            val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
            try {
                var count = 0
                while (queue.isNotEmpty() && count++ < 2_000) {
                    val node = queue.removeFirst()
                    try {
                        if (node.packageName?.toString() != context.packageName || node.isPassword || node.isEditable) continue
                        if (predicate(node)) return true
                        for (index in 0 until node.childCount) node.getChild(index)?.let(queue::add)
                    } finally { node.recycle() }
                }
                return false
            } finally { queue.forEach { it.recycle() } }
        }
        fun has(value: String, contains: Boolean = false) = visit { node ->
            node.isVisibleToUser && listOf(node.text?.toString(), node.contentDescription?.toString()).any {
                it != null && if (contains) it.contains(value, ignoreCase = true) else it.equals(value, ignoreCase = true)
            }
        }
        fun click(value: String) = visit { node ->
            if (!node.isVisibleToUser || !node.isClickable || !node.isEnabled ||
                (node.text?.toString()?.equals(value, ignoreCase = true) != true &&
                    node.contentDescription?.toString()?.equals(value, ignoreCase = true) != true)) false
            else {
                val bounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }
                if (bounds.isEmpty) false else {
                    // Exercise the same touchscreen route as the successful owned adb entry.
                    val descriptor = automation.executeShellCommand("input touchscreen tap ${bounds.centerX()} ${bounds.centerY()}")
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { check(it.readBytes().isEmpty()) }
                    SystemClock.sleep(300)
                    true
                }
            }
        }
        fun expect(value: Boolean) { check(value) { "history failure check ${checks + 1}" }; checks++ }
        fun awaitReady(condition: () -> Boolean) {
            val until = SystemClock.elapsedRealtime() + 25_000
            while (SystemClock.elapsedRealtime() < until) {
                if (condition()) { checks++; return }
                SystemClock.sleep(200)
            }
            val reactContext = (context.applicationContext as MainApplication).reactHost.currentReactContext
            val selected = visit { it.contentDescription?.toString() == "Insights" && it.isSelected }
            val loading = has("Loading your history…")
            error("history failure bounded wait ${checks + 1}; native lifecycle ${reactContext?.lifecycleState?.name ?: "absent"}; insights selected $selected; loading $loading")
        }
        fun capture(name: String) {
            // Foreground-return assertions can settle before the activity transition finishes.
            SystemClock.sleep(1_000)
            expect(has("History couldn’t refresh"))
            val bitmap = checkNotNull(automation.takeScreenshot())
            File(context.cacheDir, "history-failure-$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        val cases = listOf(
            HistoryFailureFixture.Case.BUSY to "native_busy",
            HistoryFailureFixture.Case.INTERRUPTED to "native_interrupted",
            HistoryFailureFixture.Case.STORAGE_UNAVAILABLE to "native_storage_unavailable",
            HistoryFailureFixture.Case.UNKNOWN to "native_unknown",
            HistoryFailureFixture.Case.PAYLOAD to "payload",
            HistoryFailureFixture.Case.JSON to "json",
            HistoryFailureFixture.Case.VALIDATION to "validation",
        )
        try {
            if (staleOnly) HistoryFailureFixture.clear() else HistoryFailureFixture.set(cases.first().first)
            context.startActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("attentionstats://today")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            awaitReady { click("Today") }
            // Separate activity startup from the focused history-entry assertion.
            SystemClock.sleep(1_000)
            expect(click("Insights"))
            if (!staleOnly) awaitReady { has("History couldn’t refresh") }
            for ((index, pair) in (if (staleOnly) emptyList() else cases).withIndex()) {
                val (case, code) = pair
                HistoryFailureFixture.set(case)
                if (index > 0) expect(click("Try again"))
                awaitReady { has("History couldn’t refresh") }
                expect(!has("Troubleshooting code:", true))
                expect(click("About History couldn’t refresh"))
                awaitReady { has("Troubleshooting code: $code.", true) }
                expect(has("History is unavailable. Saved data is preserved."))
                expect(!has("synthetic-private", true))
                if (case == HistoryFailureFixture.Case.BUSY || case == HistoryFailureFixture.Case.VALIDATION) capture(code)
                expect(click("About History couldn’t refresh"))
                expect(!has("Troubleshooting code:", true))
            }
            HistoryFailureFixture.clear()
            if (!staleOnly) expect(click("Try again"))
            awaitReady { !has("History couldn’t refresh") && has("About Video Encounters") }
            if (staleOnly) {
                HistoryFailureFixture.set(HistoryFailureFixture.Case.BUSY)
                expect(click("Today"))
                expect(click("Insights"))
                awaitReady { has("History couldn’t refresh") && has("Showing the last loaded observations.") }
                expect(has("About Video Encounters"))
                capture("stale")
                HistoryFailureFixture.clear()
                expect(click("Try again"))
                awaitReady { !has("History couldn’t refresh") && has("About Video Encounters") }
                expect(!has("Showing the last loaded observations."))
                // A foreground generation must not show observations from an invalidated cache.
                HistoryFailureFixture.set(HistoryFailureFixture.Case.BUSY)
                automation.executeShellCommand("input keyevent KEYCODE_HOME").let {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> check(stream.readBytes().isEmpty()) }
                }
                SystemClock.sleep(1_000)
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                awaitReady { has("History couldn’t refresh") }
                expect(!has("Showing the last loaded observations."))
                expect(!has("About Video Encounters"))
                capture("foreground")
                HistoryFailureFixture.clear()
                expect(click("Try again"))
                awaitReady { !has("History couldn’t refresh") && has("About Video Encounters") }
            }
            for ((name, values) in before) expect(context.getSharedPreferences(name, 0).all == values)
            if (staleOnly) return "PASS: $checks history stale/tab-return/foreground-invalidation/retry checks; no store writes; captures require visual review"
            return "PASS: $checks injected history failure/help/retry/recovery checks; seven fixed codes, no raw error; real regression cause unestablished; captures require visual review"
        } finally { HistoryFailureFixture.clear() }
    }
}
