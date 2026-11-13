package com.attentionstats.widget

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Switch
import com.attentionstats.appearance.AppearanceOwner
import com.attentionstats.appearance.NativePage
import com.attentionstats.reels.ReelsCoordinator

/** Reconfiguration is a draft with a real RemoteViews preview; Save is the only preference write. */
class WidgetConfigureActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var draft = WidgetConfiguration()
    private var section = "main"
    private var previewSize = 1
    private lateinit var page: NativePage
    private var preview: FrameLayout? = null
    private val changed: () -> Unit = { runOnUiThread { if (!isDestroyed && section == "main") updatePreview() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppearanceOwner.prepare(this); super.onCreate(savedInstanceState); setResult(RESULT_CANCELED)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
        if (widgetId <= 0 || info?.provider != ComponentName(this, TodayWidget::class.java)) { finish(); return }
        draft = savedInstanceState?.getString("draft")?.let { runCatching { WidgetConfiguration.decode(it) }.getOrNull() } ?: WidgetConfiguration.load(this, widgetId)
        section = savedInstanceState?.getString("section")?.takeIf { it in setOf("main", "style", "stats", "order", "appearance") } ?: "main"
        previewSize = (savedInstanceState?.getInt("previewSize") ?: 1).coerceIn(0, 2)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (section != "main") navigate("main") else finish() }
        })
        show(); ReelsCoordinator.get(this).refreshWidget()
    }
    override fun onResume() { super.onResume(); TodayWidget.addListener(changed); if (::page.isInitialized) { page.refresh(); updatePreview() } }
    override fun onPause() { TodayWidget.removeListener(changed); super.onPause() }
    override fun onSaveInstanceState(out: Bundle) { out.putString("draft", draft.encode()); out.putString("section", section); out.putInt("previewSize", previewSize); super.onSaveInstanceState(out) }
    private fun navigate(next: String) { section = next; show() }
    private fun show() {
        preview = null; page = NativePage(this)
        page.text(if (section == "main") "Your Widget" else when (section) { "style" -> "Widget Style"; "stats" -> "Choose Stats"; "order" -> "Arrange Stats"; else -> "Widget Appearance" }, 32f)
        if (section != "main") page.button("Back to Widget") { navigate("main") }
        when (section) {
            "style" -> WidgetStyle.entries.forEach { style -> page.choice(style.title, style.detail, draft.style == style) { draft = draft.copy(style = style); navigate("main") } }
            "stats" -> {
                page.text("Select one to nine. Your first stat is featured when space is small.")
                WidgetStat.entries.forEach { stat ->
                    var resetting = false
                    val toggle = Switch(this).apply {
                        text = stat.title; isChecked = stat in draft.stats
                        setOnCheckedChangeListener { button, checked ->
                            if (resetting) return@setOnCheckedChangeListener
                            if (checked && draft.stats.size >= 9 || !checked && draft.stats.size == 1) {
                                resetting = true; button.isChecked = !checked; resetting = false
                                AlertDialog.Builder(this@WidgetConfigureActivity).setMessage("Choose between one and nine stats.").setPositiveButton("Okay") { _, _ -> show() }.show()
                            } else draft = draft.copy(stats = if (checked) draft.stats + stat else draft.stats - stat)
                        }
                    }
                    page.toggle(toggle); page.text(stat.description, 13f)
                }
                page.button("Done") { navigate("main") }
            }
            "order" -> {
                page.text("The first stat stays visible at the smallest size.")
                draft.stats.forEachIndexed { index, stat ->
                    page.text("${index + 1}. ${stat.title}", 21f)
                    if (index > 0) page.button("Move ${stat.title} Up") { move(index, index - 1) }
                    if (index < draft.stats.lastIndex) page.button("Move ${stat.title} Down") { move(index, index + 1) }
                }
            }
            "appearance" -> {
                page.text("Theme", 21f)
                listOf("app" to "Match App", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                    page.choice(label, if (key == "app") "Follow the app appearance" else "Keep this widget ${label.lowercase()}", draft.theme == key) { draft = draft.copy(theme = key); show() }
                }
                page.text("Accent", 21f)
                listOf("app", "graphite", "blue", "teal", "violet").forEach { key -> page.choice(if (key == "app") "Match App" else key.replaceFirstChar { it.uppercase() }, "Widget accent color", draft.accent == key) { draft = draft.copy(accent = key); show() } }
                val names = Switch(this).apply { text = "Show App Names"; isChecked = draft.names; setOnCheckedChangeListener { _, value -> draft = draft.copy(names = value) } }
                page.toggle(names); page.text("App names appear on your home screen only when you choose this.", 13f)
            }
            else -> {
                page.text("Preview", 21f)
                val frame = FrameLayout(this); preview = frame
                page.custom(frame, previewDimensions().second); updatePreview()
                page.button("Preview Size · ${listOf("Small", "Medium", "Large")[previewSize]}") { previewSize = (previewSize + 1) % 3; show() }
                page.text("A dash means unavailable. Resizing reveals more selected stats.", 13f)
                page.button("Style · ${draft.style.title}") { navigate("style") }
                page.button("Stats · ${draft.stats.size} Selected") { navigate("stats") }
                if (draft.stats.size > 1) page.button("Arrange Stats") { navigate("order") }
                page.button("Appearance · ${if (draft.theme == "app") "Match App" else draft.theme}") { navigate("appearance") }
                page.button("Save Widget", filled = true) { save() }
                page.button("Cancel") { finish() }
            }
        }
    }
    private fun move(from: Int, to: Int) { val next = draft.stats.toMutableList(); java.util.Collections.swap(next, from, to); draft = draft.copy(stats = next); show() }
    private fun previewDimensions(): Pair<Int, Int> {
        val available = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt() - 36
        val size = listOf(160 to 100, 280 to 180, 330 to 270)[previewSize]
        return minOf(available, size.first) to size.second
    }
    private fun updatePreview() {
        val frame = preview ?: return
        val (width, height) = previewDimensions()
        val views = WidgetRenderer.views(this, draft, TodayWidget.latest, width, height, interactive = false)
        val content = views.apply(this, frame)
        frame.removeAllViews()
        frame.addView(content, FrameLayout.LayoutParams((width * resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT, android.view.Gravity.CENTER))
    }
    private fun save() {
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
        if (info?.provider != ComponentName(this, TodayWidget::class.java) || !WidgetConfiguration.save(this, widgetId, draft)) {
            AlertDialog.Builder(this).setMessage("Could not save this widget. Your draft is kept; try again.").setPositiveButton("Okay", null).show(); return
        }
        runCatching { TodayWidget.renderOne(this, AppWidgetManager.getInstance(this), widgetId) }; ReelsCoordinator.get(this).refreshWidget()
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)); finish()
    }
}
