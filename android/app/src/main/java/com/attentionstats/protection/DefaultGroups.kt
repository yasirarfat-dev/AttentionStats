package com.attentionstats.protection

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import org.attentionstats.protection.AppGroup
import org.attentionstats.reels.ReelApp

/** Editable starting points from installed metadata, never from personal activity. Unknown categories stay unknown. */
internal object DefaultGroups {
    fun create(context: Context, excluded: Set<String>): List<AppGroup> {
        val pm = context.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }.filter { it.packageName !in excluded }
        fun category(value: Int) = apps.filter { Build.VERSION.SDK_INT >= 26 && it.category == value }.map { it.packageName }.toSet()
        val reels = apps.map { it.packageName }.filter { it in ReelApp.allPackages }.toSet()
        val social = category(ApplicationInfo.CATEGORY_SOCIAL) + reels
        val games = category(ApplicationInfo.CATEGORY_GAME)
        val productive = category(ApplicationInfo.CATEGORY_PRODUCTIVITY)
        val essentialPackages = setOf("com.google.android.calendar", "com.samsung.android.calendar", "com.android.calendar",
            "com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock",
            "com.google.android.apps.maps", "com.sec.android.app.popupcalculator", "com.google.android.calculator",
            "com.android.calculator2", "com.google.android.contacts", "com.samsung.android.app.contacts")
        val essentials = apps.map { it.packageName }.filter { it in essentialPackages }.toSet()
        return listOf(Triple("reels", "Reels Apps", reels), Triple("social", "Social Apps", social),
            Triple("distracting", "Distracting Apps", social + games), Triple("games", "Games", games),
            Triple("productive", "Productive Apps", productive), Triple("essentials", "Essentials", essentials))
            .filter { it.third.isNotEmpty() }.map { (id, name, members) -> AppGroup("default-$id", name, members.sorted().take(100).toSet()) }
    }
}
