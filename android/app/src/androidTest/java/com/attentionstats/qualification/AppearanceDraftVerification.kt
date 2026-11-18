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
    fun run(instrumentation: Instrumentation, themesOnly: Boolean = false): String {
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
        fun scroll(up: Boolean, fine: Boolean = false) {
            val root = checkNotNull(automation.rootInActiveWindow)
            val bounds = Rect().also { root.getBoundsInScreen(it) }
            check(root.packageName?.toString() == context.packageName); root.recycle()
            // Match the independently working own-app touchscreen scroll path.
            val start = bounds.top + bounds.height() * (if (up) 62 else 30) / 100
            val end = bounds.top + bounds.height() * (if (fine) { if (up) 54 else 38 } else { if (up) 30 else 62 }) / 100
            // The widened Appearance frame puts the canvas under the old 1/8-width swipe.
            // Scroll in the page gutter so finding the preview cannot edit its colors.
            val x = bounds.left + (8 * context.resources.displayMetrics.density).toInt()
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
        fun reveal(name: String, up: Boolean = true) {
            repeat(35) {
                var direction = up
                var near = false
                if (visit { node ->
                    val bounds = Rect().also { node.getBoundsInScreen(it) }
                    if (!named(node, name)) false else {
                        near = true; direction = bounds.top >= 90
                        node.isVisibleToUser && bounds.top > 90 &&
                            bounds.bottom < context.resources.displayMetrics.heightPixels * .72
                    }
                }) { checks++; return }
                // Coarse reverse swipes can overshoot the tall preview at 200% text.
                scroll(direction, fine = near)
            }
            error("appearance draft reveal ${checks + 1}")
        }
        fun capture(name: String) {
            expect(visit { it.isVisibleToUser })
            val bitmap = checkNotNull(automation.takeScreenshot())
            File(context.cacheDir, "appearance-draft-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        fun preview(dark: Boolean) {
            val label = "Appearance preview. Video encounters 965. Recorded surface time 6 hours 51 minutes."
            // At 200%, reading the hex below the preview can scroll the preview above us.
            reveal(label, up = false)
            var bounds: Rect? = null
            expect(visit { node ->
                if (!named(node, label)) false else {
                    bounds = Rect().also { node.getBoundsInScreen(it) }; true
                }
            })
            val bitmap = checkNotNull(automation.takeScreenshot())
            val rect = checkNotNull(bounds)
            val pixel = bitmap.getPixel(rect.right - 35, rect.centerY())
            bitmap.recycle()
            expect((androidx.core.graphics.ColorUtils.calculateLuminance(pixel) < .179) == dark)
        }
        fun typeColor(color: String) {
            expect(visit { node ->
                named(node, "Selected hex color") && node.isEditable && node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, color) })
            })
            SystemClock.sleep(300)
        }
        val fixture = JSONObject().put("mode", "dark").put("accent", "graphite").put("motion", true)
            .put("foregroundColor", "#E5E7EB").put("backgroundColor", "#15191F").put("customColor", "#4477AA")
            .put("corners", 24).put("spacing", 20).put("numberSize", 30)
        fun unchanged() = expect(AppearanceOwner.snapshot(context).toString() == fixture.toString())
        // Templates save as soon as they are tapped (ADR-119): wait for the saved record to match.
        fun applied(vararg values: Pair<String, String>) {
            values.forEach { (key, value) -> fixture.put(key, value) }
            val until = SystemClock.elapsedRealtime() + 5_000
            while (AppearanceOwner.snapshot(context).toString() != fixture.toString() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(150)
            unchanged()
        }
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
            click("Light"); applied("mode" to "light", "foregroundColor" to "", "backgroundColor" to "")
            click("Background"); field("#F5F7FC"); preview(false); capture("light-preview")
            click("Save appearance")
            fixture.put("mode", "light").put("foregroundColor", "").put("backgroundColor", "")
            unchanged()
            expect(AppearanceOwner.palette(context).canvas == android.graphics.Color.parseColor("#F5F7FC"))
            // System must follow the OS even while a different app theme is forced.
            val systemDark = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
            click("System", false); applied("mode" to "system")
            click("Background"); field(if (systemDark) "#08090B" else "#F5F7FC"); preview(systemDark); capture("system-from-light")
            click("Dark", false); applied("mode" to "dark")
            click("Background"); field("#08090B"); preview(true); capture("dark-preview")
            click("Save appearance")
            val themed = AppearanceOwner.snapshot(context)
            expect(themed.getString("mode") == "dark" && themed.getString("foregroundColor").isEmpty() && themed.getString("backgroundColor").isEmpty())
            expect(themed.getString("customColor") == "#4477AA" && themed.getInt("spacing") == 20)
            fixture.put("mode", "dark")
            expect(AppearanceOwner.palette(context).canvas == android.graphics.Color.parseColor("#08090B"))
            click("System"); applied("mode" to "system")
            expect(visit { named(it, "System") && it.isSelected })
            click("Background"); field(if (systemDark) "#08090B" else "#F5F7FC"); preview(systemDark); capture("system-preview")
            if (themesOnly) return "PASS: $checks actual Light/Dark/System preview/save/native palette checks; original preferences restored in finally"
            click("Dark", false); applied("mode" to "dark"); capture("theme")
            click("Blue"); applied("accent" to "blue", "customColor" to ""); capture("templates")
            click("About Customization"); unchanged(); capture("customization-help")
            click("About Customization", false)
            click("Background"); field("#08090B"); typeColor("#15191F")
            click("Text", false); field("#F1F2F4")
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
