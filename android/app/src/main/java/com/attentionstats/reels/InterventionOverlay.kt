package com.attentionstats.reels

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
 * numbers are factual and non-judgemental, leaving is the primary button, and
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
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val compact: Boolean = false,
) : FrameLayout(context) {
    enum class Mode { BREAK, BLOCK }

    private val palette = com.attentionstats.appearance.AppearanceOwner.palette(context)
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private val handler = Handler(Looper.getMainLooper())
    private val template = run {
        val prefs = context.getSharedPreferences("break-presentations", Context.MODE_PRIVATE)
        val next = prefs.getInt("next", 0).coerceAtLeast(0)
        prefs.edit().putInt("next", (next + 1) % BreakTemplates.all.size).apply()
        BreakTemplates.all[next % BreakTemplates.all.size]
    }
    private val orb = BreathingOrb(context, template.visual)
    private val secondary: TextView
    private val prompt: TextView
    private val session = org.attentionstats.protection.InterventionSession(if (compact) 0 else waitSeconds * 1000L)
    private var finished = false
    init {
        isClickable = true // Touches stop here; they never reach the paused app.
        isFocusable = true
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            if (mode == Mode.BREAK) intArrayOf(palette.surface, palette.canvas)
            else intArrayOf(palette.surfaceAlt, palette.canvas))
        if (!compact) addView(orb, LayoutParams(dp(260), dp(260), Gravity.CENTER_HORIZONTAL or Gravity.TOP).apply { topMargin = dp(96) })
        else orb.stop()

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), if (compact) dp(20) else 0, dp(28), dp(if (compact) 20 else 40))
        }
        if (!compact) column.addView(text("ATTENTION STATS", 12f, palette.muted, bold = true, spacing = 0.18f))
        column.addView(text(headline, if (compact) 23f else 30f, palette.text, bold = true).apply {
            accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        }, wrap(top = 14))
        column.addView(text(detail, 17f, palette.muted), wrap(top = 10))
        prompt = text(template.prompt, 15f, palette.muted).apply { alpha = 0.9f }
        column.addView(prompt, wrap(top = if (compact) 12 else 22))
        val primary = button(primaryLabel, primary = true) {
            if (session.choose(elapsedRealtime(), waitRequired = false)) { finish(); onPrimary() }
        }
        secondary = button(secondaryLabel, primary = false) {
            if (session.choose(elapsedRealtime(), waitRequired = true)) { finish(); onSecondary() }
        }
        if (compact) {
            val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(primary, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
            actions.addView(secondary, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            column.addView(actions, wrap(top = 16))
        } else {
            column.addView(primary, wrap(top = 32))
            column.addView(secondary, wrap(top = 12))
        }
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, if (compact) Gravity.CENTER else Gravity.BOTTOM))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "$headline. $detail"
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.removeCallbacksAndMessages(null)
        session.start(elapsedRealtime())
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
        setTextColor(if (primary) palette.onAccent else palette.text)
        background = GradientDrawable().apply {
            cornerRadius = dp(27).toFloat()
            if (primary) setColor(palette.accentFill) else { setColor(palette.surfaceAlt); setStroke(dp(1), palette.border) }
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
        if (finished) return
        val now = elapsedRealtime()
        val remaining = session.remainingSeconds(now)
        if (remaining > 0) {
            secondary.text = "$secondaryLabel · $remaining"
            secondary.alpha = 0.45f
            secondary.isEnabled = false
        } else {
            secondary.text = secondaryLabel
            secondary.alpha = 1f
            secondary.isEnabled = true
        }
        handler.postDelayed({ tick() }, 250)
    }

    fun finish() {
        finished = true; session.finish()
        handler.removeCallbacksAndMessages(null)
        prompt.animate().cancel(); animate().cancel(); orb.stop()
    }

    override fun onDetachedFromWindow() { finish(); super.onDetachedFromWindow() }

    /** A soft glowing circle that grows over 5 s (breathe in) and shrinks over 5 s (breathe out). */
    private class BreathingOrb(context: Context, private val visual: Int) : View(context) {
        private val palette = com.attentionstats.appearance.AppearanceOwner.palette(context)
        private fun alpha(color: Int, value: Int) = (color and 0x00FFFFFF) or (value shl 24)
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val core = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = alpha(palette.accent, 70) }
        private var scale = 0.7f
        private val animator = ValueAnimator.ofFloat(0.62f, 1f).apply {
            duration = 5_000; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { scale = it.animatedValue as Float; invalidate() }
        }
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; if (visual == 0 && com.attentionstats.appearance.AppearanceOwner.motion(context)) animator.start() }
        fun stop() = animator.cancel()
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height / 2f; val max = minOf(width, height) / 2f
            val radius = max * scale
            if (visual != 0) {
                ring.strokeWidth = 3f
                if (visual == 1) {
                    canvas.drawCircle(cx, cy, max * 0.45f, ring)
                    canvas.drawCircle(cx, cy, max * 0.7f, ring)
                } else if (visual == 2) {
                    canvas.drawLine(cx - max * 0.65f, cy + max * 0.18f, cx + max * 0.65f, cy + max * 0.18f, ring)
                    canvas.drawCircle(cx, cy - max * 0.2f, max * 0.18f, ring)
                } else {
                    canvas.drawRoundRect(cx - max * 0.24f, cy - max * 0.48f, cx + max * 0.24f, cy + max * 0.48f, 18f, 18f, ring)
                    canvas.drawLine(cx - max * 0.08f, cy + max * 0.34f, cx + max * 0.08f, cy + max * 0.34f, ring)
                }
                return
            }
            glow.shader = RadialGradient(cx, cy, radius, intArrayOf(alpha(palette.accent, 90), alpha(palette.accent, 0)),
                null, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, radius, glow)
            core.shader = LinearGradient(cx - radius, cy - radius, cx + radius, cy + radius,
                alpha(palette.accent, 180), alpha(palette.accentFill, 230), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, radius * 0.48f, core)
            ring.strokeWidth = max * 0.01f + 1
            canvas.drawCircle(cx, cy, radius * 0.72f, ring)
        }
    }
}
