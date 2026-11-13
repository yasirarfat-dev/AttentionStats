package com.attentionstats.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.attentionstats.MainActivity
import com.attentionstats.R
import com.attentionstats.appearance.AppearanceOwner
import com.attentionstats.appearance.PaletteTokens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** RemoteViews are bounded to six sizes and at most nine stats, with no persisted fact cache. */
internal object WidgetRenderer {
    val sizes = listOf(SizeF(110f, 80f), SizeF(180f, 110f), SizeF(180f, 190f), SizeF(260f, 150f), SizeF(300f, 230f), SizeF(360f, 300f))
    fun responsive(context: Context, config: WidgetConfiguration, data: WidgetData?, width: Int, height: Int): RemoteViews =
        if (Build.VERSION.SDK_INT >= 31) RemoteViews(sizes.associateWith { views(context, config, data, it.width.toInt(), it.height.toInt()) })
        else views(context, config, data, width, height)

    fun duration(ms: Long?): String {
        if (ms == null) return "—"
        if (ms < 60_000) return "${ms / 1000}s"
        val minutes = ms / 60_000
        return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
    }
    private fun count(value: Long): String {
        val scale = listOf(1_000_000_000_000_000_000L to "E", 1_000_000_000_000_000L to "Q", 1_000_000_000_000L to "T", 1_000_000_000L to "B", 1_000_000L to "M").firstOrNull { value >= it.first }
        return if (scale == null) "%,d".format(value) else "${value / scale.first}.${value % scale.first / (scale.first / 10)}${scale.second}"
    }
    fun value(stat: WidgetStat, data: WidgetData?): String {
        if (data == null) return "—"
        return when (stat) {
            WidgetStat.VIDEOS -> count(data.todayVideos)
            WidgetStat.SESSION -> count(data.sessionVideos)
            WidgetStat.WATCHING -> duration(data.watchingMs)
            WidgetStat.SCREEN -> duration(data.screenMs)
            WidgetStat.REELS_APPS -> duration(data.reelsAppsMs)
            WidgetStat.LAUNCHES -> data.launches?.let(::count) ?: "—"
            WidgetStat.BREAKS -> count(data.breaks)
            WidgetStat.PHOTOS -> count(data.photos)
            WidgetStat.ADS -> count(data.ads)
            WidgetStat.PLAYBACK -> duration(data.playbackMs)
            WidgetStat.FOCUS -> data.focusRemainingMs?.let(::duration) ?: "Off"
            WidgetStat.APPS -> count(data.observedApps)
        }
    }

    fun views(context: Context, config: WidgetConfiguration, data: WidgetData?, width: Int, height: Int, interactive: Boolean = true): RemoteViews {
        config.validate()
        val dark = when (config.theme) { "light" -> false; "dark" -> true; else -> AppearanceOwner.dark(context) }
        val accent = if (config.accent == "app") AppearanceOwner.snapshot(context).getString("accent") else config.accent
        val palette = PaletteTokens.resolve(dark, accent)
        val current = data?.takeIf { it.day == LocalDate.now().toString() }
        val compact = height < 110 || width < 160
        val style = if (compact) WidgetStyle.SPOTLIGHT else config.style
        val root = RemoteViews(context.packageName, R.layout.widget_flexible)
        root.setInt(R.id.widget_root, "setBackgroundResource", if (dark) R.drawable.widget_panel_dark else R.drawable.widget_panel_light)
        root.setTextViewText(R.id.widget_title, when (style) { WidgetStyle.APPS -> "Reels By Apps"; WidgetStyle.RHYTHM -> "Hourly Encounters"; else -> "Attention Stats · Today" })
        root.setTextColor(R.id.widget_title, palette.accent)
        root.setTextColor(R.id.widget_detail, palette.muted)
        root.setViewVisibility(R.id.widget_title, if (compact) View.GONE else View.VISIBLE)
        root.setViewVisibility(R.id.widget_detail, if (compact) View.GONE else View.VISIBLE)
        root.setViewPadding(R.id.widget_root, dp(context, if (compact) 6 else 12), dp(context, if (compact) 6 else 12), dp(context, if (compact) 6 else 12), dp(context, if (compact) 6 else 12))
        root.setTextViewText(R.id.widget_detail, if (current == null) "Open App to Load Today's Stats" else "Updated " +
            Instant.ofEpochMilli(current.updatedMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mma")))
        if (interactive) root.setOnClickPendingIntent(R.id.widget_root, open(context, null))
        root.removeAllViews(R.id.widget_body)
        fun tile(stat: WidgetStat, list: Boolean = false): RemoteViews = RemoteViews(context.packageName, if (list) R.layout.widget_flex_line else R.layout.widget_flex_tile).apply {
            setTextViewText(R.id.w_label, stat.title); setTextViewText(R.id.w_value, value(stat, current))
            setTextColor(R.id.w_label, palette.muted); setTextColor(R.id.w_value, palette.text)
            if (!list) setInt(R.id.w_tile, "setBackgroundResource", if (dark) R.drawable.widget_tile_dark else R.drawable.widget_tile_light)
            setContentDescription(R.id.w_tile, "${stat.title}: ${value(stat, current)}. ${stat.description}")
            if (interactive) setOnClickPendingIntent(R.id.w_tile, open(context, stat))
        }
        fun row(stats: List<WidgetStat>) = RemoteViews(context.packageName, R.layout.widget_flex_row).apply {
            removeAllViews(R.id.w_row); stats.forEach { addView(R.id.w_row, tile(it)) }
        }
        val columns = if (width >= 300) 3 else if (width >= 180) 2 else 1
        val rows = ((height - if (compact) 12 else 58) / 55).coerceIn(1, 3)
        when (style) {
            WidgetStyle.LIST -> config.stats.take(((height - 48) / 34).coerceIn(1, 9)).forEach { root.addView(R.id.widget_body, tile(it, true)) }
            WidgetStyle.SPOTLIGHT -> {
                root.addView(R.id.widget_body, row(config.stats.take(1)))
                if (!compact && height >= 180 && config.stats.size > 1) root.addView(R.id.widget_body, row(config.stats.drop(1).take(columns)))
            }
            WidgetStyle.APPS, WidgetStyle.RHYTHM -> {
                root.addView(R.id.widget_body, row(config.stats.take(columns)))
                if (height >= 150) {
                    val chartViews = RemoteViews(context.packageName, R.layout.widget_flex_chart)
                    val bitmap = chart(current, config, style, palette.accent, palette.muted, width.coerceIn(110, 400))
                    chartViews.setImageViewBitmap(R.id.w_chart, bitmap)
                    chartViews.setContentDescription(R.id.w_chart, if (current == null) "No Current Observations" else if (style == WidgetStyle.RHYTHM)
                        "Recorded media encounters by hour, including photos and ads. " + current.hours.mapIndexed { hour, count -> "$hour:00 $count" }.joinToString(", ")
                        else current.topVideoApps.filter { it.second > 0 }.take(4).mapIndexed { index, source -> "${if (config.names) source.first else "App ${index + 1}"}: ${source.second} video encounters" }.joinToString(", ").ifEmpty { "No Saved Video Encounters" })
                    root.addView(R.id.widget_body, chartViews)
                }
            }
            WidgetStyle.ADAPTIVE, WidgetStyle.TILES -> config.stats.take(columns * rows).chunked(columns).forEach { root.addView(R.id.widget_body, row(it)) }
        }
        return root
    }

    private fun chart(data: WidgetData?, config: WidgetConfiguration, style: WidgetStyle, accent: Int, muted: Int, width: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = muted; paint.textSize = 10f
        if (data == null) { canvas.drawText("No Current Observations", 4f, 40f, paint); return bitmap }
        if (style == WidgetStyle.RHYTHM) {
            val max = (data.hours.maxOrNull() ?: 0).coerceAtLeast(1)
            val step = bitmap.width / 24f
            paint.color = accent
            data.hours.forEachIndexed { index, value -> canvas.drawRoundRect(index * step + 2, 64 - value.toFloat() / max * 56, (index + 1) * step - 2, 64f, 2f, 2f, paint) }
            paint.color = muted
            listOf(0 to "12AM", 6 to "6AM", 12 to "12PM", 18 to "6PM").forEach { (hour, label) -> canvas.drawText(label, hour * step + 2, 77f, paint) }
        } else {
            val sources = data.topVideoApps.filter { it.second > 0 }.take(4)
            if (sources.isEmpty()) { canvas.drawText("No Saved Video Encounters", 4f, 40f, paint); return bitmap }
            val max = sources.maxOf { it.second }.coerceAtLeast(1)
            sources.forEachIndexed { index, source ->
                val top = index * 19.5f
                paint.color = muted; canvas.drawText(if (config.names) source.first else "App ${index + 1}", 3f, top + 9, paint)
                val value = count(source.second); canvas.drawText(value, bitmap.width - paint.measureText(value) - 3, top + 9, paint)
                paint.color = accent; canvas.drawRoundRect(3f, top + 12, 3 + (bitmap.width - 6) * source.second.toFloat() / max, top + 15.5f, 3f, 3f, paint)
            }
        }
        return bitmap
    }
    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun open(context: Context, stat: WidgetStat?): PendingIntent {
        val destination = if (stat == WidgetStat.FOCUS) "protect/focus" else "today"
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse("attentionstats://$destination"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 100 + (stat?.ordinal ?: 99), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
