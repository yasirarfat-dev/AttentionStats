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
            .put("foregroundColor", prefs.getString("foregroundColor", "")).put("backgroundColor", prefs.getString("backgroundColor", ""))
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
        val legacyKeys = setOf("mode", "accent", "motion", "customColor", "corners", "spacing", "numberSize")
        val keys = json.keys().asSequence().toSet()
        require(keys == legacyKeys || keys == legacyKeys + setOf("foregroundColor", "backgroundColor"))
        if (keys != legacyKeys) require(listOf("foregroundColor", "backgroundColor").all { json.get(it) is String })
        val foreground = json.optString("foregroundColor"); val background = json.optString("backgroundColor")
        require(listOf(foreground, background).all { it.isEmpty() || it.matches(Regex("#[0-9a-fA-F]{6}")) })
        val mode = json.getString("mode"); val accent = json.getString("accent"); val color = json.getString("customColor")
        require(mode in setOf("system", "light", "dark") && accent in setOf("graphite", "blue", "teal", "violet"))
        require(color.isEmpty() || color.matches(Regex("#[0-9a-fA-F]{6}")))
        require(json.get("motion") is Boolean)
        listOf("corners", "spacing", "numberSize").forEach { require(json.get(it) is Int) }
        require(json.getInt("corners") in 0..32 && json.getInt("spacing") in 12..24 && json.getInt("numberSize") in 24..40)
        check(context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
            .putString("mode", mode).putString("accent", accent).putBoolean("motion", json.getBoolean("motion"))
            .putString("foregroundColor", foreground).putString("backgroundColor", background)
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
        var base = PaletteTokens.resolve(dark, prefs.getString("accent"))
        fun valid(value: String) = value.matches(Regex("#[0-9a-fA-F]{6}"))
        fun blend(a: Int, b: Int, amount: Float) = androidx.core.graphics.ColorUtils.blendARGB(a, b, amount)
        val foreground = prefs.optString("foregroundColor"); val background = prefs.optString("backgroundColor")
        val scoped = valid(foreground) || valid(background)
        val canvas = if (valid(background)) android.graphics.Color.parseColor(background) else base.canvas
        val effectiveDark = if (scoped) androidx.core.graphics.ColorUtils.calculateLuminance(canvas) < 0.179 else dark
        val toward = if (effectiveDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        fun readable(chosen: Int, against: List<Int>, destination: Int): Int {
            for (i in 0..100) {
                val candidate = blend(chosen, destination, i / 100f)
                if (against.all { androidx.core.graphics.ColorUtils.calculateContrast(candidate, it) >= 4.5 }) return candidate
            }
            return destination
        }
        val panelTone = if (effectiveDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        if (valid(background)) base = base.copy(canvas = canvas, surface = blend(canvas, panelTone, .035f),
            surfaceAlt = blend(canvas, panelTone, .07f), raised = blend(canvas, panelTone, .1f))
        val backgrounds = listOf(base.canvas, base.surface, base.surfaceAlt, base.raised)
        if (scoped) {
            val ink = if (valid(foreground)) android.graphics.Color.parseColor(foreground) else base.text
            val text = readable(ink, backgrounds, toward)
            val muted = readable(blend(text, canvas, .25f), backgrounds, toward)
            base = base.copy(text = text, muted = muted, faint = muted, border = blend(canvas, text, .22f), track = blend(canvas, text, .18f))
        }
        val color = prefs.optString("customColor")
        if (valid(color)) {
            val chosen = android.graphics.Color.parseColor(color)
            val soft = if (scoped) blend(chosen, canvas, .94f) else blend(chosen, if (effectiveDark) 0xFF141518.toInt() else android.graphics.Color.WHITE, if (effectiveDark) .83f else .92f)
            val accent = readable(chosen, listOf(soft, base.surfaceAlt, base.surface) + if (scoped) backgrounds else emptyList(), toward)
            val fill = readable(chosen, listOf(android.graphics.Color.WHITE), android.graphics.Color.BLACK)
            base = base.copy(accent = accent, accentFill = fill, accentSoft = soft, onAccent = android.graphics.Color.WHITE,
                hero = blend(fill, 0xFF08090B.toInt(), .45f), onHero = android.graphics.Color.WHITE, heroMuted = 0xFFEEEEEE.toInt(), tint = soft)
        } else if (scoped) {
            val soft = blend(base.accent, canvas, .94f)
            base = base.copy(accentSoft = soft, tint = soft, accent = readable(base.accent, backgrounds + soft, toward))
        }
        return base
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
