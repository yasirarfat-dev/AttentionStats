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
import android.widget.SeekBar
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
    private var livePreview = false
    private var moreAppearance = false
    private var moreLayouts = false
    private lateinit var page: NativePage
    private var preview: FrameLayout? = null
    private val changed: () -> Unit = { runOnUiThread { if (!isDestroyed && section == "main") updatePreview() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppearanceOwner.prepare(this); super.onCreate(savedInstanceState); setResult(RESULT_CANCELED)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
        if (widgetId <= 0 || !TodayWidget.owns(this, info?.provider)) { finish(); return }
        draft = savedInstanceState?.getString("draft")?.let { runCatching { WidgetConfiguration.decode(it) }.getOrNull() } ?: WidgetConfiguration.load(this, widgetId)
        section = savedInstanceState?.getString("section")?.takeIf { it in setOf("main", "style", "stats", "order", "appearance", "theme", "accent", "corners", "numbers") } ?: "main"
        moreAppearance = savedInstanceState?.getBoolean("moreAppearance") ?: false
        moreLayouts = savedInstanceState?.getBoolean("moreLayouts") ?: (draft.style !in WidgetStyle.offered)
        livePreview = savedInstanceState?.getBoolean("livePreview") ?: false
        previewSize = (savedInstanceState?.getInt("previewSize") ?: 1).coerceIn(0, 2)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (section != "main") navigate(parentSection()) else finish() }
        })
        show(); ReelsCoordinator.get(this).refreshWidget()
    }
    override fun onResume() { super.onResume(); TodayWidget.addListener(changed); if (::page.isInitialized) { page.refresh(); updatePreview() } }
    override fun onPause() { TodayWidget.removeListener(changed); super.onPause() }
    override fun onSaveInstanceState(out: Bundle) { out.putBoolean("moreAppearance", moreAppearance); out.putBoolean("moreLayouts", moreLayouts); out.putBoolean("livePreview", livePreview); out.putString("draft", draft.encode()); out.putString("section", section); out.putInt("previewSize", previewSize); super.onSaveInstanceState(out) }
    private fun parentSection() = if (section in setOf("theme", "accent", "corners", "numbers")) "appearance" else "main"
    private fun navigate(next: String) { section = next; show() }
    private fun show() {
        preview = null; page = NativePage(this)
        page.text(if (section == "main") "Your Widget" else when (section) { "style" -> "Widget Style"; "stats" -> "Choose Stats"; "order" -> "Arrange Stats"; "theme" -> "Widget Theme"; "accent" -> "Accent Color"; "corners" -> "Corner Shape"; "numbers" -> "Stat Number Size"; else -> "Widget Appearance" }, 32f)
        if (section != "main") page.button(if (parentSection() == "appearance") "Back to Appearance" else "Back to Widget") { navigate(parentSection()) }
        when (section) {
            "style" -> {
                val choices = WidgetStyle.offered + if (moreLayouts) listOf(WidgetStyle.TILES, WidgetStyle.LIST) else emptyList()
                choices.forEach { style -> page.choice(style.title, style.detail, draft.style == style) { draft = draft.copy(style = style); navigate("main") } }
                page.button(if (moreLayouts) "Fewer Layouts" else "More Layouts") { moreLayouts = !moreLayouts; show() }
            }
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
                page.text(if (livePreview) "Your Stats Preview" else "Example Preview", 21f)
                val frame = FrameLayout(this); preview = frame
                page.custom(frame, previewDimensions().second); updatePreview()
                page.text("Background Opacity", 21f)
                val opacityLabel = page.text("${draft.opacity}% · Text stays opaque")
                val slider = SeekBar(this).apply {
                    max = 100; progress = draft.opacity; contentDescription = "Widget Background Opacity"
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(view: SeekBar?, value: Int, user: Boolean) { if (user) { draft = draft.copy(opacity = value); opacityLabel.text = "$value% · Text stays opaque"; updatePreview() } }
                        override fun onStartTrackingTouch(view: SeekBar?) {}
                        override fun onStopTrackingTouch(view: SeekBar?) {}
                    })
                }
                page.custom(slider, 56)
                page.text("0% removes the panel and tile backgrounds. Choose a theme readable on your wallpaper.", 13f)
                page.button("Theme · ${if (draft.theme == "app") "Match App" else draft.theme}") { navigate("theme") }
                page.button("Accent · ${if (draft.accent == "app") "Match App" else draft.accent}") { navigate("accent") }
                page.button(if (moreAppearance) "Fewer Options" else "More Options") { moreAppearance = !moreAppearance; show() }
                if (moreAppearance) {
                    page.button("Corner Shape · ${draft.corners}") { navigate("corners") }
                    page.button("Number Size · ${draft.numberSize}") { navigate("numbers") }
                    page.toggle(Switch(this).apply { text = "Show Update Time"; isChecked = draft.timestamp; setOnCheckedChangeListener { _, value -> draft = draft.copy(timestamp = value) } })
                    val names = Switch(this).apply { text = "Show App Names"; isChecked = draft.names; setOnCheckedChangeListener { _, value -> draft = draft.copy(names = value) } }
                    page.toggle(names); page.text("App names identify each source. Turn this off to hide them on your home screen.", 13f)
                }
            }
            "theme" -> listOf("app" to "Match App", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                page.choice(label, if (key == "app") "Follow the app appearance" else "Keep this widget ${label.lowercase()}", draft.theme == key) { draft = draft.copy(theme = key); navigate("appearance") }
            }
            "accent" -> listOf("app", "graphite", "blue", "teal", "violet").forEach { key ->
                page.choice(if (key == "app") "Match App" else key.replaceFirstChar { it.uppercase() }, "Widget accent color", draft.accent == key) { draft = draft.copy(accent = key); navigate("appearance") }
            }
            "corners" -> listOf("rounded" to "Rounded", "soft" to "Soft Corners", "square" to "Square").forEach { (key, label) ->
                page.choice(label, "Panel shape", draft.corners == key) { draft = draft.copy(corners = key); navigate("appearance") }
            }
            "numbers" -> listOf("small" to "Small Numbers", "normal" to "Standard Numbers", "large" to "Large Numbers").forEach { (key, label) ->
                page.choice(label, "Numbers fit the available space", draft.numberSize == key) { draft = draft.copy(numberSize = key); navigate("appearance") }
            }
            else -> {
                page.text(if (livePreview) "Your Stats Preview" else "Example Preview", 21f)
                val frame = FrameLayout(this); preview = frame
                page.custom(frame, previewDimensions().second); updatePreview()
                page.button(if (livePreview) "Show Example Stats" else "Preview My Stats") { livePreview = !livePreview; show() }
                page.button("Preview Size · ${listOf("Small", "Medium", "Large")[previewSize]}") { previewSize = (previewSize + 1) % 3; show() }
                page.text(if (livePreview) "A dash means unavailable. Resizing reveals more selected stats." else "Illustrative values only. Saved widgets show your recorded stats.", 13f)
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
        val views = WidgetRenderer.views(this, draft, if (livePreview) TodayWidget.latest else WidgetData.example(), width, height, interactive = false)
        if (!livePreview) views.setTextViewText(com.attentionstats.R.id.widget_detail, "Example Data · Attention Stats")
        val content = views.apply(this, frame)
        frame.removeAllViews()
        frame.addView(content, FrameLayout.LayoutParams((width * resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT, android.view.Gravity.CENTER))
    }
    private fun save() {
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
        if (!TodayWidget.owns(this, info?.provider) || !WidgetConfiguration.save(this, widgetId, draft)) {
            AlertDialog.Builder(this).setMessage("Could not save this widget. Your draft is kept; try again.").setPositiveButton("Okay", null).show(); return
        }
        runCatching { TodayWidget.renderOne(this, AppWidgetManager.getInstance(this), widgetId) }; ReelsCoordinator.get(this).refreshWidget()
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)); finish()
    }
}
