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
    }
    fun save(context: Context, mode: String, accent: String, motion: Boolean): String {
        require(mode in setOf("system", "light", "dark") && accent in setOf("graphite", "blue", "teal", "violet"))
        check(context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
            .putString("mode", mode).putString("accent", accent).putBoolean("motion", motion).commit())
        return snapshot(context).toString()
    }
    fun dark(context: Context): Boolean = when (snapshot(context).getString("mode")) {
        "dark" -> true; "light" -> false
        else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
    fun palette(context: Context): NativePalette = PaletteTokens.resolve(dark(context), snapshot(context).getString("accent"))
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
