package com.attentionstats.widget

import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.attentionstats.appearance.AppearanceOwner
import com.attentionstats.appearance.NativePage

/** Purpose-specific, illustrative previews. Adding still requires the launcher's confirmation. */
class WidgetGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppearanceOwner.prepare(this); super.onCreate(savedInstanceState)
        show()
    }
    private fun show() {
        val page = NativePage(this)
        page.text("Home Screen Widgets", 32f)
        page.section("Example Gallery", "Choose a widget, then confirm on your launcher. Examples use illustrative values, not your history. Long-press a placed widget to change its style, stats, order and appearance.")
        page.button("Back to Settings") { finish() }
        val width = minOf(330, (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt() - 76).coerceAtLeast(110)
        TodayWidget.providers.forEach { provider ->
            val style = TodayWidget.styleFor(provider.name)
            page.text(style.title, 21f)
            val frame = FrameLayout(this)
            val views = WidgetRenderer.views(this, WidgetConfiguration.defaults(style), WidgetData.example(), width, 230, interactive = false)
            views.setTextViewText(com.attentionstats.R.id.widget_detail, "Example Data · Attention Stats")
            frame.addView(views.apply(this, frame), FrameLayout.LayoutParams((width * resources.displayMetrics.density).toInt(), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
            page.custom(frame, 230)
            page.button("Add ${style.title}", filled = true) {
                val manager = AppWidgetManager.getInstance(this)
                if (Build.VERSION.SDK_INT < 26 || !manager.isRequestPinAppWidgetSupported || !manager.requestPinAppWidget(ComponentName(this, provider), null, null)) {
                    AlertDialog.Builder(this).setMessage("Long-press an empty spot on your home screen, choose Widgets, then Attention Stats.").setPositiveButton("Okay", null).show()
                }
            }
        }
    }
}
