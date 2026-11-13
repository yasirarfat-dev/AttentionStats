package com.attentionstats.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal enum class WidgetStyle(val key: String, val title: String, val detail: String) {
    ADAPTIVE("adaptive", "Today Overview", "A balanced summary with a screen-time comparison when expanded"),
    SPOTLIGHT("spotlight", "One Stat", "One prominent number with optional supporting values"),
    TILES("tiles", "Tiles", "An evenly spaced dashboard of your selected stats"),
    LIST("list", "List", "Clear labels and values in compact rows"),
    APPS("apps", "Reels By Apps", "Reels source counts with selected stats"),
    RHYTHM("rhythm", "Hourly Rhythm", "Recorded media encounters by hour with selected stats"),
    SCREEN("screen_time", "Screen Time", "Recorded app time and the portion spent in Reels apps");
    companion object {
        val offered = listOf(ADAPTIVE, SPOTLIGHT, SCREEN, APPS, RHYTHM)
        val layouts = offered + listOf(TILES, LIST)
        fun byKey(key: String) = entries.firstOrNull { it.key == key }
    }
}

internal enum class WidgetStat(val key: String, val title: String, val description: String) {
    VIDEOS("videos", "Short Videos", "Recorded video encounters today"),
    SESSION("session", "This Session", "Short-video encounters in the current observed unlock session"),
    WATCHING("watching", "On Screen", "Today's recorded short-video surface time; not confirmed playback"),
    SCREEN("screen", "Screen Time", "Known whole-app time across recorded apps today"),
    REELS_APPS("reels_apps", "Reels Apps", "Whole-app time in supported Reels apps, including non-video screens"),
    LAUNCHES("launches", "App Launches", "Observed app entries and returns, not just cold starts"),
    BREAKS("breaks", "Mindful Breaks", "Breaks offered today"),
    PHOTOS("photos", "Photos", "Recorded photo encounters today"),
    ADS("ads", "Ads", "Ad encounters; can overlap media counts"),
    PLAYBACK("playback", "Playback", "Confirmed numeric playback only; unavailable signals stay unknown"),
    FOCUS("focus", "Focus Remaining", "Remaining active focus time; taps open the focus screen"),
    APPS("apps", "Observed Apps", "Apps with recorded time or entries today");
    companion object { fun byKey(key: String) = entries.firstOrNull { it.key == key } }
}

internal data class WidgetConfiguration(
    val style: WidgetStyle = WidgetStyle.ADAPTIVE,
    val stats: List<WidgetStat> = listOf(WidgetStat.SESSION, WidgetStat.VIDEOS, WidgetStat.WATCHING,
        WidgetStat.REELS_APPS, WidgetStat.LAUNCHES, WidgetStat.BREAKS),
    val theme: String = "app", val accent: String = "app", val names: Boolean = false,
    val opacity: Int = 100, val corners: String = "rounded", val numberSize: String = "normal", val timestamp: Boolean = true,
) {
    fun validate() {
        require(opacity in 0..100 && corners in setOf("rounded", "soft", "square") && numberSize in setOf("small", "normal", "large"))
        require(stats.size in 1..9 && stats.distinct().size == stats.size)
        require(theme in setOf("app", "light", "dark") && accent in setOf("app", "graphite", "blue", "teal", "violet"))
    }
    fun encode(): String {
        validate()
        return JSONObject().put("version", 2).put("style", style.key).put("stats", JSONArray(stats.map { it.key }))
            .put("theme", theme).put("accent", accent).put("names", names).put("opacity", opacity).put("corners", corners).put("numberSize", numberSize).put("timestamp", timestamp).toString()
    }
    companion object {
        fun defaults(style: WidgetStyle) = WidgetConfiguration(style = style, stats = when (style) {
            WidgetStyle.SCREEN -> listOf(WidgetStat.SCREEN, WidgetStat.REELS_APPS)
            WidgetStyle.APPS -> listOf(WidgetStat.VIDEOS, WidgetStat.WATCHING)
            WidgetStyle.RHYTHM -> listOf(WidgetStat.VIDEOS, WidgetStat.PHOTOS)
            WidgetStyle.SPOTLIGHT -> listOf(WidgetStat.VIDEOS)
            else -> WidgetConfiguration().stats
        })
        fun decode(raw: String): WidgetConfiguration {
            require(raw.length <= 4096)
            val value = JSONObject(raw)
            val version = value.get("version")
            require(version == 1 || version == 2)
            val keys = setOf("version", "style", "stats", "theme", "accent", "names") + if (version == 2) setOf("opacity", "corners", "numberSize", "timestamp") else emptySet()
            require(value.keys().asSequence().toSet() == keys && value.get("names") is Boolean)
            if (version == 2) require(value.get("opacity") is Int && value.get("timestamp") is Boolean)
            val rows = value.getJSONArray("stats")
            return WidgetConfiguration(requireNotNull(WidgetStyle.byKey(value.getString("style"))),
                (0 until rows.length()).map { requireNotNull(WidgetStat.byKey(rows.getString(it))) },
                value.getString("theme"), value.getString("accent"), value.getBoolean("names"),
                if (version == 2) value.getInt("opacity") else 100,
                if (version == 2) value.getString("corners") else "rounded",
                if (version == 2) value.getString("numberSize") else "normal",
                if (version == 2) value.getBoolean("timestamp") else true).also { it.validate() }
        }
        fun load(context: Context, id: Int): WidgetConfiguration {
            val prefs = TodayWidget.prefs(context)
            runCatching { prefs.getString("config_$id", null) }.getOrNull()?.let { raw -> runCatching { decode(raw) }.getOrNull()?.let { return it } }
            val legacy = runCatching { prefs.getString("stat_$id", "overview") }.getOrNull()
            return WidgetStat.byKey(legacy ?: "")?.let { WidgetConfiguration(WidgetStyle.SPOTLIGHT, listOf(it)) }
                ?: defaults(TodayWidget.styleFor(android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className))
        }
        fun save(context: Context, id: Int, config: WidgetConfiguration): Boolean {
            require(id > 0)
            return TodayWidget.prefs(context).edit().putString("config_$id", config.encode()).remove("stat_$id").commit()
        }
    }
}
