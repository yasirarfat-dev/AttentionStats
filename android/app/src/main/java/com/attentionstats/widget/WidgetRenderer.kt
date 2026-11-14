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
        val compact = height < 140 || width < 160
        val style = if (compact) WidgetStyle.SPOTLIGHT else config.style
        val root = RemoteViews(context.packageName, R.layout.widget_flexible)
        val panel = when (config.corners) {
            "soft" -> if (dark) R.drawable.widget_panel_dark_soft else R.drawable.widget_panel_light_soft
            "square" -> if (dark) R.drawable.widget_panel_dark_square else R.drawable.widget_panel_light_square
            else -> if (dark) R.drawable.widget_panel_dark else R.drawable.widget_panel_light
        }
        root.setImageViewResource(R.id.widget_background, panel)
        root.setInt(R.id.widget_background, "setImageAlpha", config.opacity * 255 / 100)
        root.setTextViewText(R.id.widget_title, when (style) { WidgetStyle.APPS -> "Reels By Apps"; WidgetStyle.RHYTHM -> "Hourly Encounters"; WidgetStyle.SCREEN -> "Screen Time · Today"; else -> "Attention Stats · Today" })
        root.setTextColor(R.id.widget_title, palette.accent)
        root.setTextColor(R.id.widget_detail, palette.muted)
        root.setViewVisibility(R.id.widget_header, if (compact) View.GONE else View.VISIBLE)
        root.setViewVisibility(R.id.widget_detail, if (compact || !config.timestamp) View.GONE else View.VISIBLE)
        root.setViewPadding(R.id.widget_content, dp(context, if (compact) 6 else 12), dp(context, if (compact) 6 else 12), dp(context, if (compact) 6 else 12), dp(context, if (compact) 6 else 12))
        root.setTextViewText(R.id.widget_detail, if (current == null) "Open App to Load Today's Stats" else "Updated " +
            Instant.ofEpochMilli(current.updatedMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mma")))
        if (interactive) root.setOnClickPendingIntent(R.id.widget_root, open(context, null))
        root.removeAllViews(R.id.widget_body)
        fun tile(stat: WidgetStat, list: Boolean = false): RemoteViews = RemoteViews(context.packageName, if (list) R.layout.widget_flex_line else if (style == WidgetStyle.SPOTLIGHT) when {
            compact -> R.layout.widget_spotlight_compact
            config.numberSize == "small" -> R.layout.widget_spotlight_small
            config.numberSize == "large" -> R.layout.widget_spotlight_large
            else -> R.layout.widget_spotlight_normal
        } else when (config.numberSize) {
            "small" -> R.layout.widget_flex_tile_small; "large" -> R.layout.widget_flex_tile_large; else -> R.layout.widget_flex_tile
        }).apply {
            setTextViewText(R.id.w_label, stat.title); setTextViewText(R.id.w_value, value(stat, current))
            setTextColor(R.id.w_label, palette.muted); setTextColor(R.id.w_value, palette.text)
            if (!list && style != WidgetStyle.SPOTLIGHT) {
                if (config.opacity == 100) setInt(R.id.w_tile, "setBackgroundResource", if (dark) R.drawable.widget_tile_dark else R.drawable.widget_tile_light)
                else setInt(R.id.w_tile, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
            }
            setContentDescription(R.id.w_tile, "${stat.title}: ${value(stat, current)}. ${stat.description}")
            if (interactive) setOnClickPendingIntent(R.id.w_tile, open(context, stat))
        }
        fun row(stats: List<WidgetStat>) = RemoteViews(context.packageName, R.layout.widget_flex_row).apply {
            removeAllViews(R.id.w_row); stats.forEach { addView(R.id.w_row, tile(it)) }
        }
        val columns = if (width >= 300) 3 else if (width >= 180) 2 else 1
        val rows = ((height - if (compact) 12 else 58) / 55).coerceIn(1, 3)
        fun screenBand(featured: Boolean = false) {
            val band = RemoteViews(context.packageName, if (featured) R.layout.widget_screen_focus else R.layout.widget_screen_band)
            band.setTextViewText(R.id.w_screen_value, if (featured) duration(current?.screenMs) else "Screen Time  ${duration(current?.screenMs)}")
            band.setTextViewText(R.id.w_screen_caption, "Reels Apps  ${duration(current?.reelsAppsMs)} · Whole-App Time")
            band.setTextColor(R.id.w_screen_value, palette.text)
            band.setTextColor(R.id.w_screen_caption, palette.muted)
            val total = current?.screenMs
            val part = current?.reelsAppsMs
            val comparable = total != null && part != null && total > 0 && part in 0..total
            band.setViewVisibility(R.id.w_screen_bar, if (comparable) View.VISIBLE else View.GONE)
            band.setProgressBar(R.id.w_screen_bar, 1000, if (comparable) (part.toDouble() / total * 1000).toInt() else 0, false)
            band.setContentDescription(R.id.w_screen_band, "Recorded screen time ${duration(total)}. Reels-app whole-app time ${duration(part)}. Includes non-video screens; partial recorded totals, not confirmed playback.")
            if (interactive) band.setOnClickPendingIntent(R.id.w_screen_band, open(context, WidgetStat.SCREEN))
            root.addView(R.id.widget_body, band)
        }
        when (style) {
            WidgetStyle.LIST -> config.stats.take(((height - 48) / 34).coerceIn(1, 9)).forEach { root.addView(R.id.widget_body, tile(it, true)) }
            WidgetStyle.SPOTLIGHT -> {
                root.addView(R.id.widget_body, row(config.stats.take(1)))
                if (!compact && height >= 180 && config.stats.size > 1) root.addView(R.id.widget_body, row(config.stats.drop(1).take(columns)))
            }
            WidgetStyle.SCREEN -> {
                screenBand(featured = height >= 230)
                if (height >= 230) config.stats.filter { it != WidgetStat.SCREEN && it != WidgetStat.REELS_APPS }.take(2).forEach { stat ->
                    root.addView(R.id.widget_body, RemoteViews(context.packageName, R.layout.widget_support_line).apply {
                        setTextViewText(R.id.w_label, stat.title); setTextViewText(R.id.w_value, value(stat, current))
                        setTextColor(R.id.w_label, palette.muted); setTextColor(R.id.w_value, palette.text)
                        setContentDescription(R.id.w_tile, "${stat.title}: ${value(stat, current)}. ${stat.description}")
                        if (interactive) setOnClickPendingIntent(R.id.w_tile, open(context, stat))
                    })
                }
            }
            WidgetStyle.APPS -> {
                if (height >= 230) root.addView(R.id.widget_body, row(config.stats.take(columns)))
                val sources = current?.topVideoApps?.filter { it.second > 0 }?.take(if (height >= 230) 4 else 2).orEmpty()
                if (sources.isEmpty()) root.addView(R.id.widget_body, tile(config.stats.first(), true))
                val max = sources.maxOfOrNull { it.second } ?: 1L
                sources.forEachIndexed { index, source ->
                    val name = if (config.names) source.first else "App ${index + 1}"
                    root.addView(R.id.widget_body, RemoteViews(context.packageName, R.layout.widget_app_row).apply {
                        val sourceApp = org.attentionstats.reels.ReelApp.entries.firstOrNull { it.displayName == source.first }
                        val icon = sourceApp?.let { com.attentionstats.appearance.AppIconLoader.bitmap(context, it.packageName, dp(context, 24), sourceAliases = true) }
                        if (icon != null) setImageViewBitmap(R.id.w_app_icon, icon)
                        else setImageViewResource(R.id.w_app_icon, R.drawable.ic_app_placeholder)
                        setTextViewText(R.id.w_label, name)
                        setTextViewText(R.id.w_value, count(source.second))
                        setTextColor(R.id.w_label, palette.muted); setTextColor(R.id.w_value, palette.text)
                        setProgressBar(R.id.w_app_bar, 1000, (source.second.toDouble() / max * 1000).toInt(), false)
                        setContentDescription(R.id.w_app_row, "$name: ${source.second} video encounters. Bar relative to the highest source count.")
                        if (interactive) setOnClickPendingIntent(R.id.w_app_row, open(context, WidgetStat.VIDEOS))
                    })
                }
            }
            WidgetStyle.RHYTHM -> {
                if (height >= 190) root.addView(R.id.widget_body, row(config.stats.take(columns)))
                val chartViews = RemoteViews(context.packageName, R.layout.widget_flex_chart)
                chartViews.setImageViewBitmap(R.id.w_chart, chart(current, palette.accent, width.coerceIn(110, 400)))
                listOf(R.id.w_axis_0, R.id.w_axis_1, R.id.w_axis_2, R.id.w_axis_3).forEach { chartViews.setTextColor(it, palette.muted) }
                chartViews.setContentDescription(R.id.w_chart, if (current == null) "No Current Observations" else
                    "Recorded media encounters by hour, including photos and ads. " + current.hours.mapIndexed { hour, count -> "$hour:00 $count" }.joinToString(", "))
                root.addView(R.id.widget_body, chartViews)
            }
            WidgetStyle.ADAPTIVE, WidgetStyle.TILES -> {
                val band = style == WidgetStyle.ADAPTIVE && height >= 230
                val capacity = columns * (if (band) rows - 1 else rows)
                var selected = config.stats.take(capacity)
                // Keep a multi-column dashboard balanced; resizing reveals another complete row.
                if (columns > 1 && selected.size > columns && selected.size % columns == 1) selected = selected.dropLast(1)
                selected.chunked(columns).forEach { root.addView(R.id.widget_body, row(it)) }
                if (band) screenBand()
            }
        }
        return root
    }

    private fun chart(data: WidgetData?, accent: Int, width: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (data == null) return bitmap
        val max = (data.hours.maxOrNull() ?: 0).coerceAtLeast(1)
        val step = bitmap.width / 24f
        paint.color = accent
        data.hours.forEachIndexed { index, value ->
            if (value > 0) canvas.drawRoundRect(index * step + 2, 80 - value.toFloat() / max * 76, (index + 1) * step - 2, 80f, 2f, 2f, paint)
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
