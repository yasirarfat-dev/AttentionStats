package com.attentionstats.appearance

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Native-owned transport/widget flows use the same generated semantic roles and geometry as the app. */
@Suppress("DEPRECATION")
internal class NativePage(private val activity: Activity) {
    private val density = activity.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private val bindings = mutableListOf<(NativePalette) -> Unit>()
    private val content = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(48), dp(18), dp(32))
    }
    private val root = ScrollView(activity).apply { addView(content) }
    private var group: LinearLayout? = null

    init {
        root.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                content.setPadding(dp(18) + bars.left, dp(16) + bars.top, dp(18) + bars.right, dp(32) + bars.bottom)
            } else content.setPadding(dp(18), dp(16) + insets.systemWindowInsetTop, dp(18), dp(32) + insets.systemWindowInsetBottom)
            insets
        }
        activity.setContentView(root)
        refresh()
    }
    private fun shape(fill: Int, border: Int, radius: Int = 24) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat(); setColor(fill); setStroke(dp(1), border)
    }
    private fun add(view: View, parent: LinearLayout = group ?: content, gap: Int = 12) {
        parent.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(gap) })
    }
    private fun bind(action: (NativePalette) -> Unit) { bindings.add(action); action(AppearanceOwner.palette(activity)) }
    private fun ensureGroup(): LinearLayout {
        group?.let { return it }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(8))
            add(this, content, 14)
            bind { background = shape(it.surface, it.border) }
            group = this
        }
    }
    private fun titleCase(value: String) = value.split(" ").joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
    fun text(value: String, size: Float = 15f): TextView {
        if (size >= 24) {
            group = null
            return TextView(activity).apply {
                text = titleCase(value); textSize = 32f; typeface = Typeface.create("sans-serif", Typeface.BOLD)
                if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
                add(this, content, 22); bind { setTextColor(it.text) }
            }
        }
        if (size >= 20) {
            group = null
            val heading = TextView(activity).apply {
                text = titleCase(value); textSize = 13f; letterSpacing = .06f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setPadding(dp(4), dp(10), 0, dp(2))
                if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
            }
            add(heading, content, 10); bind { heading.setTextColor(it.muted) }
            return heading
        }
        return TextView(activity).apply {
            text = value; textSize = size; setLineSpacing(dp(3).toFloat(), 1.05f)
            add(this, ensureGroup()); bind { setTextColor(it.muted) }
        }
    }
    fun button(label: String, filled: Boolean = false, action: () -> Unit) {
        val button = TextView(activity).apply {
            text = titleCase(label); textSize = 16f; gravity = Gravity.CENTER; minHeight = dp(52)
            setPadding(dp(18), dp(12), dp(18), dp(12)); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isClickable = true; isFocusable = true; setOnClickListener { action() }
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info); info.className = "android.widget.Button"
                }
            }
        }
        add(button, ensureGroup())
        bind { button.background = shape(if (filled) it.accentFill else it.accentSoft, it.border, 26); button.setTextColor(if (filled) it.onAccent else it.accent) }
    }
    fun toggle(view: Switch) {
        view.text = titleCase(view.text.toString()); view.textSize = 16f; view.minHeight = dp(56); view.setPadding(0, dp(8), 0, dp(8))
        add(view, ensureGroup())
        bind {
            view.setTextColor(it.text)
            view.trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(it.accentFill, it.track))
            view.thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(it.onAccent, it.muted))
        }
    }
    fun choice(title: String, detail: String, selected: Boolean, action: () -> Unit) {
        group = null
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(18))
            isClickable = true; isFocusable = true; isSelected = selected
            contentDescription = "$title. $detail${if (selected) ". Selected" else ""}"
            setOnClickListener { action() }
        }
        val heading = TextView(activity).apply { text = if (selected) "$title · Selected" else title; textSize = 17f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
        val body = TextView(activity).apply { text = detail; textSize = 13f; setPadding(0, dp(6), 0, 0); setLineSpacing(dp(2).toFloat(), 1f) }
        card.addView(heading); card.addView(body); add(card, content, 10)
        bind { card.background = shape(if (selected) it.accentSoft else it.surface, if (selected) it.accent else it.border); heading.setTextColor(it.text); body.setTextColor(it.muted) }
    }
    fun custom(view: View, heightDp: Int) {
        group = null
        content.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)).apply { bottomMargin = dp(14) })
    }
    fun refresh() {
        val palette = AppearanceOwner.palette(activity)
        root.setBackgroundColor(palette.canvas)
        activity.window.decorView.setBackgroundColor(palette.canvas)
        if (Build.VERSION.SDK_INT >= 30) {
            val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            activity.window.insetsController?.setSystemBarsAppearance(if (AppearanceOwner.dark(activity)) 0 else flags, flags)
        }
        bindings.forEach { it(palette) }
    }
}
