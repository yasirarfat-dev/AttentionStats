package com.attentionstats.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import com.attentionstats.reels.ReelsCoordinator

/** "What should this widget show?" Shown when a widget is added, and on long-press → reconfigure. */
class WidgetConfigureActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var page: com.attentionstats.appearance.NativePage
    override fun onCreate(savedInstanceState: Bundle?) {
        com.attentionstats.appearance.AppearanceOwner.prepare(this)
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        page = com.attentionstats.appearance.NativePage(this)
        page.text("Make it yours", 32f)
        page.text("Choose what your widget shows. Long-press it on your home screen to change this later.")
        page.text("At a glance", 21f)
        val current = TodayWidget.statFor(this, widgetId)
        WidgetStat.entries.forEach { stat -> page.choice(stat.title, stat.description, stat == current) { choose(stat) } }
    }
    override fun onResume() { super.onResume(); if (::page.isInitialized) page.refresh() }

    private fun choose(stat: WidgetStat) {
        TodayWidget.prefs(this).edit().putString("stat_$widgetId", stat.key).apply()
        TodayWidget.render(this)
        ReelsCoordinator.get(this).refreshWidget()
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
