package com.attentionstats.bridge

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.attentionstats.reels.ReelsCoordinator
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.annotations.ReactModule

@ReactModule(name = DiaryModule.NAME)
class DiaryModule(private val context: ReactApplicationContext) : NativeDiarySpec(context) {
    override fun getName() = NAME
    private val coordinator get() = ReelsCoordinator.get(context)
    private fun failure(promise: Promise, code: String) = promise.reject(code, "Diary operation unavailable; saved history preserved")
    override fun getSnapshot(days: Double, promise: Promise) {
        if (days !in listOf(1.0, 7.0, 31.0)) { failure(promise, "INVALID_ARGUMENT"); return }
        coordinator.diarySnapshot(days.toInt(), { promise.resolve(com.attentionstats.diary.DiaryPresentationFixture.apply(it)) }, { failure(promise, it) })
    }
    override fun configure(action: String, enabled: Boolean, count: Double, minutes: Double, expectedRevision: String, promise: Promise) {
        val revision = expectedRevision.toLongOrNull()
        if (action !in (setOf("usage", "reminder", "onboarding", "ads", "break") + org.attentionstats.reels.ReelApp.entries.map { "block.${it.key}" }) || revision == null || revision < 0 ||
            !count.isFinite() || count % 1 != 0.0 || count !in 1.0..1000.0 ||
            !minutes.isFinite() || minutes % 1 != 0.0 || minutes !in 1.0..240.0) {
            failure(promise, "INVALID_ARGUMENT"); return
        }
        coordinator.configureDiary(action, enabled, count.toInt(), minutes.toInt(), revision,
            { promise.resolve(it) }, { failure(promise, it) })
    }
    override fun openUsageSettings(promise: Promise) {
        try {
            val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(direct) }
            catch (_: android.content.ActivityNotFoundException) {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            promise.resolve(true)
        } catch (_: RuntimeException) { promise.resolve(false) }
    }
    override fun openAppSettings(promise: Promise) {
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            promise.resolve(true)
        } catch (_: RuntimeException) { promise.resolve(false) }
    }
    /** PNG data URIs (96 px) for launcher icons, so lists show each app's own logo. */
    override fun getAppIcons(packages: com.facebook.react.bridge.ReadableArray, promise: Promise) {
        Thread {
            val result = org.json.JSONObject()
            val manager = context.packageManager
            val size = (48 * context.resources.displayMetrics.density).toInt().coerceIn(48, 144)
            for (index in 0 until minOf(packages.size(), 80)) {
                val name = packages.getString(index) ?: continue
                if (name.length > 256) continue
                val cached = iconCache[name]
                if (cached != null) { result.put(name, cached); continue }
                try {
                    val drawable = manager.getApplicationIcon(name)
                    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    drawable.setBounds(0, 0, size, size); drawable.draw(canvas)
                    val stream = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                    bitmap.recycle()
                    val uri = "data:image/png;base64," + android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
                    iconCache[name] = uri
                    result.put(name, uri)
                } catch (_: Exception) { }
            }
            promise.resolve(result.toString())
        }.start()
    }
    override fun openBatterySettings(promise: Promise) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            promise.resolve(true)
        } catch (_: RuntimeException) { promise.resolve(false) }
    }
    /** Asks the launcher to add the home-screen widget (Android 8+ launchers that support pinning). */
    override fun pinWidget(promise: Promise) {
        val manager = context.getSystemService(android.appwidget.AppWidgetManager::class.java)
        val provider = android.content.ComponentName(context, com.attentionstats.widget.TodayWidget::class.java)
        promise.resolve(android.os.Build.VERSION.SDK_INT >= 26 && manager?.isRequestPinAppWidgetSupported == true &&
            manager.requestPinAppWidget(provider, null, null))
    }
    companion object {
        const val NAME = "NativeDiary"
        private val iconCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    }
}
