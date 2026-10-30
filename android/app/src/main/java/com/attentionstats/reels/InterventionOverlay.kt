package com.attentionstats.reels

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-app pause screen for mindful breaks and short-video blocks. It never closes the
 * underlying app or touches its controls; the person always has an immediate way out, and
 * the system navigation bar stays uncovered.
 *
 * Human-factors choices: a slow breathing orb invites one calm breath (about 6 per minute),
 * numbers are factual and non-judgemental, the healthy choice is the primary button, and
 * "keep watching" unlocks after a short countdown instead of being hidden.
 */
internal class InterventionOverlay(
    context: Context,
    private val mode: Mode,
    private val headline: String,
    private val detail: String,
    private val waitSeconds: Int,
    private val primaryLabel: String,
    private val secondaryLabel: String,
    private val onPrimary: () -> Unit,
    private val onSecondary: () -> Unit,
) : FrameLayout(context) {
    enum class Mode { BREAK, BLOCK }

    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private val handler = Handler(Looper.getMainLooper())
    private val orb = BreathingOrb(context)
    private val secondary: TextView
    private val prompt: TextView
    private var remaining = waitSeconds
    private var promptIndex = 0

    private val prompts = listOf(
        "Breathe in slowly… and out.",
        "Ten minutes is a short walk outside.",
        "Ten minutes is a chapter of a book.",
        "Ten minutes is a call to someone you miss.",
        "Ten minutes is a glass of water and a stretch.",
        "Your attention is yours to spend.",
        "Notice how you feel right now.",
    )

    init {
        isClickable = true // Touches stop here; they never reach the paused app.
        isFocusable = true
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            if (mode == Mode.BREAK) intArrayOf(Color.rgb(16, 42, 38), Color.rgb(12, 24, 32))
            else intArrayOf(Color.rgb(28, 22, 48), Color.rgb(12, 18, 30)))
        addView(orb, LayoutParams(dp(260), dp(260), Gravity.CENTER_HORIZONTAL or Gravity.TOP).apply { topMargin = dp(96) })

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), 0, dp(28), dp(40))
        }
        column.addView(text("ATTENTION STATS", 12f, Color.argb(150, 210, 230, 220), bold = true, spacing = 0.18f))
        column.addView(text(headline, 30f, Color.WHITE, bold = true).apply {
            accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        }, wrap(top = 14))
        column.addView(text(detail, 17f, Color.argb(220, 225, 236, 230)), wrap(top = 10))
        prompt = text(prompts.first(), 15f, Color.argb(190, 196, 222, 210)).apply { alpha = 0.9f }
        column.addView(prompt, wrap(top = 22))
        column.addView(button(primaryLabel, primary = true) { finish(); onPrimary() }, wrap(top = 32))
        secondary = button(secondaryLabel, primary = false) { if (remaining <= 0) { finish(); onSecondary() } }
        column.addView(secondary, wrap(top = 12))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "$headline. $detail"
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.removeCallbacksAndMessages(null)
        tick()
    }

    private fun wrap(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false, spacing: Float = 0f) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        letterSpacing = spacing
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setLineSpacing(0f, 1.15f)
    }

    private fun button(label: String, primary: Boolean, action: () -> Unit) = TextView(context).apply {
        text = label; textSize = 16f; gravity = Gravity.CENTER
        minHeight = dp(54)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (primary) Color.rgb(10, 32, 26) else Color.WHITE)
        background = GradientDrawable().apply {
            cornerRadius = dp(27).toFloat()
            if (primary) setColor(Color.rgb(170, 232, 200)) else { setColor(Color.argb(28, 255, 255, 255)); setStroke(dp(1), Color.argb(90, 255, 255, 255)) }
        }
        isClickable = true; isFocusable = true
        setOnClickListener { action() }
        accessibilityDelegate = object : AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.className = "android.widget.Button"
            }
        }
    }

    private fun tick() {
        if (remaining > 0) {
            secondary.text = "$secondaryLabel · $remaining"
            secondary.alpha = 0.45f
            secondary.isEnabled = false
        } else {
            secondary.text = secondaryLabel
            secondary.alpha = 1f
            secondary.isEnabled = true
        }
        if (remaining % 5 == 0 && remaining != waitSeconds) {
            promptIndex = (promptIndex + 1) % prompts.size
            prompt.animate().alpha(0f).setDuration(250).withEndAction {
                prompt.text = prompts[promptIndex]; prompt.animate().alpha(0.9f).setDuration(400).start()
            }.start()
        }
        remaining--
        handler.postDelayed({ tick() }, 1_000)
    }

    fun finish() { handler.removeCallbacksAndMessages(null); orb.stop() }

    override fun onDetachedFromWindow() { finish(); super.onDetachedFromWindow() }

    /** A soft glowing circle that grows over 5 s (breathe in) and shrinks over 5 s (breathe out). */
    private class BreathingOrb(context: Context) : View(context) {
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val core = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.argb(70, 200, 255, 230) }
        private var scale = 0.7f
        private val animator = ValueAnimator.ofFloat(0.62f, 1f).apply {
            duration = 5_000; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { scale = it.animatedValue as Float; invalidate() }
        }
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; animator.start() }
        fun stop() = animator.cancel()
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height / 2f; val max = minOf(width, height) / 2f
            val radius = max * scale
            glow.shader = RadialGradient(cx, cy, radius, intArrayOf(Color.argb(120, 120, 230, 190), Color.argb(0, 60, 140, 160)),
                null, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, radius, glow)
            core.shader = LinearGradient(cx - radius, cy - radius, cx + radius, cy + radius,
                Color.argb(230, 150, 236, 200), Color.argb(230, 120, 160, 240), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, radius * 0.48f, core)
            ring.strokeWidth = max * 0.01f + 1
            canvas.drawCircle(cx, cy, radius * 0.72f, ring)
        }
    }
}
