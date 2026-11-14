package com.attentionstats.qualification

import android.app.Instrumentation
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Parcel
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.attentionstats.MainActivity
import com.attentionstats.R
import com.attentionstats.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** Synthetic projections and actual attached RemoteViews hosts; no launcher/widget/history mutation. */
internal object WidgetVerification {
    fun run(instrumentation: Instrumentation): Int {
        val target = instrumentation.targetContext
        val prefName = "widget-qualification-${UUID.randomUUID()}"
        val isolated = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences(prefName, mode)
        }
        var checks = 0
        fun expect(value: Boolean) { check(value) { "widget check ${checks + 1}" }; checks++ }
        val today = LocalDate.now().toString()
        fun app(pkg: String, social: Any, time: Any, opens: Any) = JSONObject().put("packageName", pkg).put("name", "Synthetic App")
            .put("social", social).put("appMs", time).put("appOpens", opens).put("reelsMs", "2000").put("videoCount", "7")
            .put("photoCount", "2").put("adCount", "1").put("playbackMs", JSONObject.NULL)
        val apps = JSONArray().put(app("com.example.main", "instagram", "1000", "1"))
            .put(app("com.example.variant", "instagram", "500", "2"))
            .put(app("com.example.utility", JSONObject.NULL, "0", "0"))
        val snapshot = JSONObject().put("usageEnabled", true).put("usageGranted", true)
            .put("days", JSONArray().put(JSONObject().put("day", today).put("apps", apps).put("interventions", JSONObject().put("break.continued", 1))
                .put("hours", JSONArray(List(24) { if (it == 12) "7" else "0" }))))
        val data = WidgetData.fromSnapshot(snapshot, 3, null, System.currentTimeMillis())
        try {
            expect(data.todayVideos == 7L && data.watchingMs == 2000L)
            expect(data.photos == 2L && data.ads == 1L && data.playbackMs == null)
            expect(data.screenMs == 1500L && data.reelsAppsMs == 1500L && data.launches == 3L)
            expect(data.observedApps == 3L && data.breaks == 1L)
            expect(WidgetRenderer.value(WidgetStat.PLAYBACK, data) == "—")
            expect(WidgetRenderer.value(WidgetStat.SCREEN, data.copy(screenMs = 0)) == "0s")
            expect(WidgetRenderer.value(WidgetStat.SCREEN, data.copy(screenMs = null)) == "—")
            expect(WidgetRenderer.value(WidgetStat.VIDEOS, data.copy(todayVideos = 0)) == "0")
            expect(WidgetRenderer.value(WidgetStat.VIDEOS, data.copy(todayVideos = Long.MAX_VALUE)) == "9.2E")
            val flexible = WidgetConfiguration(opacity = 35, corners = "soft", numberSize = "large", timestamp = false)
            expect(WidgetConfiguration.decode(flexible.encode()) == flexible)
            val old = JSONObject(flexible.encode()).put("version", 1).apply { listOf("opacity", "corners", "numberSize", "timestamp").forEach { remove(it) } }
            expect(WidgetConfiguration.decode(old.toString()).opacity == 100)
            expect(WidgetConfiguration.defaults(WidgetStyle.APPS).names)
            expect(WidgetConfiguration.decode(JSONObject(flexible.encode()).put("version", 2).put("names", false).toString()).names)
            expect(!WidgetConfiguration.decode(flexible.copy(names = false).encode()).names)
            expect(runCatching { WidgetConfiguration.decode(JSONObject(flexible.encode()).put("opacity", -1).toString()) }.isFailure)
            expect(WidgetConfiguration().stats.take(2) == listOf(WidgetStat.SESSION, WidgetStat.VIDEOS))
            val first = WidgetConfiguration(WidgetStyle.RHYTHM, listOf(WidgetStat.PLAYBACK, WidgetStat.VIDEOS), "light", "teal")
            val second = WidgetConfiguration(WidgetStyle.LIST, listOf(WidgetStat.SCREEN), "dark", "violet", true)
            expect(WidgetConfiguration.decode(first.encode()) == first)
            expect(WidgetConfiguration.save(isolated, 101, first) && WidgetConfiguration.save(isolated, 202, second))
            expect(WidgetConfiguration.load(isolated, 101) == first && WidgetConfiguration.load(isolated, 202) == second)
            TodayWidget().onDeleted(isolated, intArrayOf(101))
            expect(WidgetConfiguration.load(isolated, 101) == WidgetConfiguration() && WidgetConfiguration.load(isolated, 202) == second)
            isolated.getSharedPreferences("widgets", Context.MODE_PRIVATE).edit().putString("stat_303", "watching").commit()
            expect(WidgetConfiguration.load(isolated, 303).style == WidgetStyle.SPOTLIGHT && WidgetConfiguration.load(isolated, 303).stats == listOf(WidgetStat.WATCHING))
            for (invalid in listOf(
                JSONObject(first.encode()).put("stats", JSONArray()),
                JSONObject(first.encode()).put("stats", JSONArray(listOf("videos", "videos"))),
                JSONObject(first.encode()).put("theme", "unsupported"),
                JSONObject(first.encode()).put("names", "true"),
                JSONObject(first.encode()).put("extra", "unexpected"))) expect(runCatching { WidgetConfiguration.decode(invalid.toString()) }.isFailure)
            val activity = instrumentation.startActivitySync(Intent(target, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fun texts(view: View): List<TextView> = if (view is TextView) listOf(view) else if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList()
            try {
                SystemClock.sleep(1500)
                val info = AppWidgetManager.getInstance(target).installedProviders.first { it.provider.packageName == target.packageName && it.provider.className.endsWith("TodayWidget") }
                fun ui(action: () -> Unit) {
                    var failure: Throwable? = null
                    instrumentation.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
                    failure?.let { throw it }
                }
                fun attach(config: WidgetConfiguration, sample: WidgetData?, width: Int, height: Int): AppWidgetHostView {
                    lateinit var host: AppWidgetHostView
                    val density = activity.resources.displayMetrics.density
                    val w = (width * density).toInt(); val h = (height * density).toInt()
                    ui {
                        val parent = FrameLayout(activity)
                        host = AppWidgetHostView(activity); host.setAppWidget(0, info)
                        activity.setContentView(parent)
                        parent.addView(host, FrameLayout.LayoutParams(w, h))
                        host.updateAppWidget(WidgetRenderer.views(activity, config, sample, width, height))
                    }
                    instrumentation.waitForIdleSync()
                    ui {
                        host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                        host.layout(0, 0, w, h)
                        expect(host.isAttachedToWindow)
                        check(host.findViewById<View>(R.id.widget_body) != null) { "Missing widget body: " + texts(host).joinToString { it.text.toString() } }
                    }
                    return host
                }
                for (style in WidgetStyle.entries) for (theme in listOf("light", "dark")) for ((width, height) in WidgetRenderer.sizes.map { it.width.toInt() to it.height.toInt() }) {
                    try {
                        val config = WidgetConfiguration(style = style, stats = listOf(WidgetStat.VIDEOS, WidgetStat.SESSION, WidgetStat.WATCHING, WidgetStat.REELS_APPS, WidgetStat.LAUNCHES, WidgetStat.BREAKS), theme = theme)
                        val host = attach(config, data, width, height)
                        ui {
                            if (style == WidgetStyle.APPS && width >= 160 && height >= 140)
                                expect(texts(host).any { it.id == R.id.w_label && it.text.toString() == "Instagram" })
                            if (style == WidgetStyle.APPS && width >= 160 && height >= 140) {
                                val icon = host.findViewById<android.widget.ImageView>(R.id.w_app_icon)
                                expect(icon != null && icon.drawable != null && icon.width > 0 && icon.height > 0)
                            }
                            check(texts(host).any { it.id == R.id.w_value && it.text.toString() == "7" } ||
                                (style == WidgetStyle.SCREEN && host.findViewById<View>(R.id.w_screen_value) != null) ||
                                (style == WidgetStyle.RHYTHM && width >= 160 && height >= 140 && host.findViewById<View>(R.id.w_chart) != null)) {
                                "Missing value/chart; synthetic text: " + texts(host).joinToString { it.text.toString() }
                            }; checks++
                            expect(texts(host).filter { it.id == R.id.w_value && it.visibility == View.VISIBLE }.all { it.height > 0 && (it.layout?.lineCount ?: 0) <= 1 })
                        }
                    } catch (failure: Throwable) { throw AssertionError("${style.key}/$theme/${width}x$height: ${failure.message}", failure) }
                }
                val review = Bitmap.createBitmap(720, WidgetStyle.offered.size * 300, Bitmap.Config.ARGB_8888)
                val reviewCanvas = Canvas(review)
                for ((index, style) in WidgetStyle.offered.withIndex()) for (theme in listOf("light", "dark")) {
                    val host = attach(WidgetConfiguration.defaults(style).copy(theme = theme), WidgetData.example(), 360, 300)
                    ui {
                        reviewCanvas.save()
                        reviewCanvas.translate(if (theme == "light") 0f else 360f, index * 300f)
                        val density = activity.resources.displayMetrics.density
                        reviewCanvas.scale(1f / density, 1f / density)
                        host.draw(reviewCanvas)
                        reviewCanvas.restore()
                    }
                }
                java.io.File(target.cacheDir, "widget-review.png").outputStream().use { review.compress(Bitmap.CompressFormat.PNG, 100, it) }
                review.recycle()
                for (opacity in listOf(0, 50, 100)) {
                    val host = attach(WidgetConfiguration(opacity = opacity, numberSize = "large", corners = "square", timestamp = false), data, 360, 300)
                    ui {
                        expect(host.findViewById<android.widget.ImageView>(R.id.widget_background).imageAlpha == opacity * 255 / 100)
                        expect(host.findViewById<View>(R.id.widget_detail).visibility == View.GONE)
                        expect(texts(host).filter { it.id == R.id.w_value }.all { it.alpha == 1f })
                    }
                }
                for ((total, part) in listOf(1000L to 250L, 0L to 0L, 1000L to 2000L, null to 0L)) {
                    val host = attach(WidgetConfiguration.defaults(WidgetStyle.SCREEN), data.copy(screenMs = total, reelsAppsMs = part), 300, 230)
                    ui {
                        val bar = host.findViewById<android.widget.ProgressBar>(R.id.w_screen_bar)
                        expect(bar.visibility == if (total == 1000L && part == 250L) View.VISIBLE else View.GONE)
                        if (bar.visibility == View.VISIBLE) expect(bar.progress == 250)
                    }
                }
                val parcel = Parcel.obtain()
                try { WidgetRenderer.responsive(activity, WidgetConfiguration(style = WidgetStyle.RHYTHM), data, 300, 230).writeToParcel(parcel, 0); expect(parcel.dataSize() < 1_000_000) }
                finally { parcel.recycle() }
            } finally { instrumentation.runOnMainSync { activity.finish() } }
            checks += WidgetConfigurationVerification.run(instrumentation)
            return checks
        } finally { target.deleteSharedPreferences(prefName) }
    }
}
