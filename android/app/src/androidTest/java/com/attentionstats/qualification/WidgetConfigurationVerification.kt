package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import com.attentionstats.widget.TodayWidget
import com.attentionstats.widget.WidgetConfigureActivity
import java.util.UUID

/** Real save/reopen/cancel flows in temporary hosts; original display preferences are preserved. */
internal object WidgetConfigurationVerification {
    fun run(instrumentation: Instrumentation): Int {
        val context = instrumentation.targetContext
        val manager = AppWidgetManager.getInstance(context)
        val original = context.getSharedPreferences("widgets", 0).all.toMap()
        val host = AppWidgetHost(context, UUID.randomUUID().hashCode() and Int.MAX_VALUE)
        val ids = mutableListOf<Int>()
        var checks = 0
        fun expect(value: Boolean) { check(value) { "widget configuration ${checks + 1}" }; checks++ }
        fun ui(action: () -> Unit) {
            var failure: Throwable? = null
            instrumentation.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
            failure?.let { throw it }
        }
        try {
            val providers = manager.installedProviders.filter { it.provider.packageName == context.packageName && it.provider.className.startsWith("com.attentionstats.widget.") }
            expect(providers.size == 5)
            for (info in providers) {
            val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            automation.adoptShellPermissionIdentity(android.Manifest.permission.BIND_APPWIDGET)
            val id = host.allocateAppWidgetId().also { ids.add(it) }
            try { expect(manager.bindAppWidgetIdIfAllowed(id, info.provider)) }
            finally { automation.dropShellPermissionIdentity() }
            val defaults = com.attentionstats.widget.WidgetConfiguration.load(context, id)
            val expected = when (info.provider.className.substringAfterLast('.')) {
                "StatWidget" -> "spotlight"; "ScreenTimeWidget" -> "screen_time"
                "ReelsAppsWidget" -> "apps"; "RhythmWidget" -> "rhythm"; else -> "adaptive"
            }
            expect(defaults.style.key == expected)
            expect(info.previewLayout != 0)
            ui {
                val preview = android.view.LayoutInflater.from(context).inflate(info.previewLayout, android.widget.FrameLayout(context), false)
                fun labels(view: View): List<String> = if (view is TextView) listOf(view.text.toString()) else if (view is ViewGroup) (0 until view.childCount).flatMap { labels(view.getChildAt(it)) } else emptyList()
                expect(labels(preview).any { it.contains("Example Data") } && labels(preview).size >= 4)
            }
            var activity = instrumentation.startActivitySync(Intent(context, WidgetConfigureActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fun children(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { children(view.getChildAt(it)) } else emptyList()
            fun action(prefix: String) {
                ui {
                    val text = children(activity.window.decorView).filterIsInstance<TextView>().firstOrNull { it.text.toString().startsWith(prefix, ignoreCase = true) } ?: error("Missing fixed widget control: $prefix")
                    var view: View = text
                    while (!view.isClickable && view.parent is View) view = view.parent as View
                    view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
                    expect(view.performClick())
                }
                instrumentation.waitForIdleSync()
            }
            try {
                instrumentation.waitForIdleSync()
                ui { expect(children(activity.window.decorView).any { it.id == com.attentionstats.R.id.widget_root }) }
                action("Save Widget")
                val saved = context.getSharedPreferences("widgets", 0).getString("config_$id", null)
                expect(saved != null && com.attentionstats.widget.WidgetConfiguration.decode(saved) == defaults)
                activity = instrumentation.startActivitySync(Intent(context, WidgetConfigureActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                instrumentation.waitForIdleSync()
                action("Style ·"); action("Screen Time")
                action("Stats ·")
                ui {
                    val options = children(activity.window.decorView).filterIsInstance<Switch>()
                    val selected = options.filter { it.isChecked }
                    val choice = if (selected.size > 1) selected.first() else options.first { !it.isChecked }
                    choice.requestRectangleOnScreen(Rect(0, 0, choice.width, choice.height), true)
                    val before = choice.isChecked
                    choice.performClick()
                    expect(choice.isChecked != before)
                }
                action("Done"); action("Appearance ·")
                fun capture(name: String) {
                    if (expected != "adaptive") return
                    ui {
                        val decor = activity.window.decorView
                        fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
                        descendants(decor).filterIsInstance<android.widget.ScrollView>().firstOrNull()?.scrollTo(0, 0)
                        val bitmap = android.graphics.Bitmap.createBitmap(decor.width, decor.height, android.graphics.Bitmap.Config.ARGB_8888)
                        decor.draw(android.graphics.Canvas(bitmap))
                        java.io.File(context.cacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                    }
                }
                capture("widget-config-review.png")
                ui {
                    val slider = children(activity.window.decorView).filterIsInstance<android.widget.SeekBar>().single()
                    slider.requestFocus()
                    expect(slider.onKeyDown(android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_LEFT)))
                    expect(slider.progress < 100)
                }
                action("More Options"); capture("widget-config-more-review.png"); action("Corner Shape ·"); action("Soft Corners")
                action("Number Size ·"); action("Large Numbers")
                action("Theme ·"); action("Light"); action("Accent ·"); action("Teal"); action("Back to Widget")
                action("Preview Size ·"); action("Preview Size ·")
                ui { expect(children(activity.window.decorView).any { it.id == com.attentionstats.R.id.widget_root }) }
                action("Cancel")
                expect(context.getSharedPreferences("widgets", 0).getString("config_$id", null) == saved)
            } finally { ui { if (!activity.isFinishing) activity.finish() } }
            }
            return checks
        } finally {
            ids.forEach { host.deleteAppWidgetId(it) }
            host.deleteHost()
            // Clean only identifiers allocated by this test, without replacing real display preferences.
            val edit = context.getSharedPreferences("widgets", 0).edit()
            ids.forEach { edit.remove("config_$it").remove("stat_$it") }; edit.commit()
            expect(context.getSharedPreferences("widgets", 0).all == original)
        }
    }
}
