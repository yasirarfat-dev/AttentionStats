package com.attentionstats.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import com.attentionstats.reels.ReelsCoordinator

/** Independently configured, home-screen-only instances backed by native summaries. */
open class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { runCatching { renderOne(context, manager, it) } }
        ReelsCoordinator.get(context).refreshWidget()
    }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        runCatching { renderOne(context, manager, id) }
        ReelsCoordinator.get(context).refreshWidget()
    }
    override fun onDeleted(context: Context, ids: IntArray) {
        val edit = prefs(context).edit()
        ids.forEach { edit.remove("stat_$it").remove("config_$it") }
        edit.apply()
    }
    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        val prefs = prefs(context); val edit = prefs.edit()
        oldIds.zip(newIds).forEach { (old, new) ->
            prefs.getString("config_$old", null)?.let { edit.putString("config_$new", it) }
            prefs.getString("stat_$old", null)?.let { edit.putString("stat_$new", it) }
            if (old != new) edit.remove("config_$old").remove("stat_$old")
        }
        edit.apply()
        onUpdate(context, AppWidgetManager.getInstance(context), newIds)
    }
    companion object {
        internal val providers = listOf(TodayWidget::class.java, StatWidget::class.java, ScreenTimeWidget::class.java, ReelsAppsWidget::class.java, RhythmWidget::class.java)
        internal fun styleFor(name: String?): WidgetStyle = when (name) {
            StatWidget::class.java.name -> WidgetStyle.SPOTLIGHT
            ScreenTimeWidget::class.java.name -> WidgetStyle.SCREEN
            ReelsAppsWidget::class.java.name -> WidgetStyle.APPS
            RhythmWidget::class.java.name -> WidgetStyle.RHYTHM
            else -> WidgetStyle.ADAPTIVE
        }
        internal fun owns(context: Context, component: ComponentName?) = providers.any { component == ComponentName(context, it) }
        private val listeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()
        internal fun addListener(listener: () -> Unit) { listeners.add(listener) }
        internal fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
        internal fun prefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
        @Volatile internal var latest: WidgetData? = null
            private set
        internal fun clear(context: Context) { latest = null; render(context) }
        internal fun render(context: Context, data: WidgetData? = null) {
            if (data != null) latest = data
            val manager = AppWidgetManager.getInstance(context)
            providers.forEach { provider -> manager.getAppWidgetIds(ComponentName(context, provider)).forEach { runCatching { renderOne(context, manager, it) } } }
            listeners.forEach { runCatching { it() } }
        }
        internal fun renderOne(context: Context, manager: AppWidgetManager, id: Int) {
            val options = manager.getAppWidgetOptions(id)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180).coerceAtLeast(80)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110).coerceAtLeast(60)
            manager.updateAppWidget(id, WidgetRenderer.responsive(context, WidgetConfiguration.load(context, id), latest, width, height))
        }
    }
}

class StatWidget : TodayWidget()
class ScreenTimeWidget : TodayWidget()
class ReelsAppsWidget : TodayWidget()
class RhythmWidget : TodayWidget()
