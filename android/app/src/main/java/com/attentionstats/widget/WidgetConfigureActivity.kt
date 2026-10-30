package com.attentionstats.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.attentionstats.reels.ReelsCoordinator

/** "What should this widget show?" Shown when a widget is added, and on long-press → reconfigure. */
class WidgetConfigureActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val current = TodayWidget.statFor(this, widgetId)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(56), dp(22), dp(22))
            setBackgroundColor(Color.rgb(13, 19, 17))
        }
        column.addView(TextView(this).apply {
            text = "What should this widget show?"; textSize = 22f; setTextColor(Color.rgb(231, 238, 233))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        column.addView(TextView(this).apply {
            text = "You can change it any time: long-press the widget and choose Reconfigure."
            textSize = 14f; setTextColor(Color.rgb(166, 179, 172)); setPadding(0, dp(6), 0, dp(18))
        })
        for (stat in WidgetStat.entries) {
            val selected = stat == current
            val option = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(14), dp(18), dp(14))
                background = GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    setColor(if (selected) Color.rgb(30, 59, 47) else Color.rgb(22, 30, 26))
                    setStroke(dp(1), if (selected) Color.rgb(143, 224, 187) else Color.rgb(38, 50, 44))
                }
                isClickable = true; isFocusable = true
                contentDescription = "${stat.title}. ${stat.description}"
                setOnClickListener { choose(stat) }
                addView(TextView(context).apply {
                    text = stat.title; textSize = 17f; setTextColor(Color.rgb(231, 238, 233))
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                })
                addView(TextView(context).apply {
                    text = stat.description; textSize = 13f; setTextColor(Color.rgb(166, 179, 172))
                })
            }
            column.addView(option, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.rgb(13, 19, 17)); addView(column) })
        window?.decorView?.let { it.setBackgroundColor(Color.rgb(13, 19, 17)) }
        title = "Widget"
        column.gravity = Gravity.TOP
    }

    private fun choose(stat: WidgetStat) {
        TodayWidget.prefs(this).edit().putString("stat_$widgetId", stat.key).apply()
        TodayWidget.render(this)
        ReelsCoordinator.get(this).refreshWidget()
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
