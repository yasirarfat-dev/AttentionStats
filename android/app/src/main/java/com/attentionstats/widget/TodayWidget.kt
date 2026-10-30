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

/** Home-screen glance: today's short videos, this unlock, and the top apps. Tap opens Today. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ReelsCoordinator.get(context).refreshWidget()
    }

    companion object {
        fun render(context: Context, today: Long, session: Long, top: List<Pair<String, Long>>) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setTextViewText(R.id.widget_count, "%,d".format(today))
            val apps = top.filter { it.second > 0 }.take(3).joinToString(" · ") { "${it.first} ${it.second}" }
            views.setTextViewText(R.id.widget_detail, buildString {
                append("$session this session")
                if (apps.isNotEmpty()) append("\n").append(apps)
            })
            val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .setData(android.net.Uri.parse("attentionstats://today"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(ids, views)
        }
    }
}
