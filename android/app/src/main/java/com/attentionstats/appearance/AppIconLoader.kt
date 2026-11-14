package com.attentionstats.appearance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import org.attentionstats.reels.ReelApp

/** Local package icons only. Source aliases cover installed regional/lite editions. */
internal object AppIconLoader {
    fun bitmap(context: Context, packageName: String, size: Int, sourceAliases: Boolean = false): Bitmap? {
        val candidates = listOf(packageName) + (if (sourceAliases) ReelApp.byPackage(packageName)?.packages ?: emptyList() else emptyList())
        for (candidate in candidates.distinct()) {
            val drawable = try { context.packageManager.getApplicationIcon(candidate) }
                catch (_: android.content.pm.PackageManager.NameNotFoundException) { continue }
                catch (_: SecurityException) { continue }
            val bitmap = Bitmap.createBitmap(size.coerceIn(24, 144), size.coerceIn(24, 144), Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
            return bitmap
        }
        return null
    }
}
