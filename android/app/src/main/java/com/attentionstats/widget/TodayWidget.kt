package com.attentionstats.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.attentionstats.MainActivity
import com.attentionstats.R
import com.attentionstats.reels.ReelsCoordinator

/** Values every widget variant can draw from; computed by the coordinator. */
internal data class WidgetData(
    val todayVideos: Long,
    val sessionVideos: Long,
    val watchingMs: Long,
    val screenMs: Long,
    val breaks: Long,
    val topVideoApps: List<Pair<String, Long>>,
    val topScreenApp: Pair<String, Long>?,
)

/** What a placed widget shows; chosen in [WidgetConfigureActivity] and changeable by long-press. */
internal enum class WidgetStat(val key: String, val title: String, val description: String) {
    OVERVIEW("overview", "Today at a glance", "Short videos, watching time, screen time and this session"),
    VIDEOS("videos", "Short videos today", "Today's count and which apps it came from"),
    SESSION("session", "This session", "Short videos since you unlocked your phone"),
    WATCHING("watching", "Watching today", "Time spent in short videos today"),
    SCREEN("screen", "Screen time today", "Time in all apps today, with your top app"),
    BREAKS("breaks", "Breaks today", "Mindful breaks offered and taken today");

    companion object { fun byKey(key: String?): WidgetStat = entries.firstOrNull { it.key == key } ?: OVERVIEW }
}

/** Home-screen glance with the stats you choose. Tap opens Today. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ReelsCoordinator.get(context).refreshWidget()
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val prefs = prefs(context).edit()
        ids.forEach { prefs.remove("stat_$it") }
        prefs.apply()
    }

    companion object {
        internal fun prefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
        internal fun statFor(context: Context, id: Int) = WidgetStat.byKey(prefs(context).getString("stat_$id", null))

        @Volatile private var latest: WidgetData? = null

        private fun duration(ms: Long): String {
            val minutes = ms / 60_000
            return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
        }

        private fun count(value: Long) = "%,d".format(value)

        internal fun render(context: Context, data: WidgetData? = latest) {
            if (data != null) latest = data
            val values = latest ?: return
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            for (id in ids) manager.updateAppWidget(id, views(context, statFor(context, id), values))
        }

        private fun views(context: Context, stat: WidgetStat, data: WidgetData): RemoteViews {
            if (stat == WidgetStat.OVERVIEW) return overview(context, data)
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val apps = topApps(data)
            val (value, unit, detail) = when (stat) {
                WidgetStat.VIDEOS -> Triple(count(data.todayVideos), "short videos", apps.ifEmpty { "This session: ${count(data.sessionVideos)}" })
                WidgetStat.SESSION -> Triple(count(data.sessionVideos), "since unlock", "Today: ${count(data.todayVideos)}")
                WidgetStat.WATCHING -> Triple(duration(data.watchingMs), "watching short videos", "${count(data.todayVideos)} videos today")
                WidgetStat.SCREEN -> Triple(duration(data.screenMs), "on your phone",
                    data.topScreenApp?.let { "Most: ${it.first} · ${duration(it.second)}" } ?: "Turn on screen time in the app")
                WidgetStat.BREAKS -> Triple(data.breaks.toString(), if (data.breaks == 1L) "mindful break" else "mindful breaks",
                    if (data.breaks == 0L) "Breaks appear after long runs" else "Every pause counts")
                WidgetStat.OVERVIEW -> Triple("", "", "")
            }
            views.setTextViewText(R.id.widget_title, stat.title)
            views.setTextViewText(R.id.widget_count, value)
            views.setTextViewText(R.id.widget_unit, unit)
            views.setTextViewText(R.id.widget_detail, detail)
            views.setOnClickPendingIntent(R.id.widget_root, openToday(context))
            return views
        }

        private fun overview(context: Context, data: WidgetData): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_overview)
            views.setTextViewText(R.id.ov_videos, count(data.todayVideos))
            views.setTextViewText(R.id.ov_watching, duration(data.watchingMs))
            views.setTextViewText(R.id.ov_screen, if (data.screenMs > 0) duration(data.screenMs) else "—")
            views.setTextViewText(R.id.ov_session, count(data.sessionVideos))
            views.setTextViewText(R.id.widget_detail, topApps(data).ifEmpty {
                data.topScreenApp?.let { "Most used: ${it.first}" } ?: "No short videos yet today"
            })
            views.setOnClickPendingIntent(R.id.widget_root, openToday(context))
            return views
        }

        private fun topApps(data: WidgetData) =
            data.topVideoApps.filter { it.second > 0 }.take(3).joinToString(" · ") { "${it.first} ${it.second}" }

        private fun openToday(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .setData(android.net.Uri.parse("attentionstats://today"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
