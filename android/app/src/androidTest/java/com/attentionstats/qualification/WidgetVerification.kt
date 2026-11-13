package com.attentionstats.qualification

import android.app.Instrumentation
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
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
                for (style in WidgetStyle.entries) for (theme in listOf("light", "dark")) for ((width, height) in listOf(110 to 80, 260 to 150, 360 to 300)) {
                    instrumentation.runOnMainSync {
                        val parent = FrameLayout(activity)
                        val host = AppWidgetHostView(activity); host.setAppWidget(0, info)
                        activity.setContentView(parent)
                        val density = activity.resources.displayMetrics.density
                        val w = (width * density).toInt(); val h = (height * density).toInt()
                        parent.addView(host, FrameLayout.LayoutParams(w, h))
                        host.updateAppWidget(WidgetRenderer.views(activity, WidgetConfiguration(style = style, theme = theme), data, width, height))
                        host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)); host.layout(0, 0, w, h)
                        expect(host.isAttachedToWindow)
                        expect(texts(host).any { it.id == R.id.w_value && it.text.toString() == "7" })
                        expect(texts(host).filter { it.id == R.id.w_value && it.visibility == View.VISIBLE }.all { it.height > 0 && (it.layout?.lineCount ?: 0) <= 1 })
                    }
                    instrumentation.waitForIdleSync()
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
