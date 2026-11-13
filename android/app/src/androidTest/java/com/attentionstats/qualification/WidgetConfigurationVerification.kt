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

/** Real configuration/preview, canceled without saving. Temporary host only if none exists. */
internal object WidgetConfigurationVerification {
    fun run(instrumentation: Instrumentation): Int {
        val context = instrumentation.targetContext
        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, TodayWidget::class.java)
        val original = context.getSharedPreferences("widgets", 0).all.toMap()
        var host: AppWidgetHost? = null
        var id = manager.getAppWidgetIds(provider).firstOrNull()
        var checks = 0
        fun expect(value: Boolean) { check(value) { "widget configuration ${checks + 1}" }; checks++ }
        try {
            if (id == null) {
                val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                automation.adoptShellPermissionIdentity(android.Manifest.permission.BIND_APPWIDGET)
                try {
                    val owner = AppWidgetHost(context, UUID.randomUUID().hashCode() and Int.MAX_VALUE).also { host = it }
                    id = owner.allocateAppWidgetId()
                    expect(manager.bindAppWidgetIdIfAllowed(checkNotNull(id), provider))
                } finally { automation.dropShellPermissionIdentity() }
            }
            val activity = instrumentation.startActivitySync(Intent(context, WidgetConfigureActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, checkNotNull(id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fun children(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { children(view.getChildAt(it)) } else emptyList()
            fun action(prefix: String) {
                instrumentation.runOnMainSync {
                    val text = children(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString().startsWith(prefix) }
                    var view: View = text
                    while (!view.isClickable && view.parent is View) view = view.parent as View
                    view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
                    expect(view.performClick())
                }
                instrumentation.waitForIdleSync()
            }
            try {
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync { expect(children(activity.window.decorView).any { it.id == com.attentionstats.R.id.widget_root }) }
                action("Style ·"); action("List")
                action("Stats ·")
                instrumentation.runOnMainSync {
                    val options = children(activity.window.decorView).filterIsInstance<Switch>()
                    val selected = options.filter { it.isChecked }
                    val choice = if (selected.size > 1) selected.first() else options.first { !it.isChecked }
                    choice.requestRectangleOnScreen(Rect(0, 0, choice.width, choice.height), true)
                    expect(choice.performClick())
                }
                action("Done"); action("Appearance ·"); action("Light"); action("Teal"); action("Back to Widget")
                action("Preview Size ·"); action("Preview Size ·")
                instrumentation.runOnMainSync { expect(children(activity.window.decorView).any { it.id == com.attentionstats.R.id.widget_root }) }
                action("Cancel")
                expect(context.getSharedPreferences("widgets", 0).all == original)
            } finally { instrumentation.runOnMainSync { if (!activity.isFinishing) activity.finish() } }
            return checks
        } finally {
            host?.let { owner -> id?.let { owner.deleteAppWidgetId(it) }; owner.deleteHost() }
        }
    }
}
