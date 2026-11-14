package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import com.attentionstats.MainActivity
import com.attentionstats.diary.DiaryPresentationFixture
import java.io.File

/** Display-only stress values in the actual RN screen. No database/preferences/collector writes. */
internal object TodayLayoutVerification {
    fun run(instrumentation: Instrumentation): String {
        val context = instrumentation.targetContext
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun capture(name: String) {
            val root = checkNotNull(automation.rootInActiveWindow)
            check(root.packageName?.toString() == context.packageName)
            val bitmap = checkNotNull(automation.takeScreenshot())
            File(context.cacheDir, "today-layout-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        fun swipe(up: Boolean) {
            val root = checkNotNull(automation.rootInActiveWindow)
            check(root.packageName?.toString() == context.packageName)
            val bounds = android.graphics.Rect().also { root.getBoundsInScreen(it) }
            val x = bounds.left + bounds.width() / 10
            val a = bounds.top + bounds.height() * 3 / 4
            val b = bounds.top + bounds.height() / 3
            automation.executeShellCommand("input touchscreen swipe $x ${if (up) a else b} $x ${if (up) b else a} 350")
                .let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> check(stream.readBytes().isEmpty()) } }
            SystemClock.sleep(500)
        }
        fun hasDescription(node: android.view.accessibility.AccessibilityNodeInfo, expected: String): Boolean {
            if (node.contentDescription?.toString() == expected) return true
            for (index in 0 until node.childCount) node.getChild(index)?.let { child ->
                if (hasDescription(child, expected)) return true
            }
            return false
        }
        fun clickToday(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
            if (node.contentDescription?.toString() == "Today" && node.isClickable)
                return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            for (index in 0 until node.childCount) node.getChild(index)?.let { child ->
                if (clickToday(child)) return true
            }
            return false
        }
        try {
            for (case in listOf("zero", "typical", "large", "extreme")) {
                DiaryPresentationFixture.today(case)
                context.startActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                    .setData(Uri.parse("attentionstats://today")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                val readyBy = SystemClock.elapsedRealtime() + 25_000
                var selected = false
                while (!selected && SystemClock.elapsedRealtime() < readyBy) {
                    SystemClock.sleep(500)
                    val root = automation.rootInActiveWindow
                    if (root?.packageName?.toString() == context.packageName) selected = clickToday(root)
                }
                check(selected) { "Today tab unavailable after bounded startup wait" }
                SystemClock.sleep(12_000)
                repeat(7) { swipe(false) }
                val count = when (case) { "zero" -> 0L; "typical" -> 965L; "large" -> 999_999L; else -> 9_999_999_999_999L }
                val total = count + count / 3 + count / 4 + count / 5 + count / 6
                check(hasDescription(checkNotNull(automation.rootInActiveWindow), "$total video encounters today")) {
                    "Synthetic Today totals did not reach actual screen: $case"
                }
                capture("$case-hero")
                swipe(true)
                capture("$case-hero-lower")
                fun sourcesVisible(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
                    if (node.contentDescription?.toString() == "About Reels by Apps" && node.isVisibleToUser) {
                        val bounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }
                        val window = android.graphics.Rect().also { checkNotNull(automation.rootInActiveWindow).getBoundsInScreen(it) }
                        if (bounds.top >= window.top + 100 && bounds.bottom <= window.bottom - 180) return true
                    }
                    for (index in 0 until node.childCount) node.getChild(index)?.let { if (sourcesVisible(it)) return true }
                    return false
                }
                var revealed = false
                repeat(14) {
                    if (!revealed) {
                        revealed = sourcesVisible(checkNotNull(automation.rootInActiveWindow))
                        if (!revealed) swipe(true)
                    }
                }
                check(revealed) { "Source heading not reached: $case" }
                capture("$case-sources")
                swipe(true)
                capture("$case-sources-lower")
                swipe(true)
                capture("$case-sources-end")
                instrumentation.sendStatus(1, android.os.Bundle().apply { putString("stream", "Captured actual Today layout: $case") })
            }
            return "PASS: four synthetic Today display cases captured on native RN; visual review required; saved data unchanged"
        } finally { DiaryPresentationFixture.clear() }
    }
}
