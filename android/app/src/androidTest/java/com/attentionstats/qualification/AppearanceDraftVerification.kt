package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.attentionstats.MainActivity
import com.attentionstats.appearance.AppearanceOwner
import java.io.File
import org.json.JSONObject

/** Actual RN draft/save/reset controls; temporary visual preferences restored exactly in finally. */
@Suppress("DEPRECATION")
internal object AppearanceDraftVerification {
    fun run(instrumentation: Instrumentation): String {
        val context = instrumentation.targetContext
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val preferences = context.getSharedPreferences("appearance", 0)
        val original = preferences.all.toMap()
        val backup = context.getSharedPreferences("system-backup", 0).all.toMap()
        var checks = 0
        fun expect(ok: Boolean) { check(ok) { "appearance draft check ${checks + 1}" }; checks++ }
        fun visit(predicate: (AccessibilityNodeInfo) -> Boolean): Boolean {
            if (android.os.Build.VERSION.SDK_INT >= 34) automation.clearCache()
            val root = automation.rootInActiveWindow ?: return false
            if (root.packageName?.toString() != context.packageName) { root.recycle(); return false }
            val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
            try {
                var count = 0
                while (queue.isNotEmpty() && count++ < 2_000) {
                    val node = queue.removeFirst()
                    try {
                        if (node.packageName?.toString() != context.packageName || node.isPassword) continue
                        if (predicate(node)) return true
                        for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
                    } finally { node.recycle() }
                }
                return false
            } finally { queue.forEach { it.recycle() } }
        }
        fun named(node: AccessibilityNodeInfo, name: String) = listOf(node.text, node.contentDescription)
            .any { it?.toString()?.equals(name, ignoreCase = true) == true }
        fun shell(command: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {
                check(it.readBytes().isEmpty())
            }
        }
        fun scroll(up: Boolean) {
            val root = checkNotNull(automation.rootInActiveWindow)
            val bounds = Rect().also { root.getBoundsInScreen(it) }
            check(root.packageName?.toString() == context.packageName); root.recycle()
            // Match the independently working own-app touchscreen scroll path.
            val start = bounds.top + bounds.height() * (if (up) 62 else 30) / 100
            val end = bounds.top + bounds.height() * (if (up) 30 else 62) / 100
            val x = bounds.left + bounds.width() / 8
            shell("input touchscreen swipe $x $start $x $end 350")
            SystemClock.sleep(250)
        }
        fun click(name: String, up: Boolean = true) {
            instrumentation.sendStatus(1, Bundle().apply { putString("stream", "Appearance control ${checks + 1}: $name") })
            for (direction in listOf(up, !up)) repeat(35) {
                val clicked = visit { node ->
                    val bounds = Rect().also { node.getBoundsInScreen(it) }
                    val displayHeight = context.resources.displayMetrics.heightPixels
                    if (!named(node, name) || !node.isVisibleToUser || !node.isClickable || !node.isEnabled ||
                        bounds.isEmpty || bounds.top < 90 || bounds.bottom > displayHeight * .72) false
                    else { shell("input touchscreen tap ${bounds.centerX()} ${bounds.centerY()}"); true }
                }
                if (clicked) { SystemClock.sleep(300); checks++; return }
                scroll(direction)
            }
            val bitmap = if (visit { it.isVisibleToUser }) automation.takeScreenshot() else null
            bitmap?.let {
                File(context.cacheDir, "appearance-draft-failure.png").outputStream().use { out -> it.compress(Bitmap.CompressFormat.PNG, 100, out) }
                it.recycle()
            }
            error("appearance draft control ${checks + 1}")
        }
        fun field(expected: String, up: Boolean = true) {
            repeat(35) {
                if (visit { node ->
                    val bounds = Rect().also { node.getBoundsInScreen(it) }
                    node.isEditable && named(node, "Selected hex color") && node.text?.toString()?.equals(expected, true) == true &&
                        node.isVisibleToUser && bounds.top > 90 && bounds.bottom < context.resources.displayMetrics.heightPixels * .72
                }) { checks++; return }
                scroll(up)
            }
            error("appearance draft hex ${checks + 1}")
        }
        fun reveal(name: String) {
            repeat(35) {
                if (visit { node ->
                    val bounds = Rect().also { node.getBoundsInScreen(it) }
                    named(node, name) && node.isVisibleToUser && bounds.top > 90 &&
                        bounds.bottom < context.resources.displayMetrics.heightPixels * .72
                }) { checks++; return }
                scroll(true)
            }
            error("appearance draft reveal ${checks + 1}")
        }
        fun capture(name: String) {
            expect(visit { it.isVisibleToUser })
            val bitmap = checkNotNull(automation.takeScreenshot())
            File(context.cacheDir, "appearance-draft-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        val fixture = JSONObject().put("mode", "dark").put("accent", "graphite").put("motion", true)
            .put("foregroundColor", "#E5E7EB").put("backgroundColor", "#15191F").put("customColor", "#4477AA")
            .put("corners", 24).put("spacing", 20).put("numberSize", 30)
        fun unchanged() = expect(AppearanceOwner.snapshot(context).toString() == fixture.toString())
        try {
            AppearanceOwner.configure(context, fixture.toString())
            context.startActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("attentionstats://settings")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val until = SystemClock.elapsedRealtime() + 25_000
            while (!visit { named(it, "Settings") && it.isClickable } && SystemClock.elapsedRealtime() < until) SystemClock.sleep(300)
            // Tab controls sit outside the scrollable content and need their own bounds rule.
            expect(visit { node ->
                if (!named(node, "Settings") || !node.isClickable) false else {
                    val b = Rect().also { node.getBoundsInScreen(it) }
                    shell("input touchscreen tap ${b.centerX()} ${b.centerY()}"); true
                }
            })
            click("About Templates"); unchanged(); capture("templates-help")
            click("About Templates", false)
            click("System"); unchanged()
            expect(visit { named(it, "System") && it.isSelected })
            click("Dark", false); capture("theme")
            click("Blue"); unchanged(); capture("templates")
            click("About Customization"); unchanged(); capture("customization-help")
            click("About Customization", false)
            click("Text"); field("#E5E7EB")
            expect(visit { node ->
                named(node, "Selected hex color") && node.isEditable && node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "#AABBCC") })
            })
            SystemClock.sleep(300); unchanged()
            click("Save appearance")
            val saved = AppearanceOwner.snapshot(context)
            expect(saved.getString("accent") == "blue" && saved.getString("customColor").isEmpty())
            expect(saved.getString("foregroundColor").equals("#AABBCC", true) && saved.getString("backgroundColor") == "#15191F")
            click("Background", false); field("#15191F")
            click("Use preset for this color", false)
            expect(visit { it.isEditable && named(it, "Selected hex color") &&
                it.text?.toString()?.matches(Regex("#[0-9A-Fa-f]{6}")) == true &&
                !it.text.toString().equals("#15191F", true) && !it.text.toString().equals("#5676A6", true) })
            click("Discard appearance changes"); field("#15191F", false)
            expect(AppearanceOwner.snapshot(context).toString() == saved.toString())
            click("Reset appearance draft"); capture("reset-draft")
            expect(AppearanceOwner.snapshot(context).toString() == saved.toString())
            click("Discard appearance changes"); field("#15191F", false)
            click("Fine-tune RGB"); capture("rgb")
            reveal("Green"); capture("rgb-channels")
            click("Hide RGB controls", false)
            click("Customize layout & numbers"); capture("layout")
            click("About Layout & Numbers"); capture("layout-help")
            reveal("Statistic size"); capture("layout-lower")
            click("Hide layout controls", false)
            reveal("Save appearance"); capture("actions")
            expect(context.getSharedPreferences("system-backup", 0).all == backup)
            return "PASS: $checks actual Appearance draft/save/discard/reset/help checks; visual review required"
        } finally {
            val edit = preferences.edit().clear()
            original.forEach { (key, value) -> when (value) {
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                else -> error("Unexpected appearance preference type")
            } }
            check(edit.commit() && preferences.all == original)
        }
    }
}
