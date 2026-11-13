package com.attentionstats.appearance

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import org.json.JSONObject
import com.attentionstats.R

/** Non-sensitive visual preferences, independent of observation consent and history. */
internal object AppearanceOwner {
    fun snapshot(context: Context): JSONObject {
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        return JSONObject().put("mode", prefs.getString("mode", "system"))
            .put("accent", prefs.getString("accent", "graphite")).put("motion", prefs.getBoolean("motion", true))
            .put("customColor", prefs.getString("customColor", "")).put("corners", prefs.getInt("corners", 24))
            .put("spacing", prefs.getInt("spacing", 20)).put("numberSize", prefs.getInt("numberSize", 30))
    }
    fun save(context: Context, mode: String, accent: String, motion: Boolean): String {
        require(mode in setOf("system", "light", "dark") && accent in setOf("graphite", "blue", "teal", "violet"))
        check(context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
            .putString("mode", mode).putString("accent", accent).putBoolean("motion", motion).commit())
        return snapshot(context).toString()
    }
    fun configure(context: Context, document: String): String {
        require(document.length <= 1024)
        val json = JSONObject(document)
        require(json.keys().asSequence().toSet() == setOf("mode", "accent", "motion", "customColor", "corners", "spacing", "numberSize"))
        val mode = json.getString("mode"); val accent = json.getString("accent"); val color = json.getString("customColor")
        require(mode in setOf("system", "light", "dark") && accent in setOf("graphite", "blue", "teal", "violet"))
        require(color.isEmpty() || color.matches(Regex("#[0-9a-fA-F]{6}")))
        require(json.get("motion") is Boolean)
        listOf("corners", "spacing", "numberSize").forEach { require(json.get(it) is Int) }
        require(json.getInt("corners") in 0..32 && json.getInt("spacing") in 12..24 && json.getInt("numberSize") in 24..40)
        check(context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
            .putString("mode", mode).putString("accent", accent).putBoolean("motion", json.getBoolean("motion"))
            .putString("customColor", color).putInt("corners", json.getInt("corners"))
            .putInt("spacing", json.getInt("spacing")).putInt("numberSize", json.getInt("numberSize")).commit())
        return snapshot(context).toString()
    }
    fun dark(context: Context): Boolean = when (snapshot(context).getString("mode")) {
        "dark" -> true; "light" -> false
        else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
    fun palette(context: Context): NativePalette {
        val dark = dark(context); val prefs = snapshot(context)
        val base = PaletteTokens.resolve(dark, prefs.getString("accent"))
        val color = prefs.optString("customColor")
        if (!color.matches(Regex("#[0-9a-fA-F]{6}"))) return base
        val chosen = android.graphics.Color.parseColor(color)
        fun blend(a: Int, b: Int, amount: Float) = androidx.core.graphics.ColorUtils.blendARGB(a, b, amount)
        fun readable(against: List<Int>, toward: Int): Int {
            for (i in 0..100) {
                val candidate = blend(chosen, toward, i / 100f)
                if (against.all { androidx.core.graphics.ColorUtils.calculateContrast(candidate, it) >= 4.5 }) return candidate
            }
            return toward
        }
        val soft = blend(chosen, if (dark) 0xFF141518.toInt() else android.graphics.Color.WHITE, if (dark) 0.83f else 0.92f)
        val accent = readable(listOf(soft, base.surfaceAlt, base.surface), if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
        val fill = readable(listOf(android.graphics.Color.WHITE), android.graphics.Color.BLACK)
        return base.copy(accent = accent, accentFill = fill, accentSoft = soft, onAccent = android.graphics.Color.WHITE,
            hero = blend(fill, 0xFF08090B.toInt(), 0.45f), onHero = android.graphics.Color.WHITE, heroMuted = 0xFFEEEEEE.toInt(), tint = soft)
    }
    fun prepare(activity: Activity) {
        val dark = dark(activity)
        val style = when (snapshot(activity).getString("accent")) {
            "graphite" -> if (dark) R.style.AttentionNativeDarkGraphite else R.style.AttentionNativeLightGraphite
            "teal" -> if (dark) R.style.AttentionNativeDarkTeal else R.style.AttentionNativeLightTeal
            "violet" -> if (dark) R.style.AttentionNativeDarkViolet else R.style.AttentionNativeLightViolet
            else -> if (dark) R.style.AttentionNativeDark else R.style.AttentionNativeLight
        }
        activity.setTheme(style)
    }
    fun motion(context: Context) = snapshot(context).getBoolean("motion") &&
        (android.os.Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled())
}
