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
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.attentionstats.appearance.AppearanceOwner
import java.text.NumberFormat

/**
 * A warm invitation out of short videos. Touches stay inside this window; the caller
 * leaves system navigation reachable. Every primary exit is immediate, including
 * during the optional celebration. Only the secondary action waits for the session.
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
    private val stats: Stats? = null,
    /** A "Do something real" choice: leaves for Home. Defaults to the primary action. */
    private val onChoice: () -> Unit = onPrimary,
    /**
     * Covers that grant time: the secondary action becomes one "− Allow 5 min +" control (ADR-122). Minus, plus or a
     * sideways swipe on the button change the minutes in steps of 5 (5 to 15); tapping the button allows them.
     */
    private val onAllow: ((Int) -> Unit)? = null,
) : FrameLayout(context) {
    enum class Mode { BREAK, BLOCK }
    data class Stats(val sessionVideos: Long, val todayVideos: Long, val sessionMinutes: Int)

    /**
     * A deep, calm scene in the hue of the person's own accent (or custom colour), so it always matches
     * the theme they chose. Neutral accents use a quiet night blue. Lightness stays at or below 22%, so
     * white text keeps well over 4.5:1 on every gradient stop; each break turns the hue a few degrees.
     */
    private data class Scene(val top: Int, val bottom: Int, val glow: Int)
    private fun themedScene(visual: Int): Scene {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(AppearanceOwner.palette(context).accent, hsl)
        val neutral = hsl[1] < .18f
        val hue = ((if (neutral) 222f else hsl[0]) + listOf(0f, 12f, -10f, 22f)[visual.mod(4)] + 360f) % 360f
        val saturation = if (neutral) .38f else hsl[1].coerceIn(.35f, .7f)
        return Scene(ColorUtils.HSLToColor(floatArrayOf(hue, saturation * .7f, .08f)),
            ColorUtils.HSLToColor(floatArrayOf((hue + 18f) % 360f, saturation, .21f)),
            ColorUtils.HSLToColor(floatArrayOf(hue, saturation.coerceAtLeast(.5f), .76f)))
    }
    private val ink = Color.WHITE
    private val inkSoft = ColorUtils.setAlphaComponent(Color.WHITE, 214)
    private val density = resources.displayMetrics.density
    /**
     * Fit to the window like viewport units: every size below is multiplied by [scale], chosen (with [trim],
     * how many optional lines are left out) so the whole screen and its buttons fit without scrolling.
     */
    private var scale = 1f
    private var trim = 0
    private var fitted = 0L
    private fun dp(value: Int) = (value * density * scale).toInt()
    /** Unscaled, for outer margins and minimum touch targets. */
    private fun px(value: Int) = (value * density).toInt()
    private fun motion() = AppearanceOwner.motion(context)
    private val handler = Handler(Looper.getMainLooper())
    private val animators = mutableListOf<ValueAnimator>()
    private val template = run {
        val prefs = context.getSharedPreferences("break-presentations", Context.MODE_PRIVATE)
        val next = prefs.getInt("next", 0).coerceAtLeast(0)
        prefs.edit().putInt("next", (next + 1) % BreakTemplates.all.size).apply()
        BreakTemplates.all[next % BreakTemplates.all.size]
    }
    private val choices = context.getSharedPreferences("real-life-choices", Context.MODE_PRIVATE)
    private val scene = themedScene(template.visual)
    // The count sits on the glowing orb; outline variants only appear without a count.
    private val orb = BreathingOrb(context, if (stats != null) 0 else template.visual, scene.glow)
    private val column = LinearLayout(context)
    private val scroll = ScrollView(context)
    private val drift = DriftingBackground(context)
    private lateinit var secondary: Button
    private var allowMinutes = ALLOW_STEP
    private var fewer: Button? = null
    private var more: Button? = null
    private var hero: TextView? = null
    private val session = org.attentionstats.protection.InterventionSession(if (compact) 0 else waitSeconds * 1000L)
    private var finished = false
    private var celebrating = false

    init {
        isClickable = true // Consume empty space as well as controls.
        isFocusable = true
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER
        scroll.isFillViewport = true
        scroll.isClickable = true
        scroll.addView(column, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        if (mode == Mode.BREAK) {
            // A calm card floating over the paused video, with even margins on every side.
            setBackgroundColor(ColorUtils.setAlphaComponent(Color.BLACK, 150))
            val card = FrameLayout(context).apply {
                background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(scene.top) }
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                clipToOutline = true
            }
            card.addView(drift, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            card.addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                val side = px(18); marginStart = side; marginEnd = side; topMargin = px(16); bottomMargin = px(16)
            })
        } else {
            setBackgroundColor(scene.top)
            addView(drift, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
        }

        build()
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /** (Re)creates the content at the current [scale] and [trim]; the orb, scene and session carry over. */
    private fun build() {
        column.removeAllViews()
        (orb.parent as? android.view.ViewGroup)?.removeView(orb)
        hero = null
        column.setPadding(dp(20), dp(if (compact) 20 else 22), dp(20), dp(if (compact) 20 else 24))
        if (mode == Mode.BREAK) {
            // A smaller orb on shorter screens keeps both actions in view without scrolling.
            val heightDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density
            val size = if (compact) 132 else (heightDp * 0.14f).toInt().coerceIn(120, 196)
            val orbFrame = FrameLayout(context).apply { minimumHeight = dp(size) }
            orbFrame.addView(orb, LayoutParams(dp(size), dp(size), Gravity.CENTER))
            val guide = text(if (motion()) "Breathe in" else "Breathe in 4 · hold 2 · out 6", if (compact) 18f else 22f, ink, bold = true)
            guide.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            orb.onPhase = { value -> guide.animate().cancel(); guide.text = value; guide.alpha = .4f; guide.animate().alpha(1f).setDuration(400).start() }
            orbFrame.addView(guide, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            orbFrame.contentDescription = "Breathing guide: breathe in for 4 seconds, hold for 2, breathe out for 6."
            column.addView(orbFrame, wrap(0))
            if (trim < 3) column.addView(text("Follow the circle for three slow breaths", 14f, inkSoft), wrap(4))
        } else if (!compact) {
            val orbFrame = FrameLayout(context).apply { minimumHeight = dp(180) }
            orbFrame.addView(orb, LayoutParams(dp(180), dp(180), Gravity.CENTER))
            stats?.let {
                hero = text(formatCount(it.sessionVideos), 60f, ink, bold = true).apply {
                    // Announce the final value, never every frame of the animation.
                    contentDescription = formatCount(it.sessionVideos)
                }
                orbFrame.addView(hero, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            }
            column.addView(orbFrame, wrap(0))
            stats?.let {
                val minutes = if (it.sessionMinutes > 0) " · ${it.sessionMinutes} min" else ""
                column.addView(text("short videos since you unlocked$minutes", 14f, inkSoft), wrap(0))
            }
        }
        column.addView(text(headline, if (compact) 23f else 30f, ink, bold = true).apply {
            contentDescription = headline
            accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        }, wrap(14))
        column.addView(text(detail, 17f, inkSoft), wrap(10))
        if (!compact && trim < 2) stats?.let { value ->
            Perspective.distance(value.todayVideos)?.let {
                pill(it.text.replace("thumb scrolling · ", "thumb scrolling today," + System.lineSeparator()))
            }
            if (value.sessionMinutes >= 5) {
                Perspective.achievements(value.sessionMinutes * 60_000L, 1, System.currentTimeMillis() / 86_400_000L)
                    .firstOrNull()?.let { pill("In those ${value.sessionMinutes} minutes you could have ${it.text}.") }
            }
        }
        if (!compact) addChoices()
        if (!compact && trim < 1) column.addView(text(template.prompt, 15f, inkSoft), wrap(16))
        column.addView(button(primaryLabel, primary = true, action = ::choosePrimary), wrap(if (compact) 16 else 24))
        val allow = onAllow
        if (allow == null) {
            fewer = null; more = null
            secondary = button(secondaryLabel, primary = false) {
                if (session.choose(elapsedRealtime(), waitRequired = true)) { finish(); onSecondary() }
            }
            column.addView(secondary, wrap(12))
        } else column.addView(allowControl(allow), wrap(12))
    }

    /** One row: minus, the allow button that carries the minutes, plus. Never a second step. */
    @android.annotation.SuppressLint("ClickableViewAccessibility") // Taps still reach performClick; ± are the accessible path.
    private fun allowControl(allow: (Int) -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = false // The larger − and + glyphs would otherwise push the middle pill down.
        // One fixed height for all three, with round ends, so no edge is clipped by the row.
        val side = maxOf(px(48), dp(56))
        fun round(view: Button) = view.apply {
            minHeight = side; minimumHeight = side; setPadding(dp(8), 0, dp(8), 0)
            (background as? GradientDrawable)?.cornerRadius = side / 2f
        }
        fun step(delta: Int) {
            val next = (allowMinutes + delta).coerceIn(ALLOW_STEP, ALLOW_MAX)
            if (next == allowMinutes) return
            allowMinutes = next; tick(schedule = false) // The button is a polite live region: the new minutes are read out.
        }
        fewer = button("−", primary = false) { step(-ALLOW_STEP) }.apply { contentDescription = "Fewer minutes"; textSize = 22f * scale.coerceAtLeast(.85f) }
        more = button("+", primary = false) { step(ALLOW_STEP) }.apply { contentDescription = "More minutes"; textSize = 22f * scale.coerceAtLeast(.85f) }
        secondary = button(allowText(), primary = false) {
            if (session.choose(elapsedRealtime(), waitRequired = true)) { finish(); allow(allowMinutes) }
        }
        secondary.accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        // A sideways swipe on the button changes the minutes too; a tap still allows them.
        var downX = 0f; var downY = 0f
        secondary.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; false }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    if (kotlin.math.abs(dx) > px(36) && kotlin.math.abs(dx) > 1.5f * kotlin.math.abs(event.y - downY)) {
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        try { view.onTouchEvent(cancel) } finally { cancel.recycle() }
                        step(if (dx > 0) ALLOW_STEP else -ALLOW_STEP); true
                    } else false
                }
                else -> false
            }
        }
        listOf(fewer!!, secondary, more!!).forEach(::round)
        addView(fewer, LinearLayout.LayoutParams(side, side))
        addView(secondary, LinearLayout.LayoutParams(0, side, 1f).apply { marginStart = dp(8); marginEnd = dp(8) })
        addView(more, LinearLayout.LayoutParams(side, side))
    }
    private fun allowText() = "Allow $allowMinutes min"

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val key = (w.toLong() shl 32) or h.toLong()
        if (w > 0 && h > 0 && key != fitted) { fitted = key; post { fit(w, h) } }
    }

    /**
     * Leaves out optional lines first (the closing prompt, the comparison pills, the small helper lines), then
     * shrinks every size in steps until the content fits the window. Scrolling stays only as a last resort.
     */
    private fun fit(w: Int, h: Int) {
        if (finished || celebrating || !isAttachedToWindow) return
        val width = if (mode == Mode.BREAK) w - px(36) else w
        val height = if (mode == Mode.BREAK) h - px(32) else h
        // trim 4 keeps one row of real-life choices, trim 5 leaves them out; the two actions always stay.
        val steps = listOf(0 to 1f, 1 to 1f, 2 to 1f, 3 to 1f, 3 to .92f, 3 to .85f, 4 to .85f, 4 to .78f, 4 to .72f,
            5 to .72f, 5 to .66f, 5 to .6f)
        for ((t, s) in steps) {
            trim = t; scale = s
            build(); tick(schedule = false)
            column.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (column.measuredHeight <= height) break
        }
        scroll.scrollTo(0, 0)
    }

    private fun formatCount(value: Long) = NumberFormat.getIntegerInstance().format(value)

    private fun rounded(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(24).toFloat()
        setColor(color)
    }

    private fun pill(value: String) {
        column.addView(text(value, 15f, ink).apply {
            background = rounded(ColorUtils.setAlphaComponent(Color.WHITE, 40))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }, wrap(14))
    }

    private fun addChoices() {
        if (trim >= 5) return
        column.addView(text("Do something real", 18f, ink, bold = true), wrap(16))
        val count = choices.getLong("count", 0)
        if (count >= 1 && trim < 3) column.addView(text("You've chosen real life ${formatCount(count)} times", 13f, inkSoft), wrap(6))
        val labels = listOf("Walk", "Read", "Drink water", "Stretch", "Call someone", "Step outside")
        val names = listOf("walk", "read", "drink water", "stretch", "call someone", "step outside")
        val icons = listOf(com.attentionstats.R.drawable.ic_real_walk, com.attentionstats.R.drawable.ic_real_read,
            com.attentionstats.R.drawable.ic_real_water, com.attentionstats.R.drawable.ic_real_stretch,
            com.attentionstats.R.drawable.ic_real_call, com.attentionstats.R.drawable.ic_real_outside)
        val responses = listOf("Ten minutes outside clears the head.", "One chapter. Your mind will thank you.",
            "Water first. Then the next good thing.", "Loosen the shoulders, straighten the back.",
            "A real voice beats a hundred videos.", "Fresh air and daylight reset your focus.")
        for (rowIndex in 0..(if (trim >= 4) 0 else 1)) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (col in 0..2) {
                val index = rowIndex * 3 + col
                row.addView(button(labels[index], primary = false) { celebrate(responses[index]) }.apply {
                    textSize = 14f * scale
                    contentDescription = "Choose ${names[index]}, leaves short videos"
                    // A Lucide line icon above the label, tinted like the text.
                    val icon = context.getDrawable(icons[index])?.mutate()?.apply {
                        setTint(ink); setBounds(0, 0, dp(22), dp(22))
                    }
                    setCompoundDrawablesRelative(null, icon, null, null)
                    compoundDrawablePadding = dp(6)
                    setPadding(dp(6), dp(14), dp(6), dp(12))
                    (background as? GradientDrawable)?.cornerRadius = dp(18).toFloat()
                }, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                    if (col > 0) marginStart = dp(6)
                })
            }
            column.addView(row, wrap(8))
        }
    }

    private fun choosePrimary() {
        if (finished) return
        if (celebrating || session.choose(elapsedRealtime(), waitRequired = false)) {
            finish()
            if (celebrating) onChoice() else onPrimary()
        }
    }

    private fun celebrate(response: String) {
        if (finished || celebrating || !session.choose(elapsedRealtime(), waitRequired = false)) return
        celebrating = true
        val count = choices.getLong("count", 0).coerceAtLeast(0)
        choices.edit().putLong("count", if (count == Long.MAX_VALUE) count else count + 1).apply()
        if (!motion()) { choosePrimary(); return }
        // The choice has already passed the immediate session gate. Keep an immediate exit
        // visible throughout the celebration rather than making the animation a new gate.
        cancelContentAnimations()
        column.removeAllViews()
        column.addView(text("Go for it.", 36f, ink, bold = true).apply {
            accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
            contentDescription = "Go for it. $response"
        }, wrap(0))
        column.addView(text(response, 20f, ink), wrap(14))
        column.addView(button(primaryLabel, primary = true, action = ::choosePrimary), wrap(28))
        scroll.scrollTo(0, 0)
        animateValue(240) {
            column.alpha = it
            column.scaleX = .94f + .06f * it
            column.scaleY = .94f + .06f * it
        }
        handler.postDelayed({ if (!finished) choosePrimary() }, 1_200)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (finished) return
        session.start(elapsedRealtime())
        scroll.post { scroll.scrollTo(0, 0) } // Start at the breathing guide, not a focused button.
        tick()
        orb.start()
        drift.start()
        animateValue(300) {
            column.alpha = it
            column.translationY = dp(18) * (1f - it)
        }
        val value = stats
        if (hero != null && value != null) animateValue(900) {
            hero?.text = formatCount(if (it >= 1f) value.sessionVideos else (value.sessionVideos * it.toDouble()).toLong())
        }
    }

    /** All finite animations reach their final state immediately when motion is disabled. */
    private fun animateValue(durationMs: Long, frame: (Float) -> Unit) {
        if (!motion()) { frame(1f); return }
        frame(0f)
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            addUpdateListener {
                if (!motion()) { cancel(); frame(1f) }
                else frame(it.animatedValue as Float)
            }
        }
        animators.add(animator)
        animator.start()
    }

    private fun cancelContentAnimations() {
        animators.forEach { it.cancel() }
        animators.clear()
        column.animate().cancel()
        column.alpha = 1f
        column.translationY = 0f
        column.scaleX = 1f
        column.scaleY = 1f
    }

    private fun wrap(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    /**
     * Centred copy: balanced line breaks (no single orphaned word) and, for short multi-sentence text,
     * one sentence per line so the block reads evenly.
     */
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = if (value.length <= 160) value.replace(Regex("([.?!]) (?=[A-Z0-9])"), "$1\n") else value
        textSize = size * scale; setTextColor(color); gravity = Gravity.CENTER
        breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED
        hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setLineSpacing(0f, 1.15f)
    }

    private fun button(label: String, primary: Boolean, action: () -> Unit) = Button(context).apply {
        text = label; textSize = 16f * scale.coerceAtLeast(.85f); gravity = Gravity.CENTER
        breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED
        isAllCaps = false
        // Touch targets never shrink below 44 dp, however short the screen.
        minHeight = maxOf(px(44), dp(54))
        minimumHeight = maxOf(px(44), dp(54))
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(10), dp(12), dp(10), dp(12))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (primary) scene.top else ink)
        backgroundTintList = null
        background = rounded(if (primary) Color.WHITE else ColorUtils.setAlphaComponent(Color.WHITE, 34)).apply {
            if (!primary) setStroke(dp(1), ColorUtils.setAlphaComponent(Color.WHITE, 96))
        }
        setOnClickListener { action() }
    }

    private fun tick(schedule: Boolean = true) {
        if (finished) return
        val remaining = session.remainingSeconds(elapsedRealtime())
        val label = if (onAllow != null) allowText() else secondaryLabel
        secondary.text = if (remaining > 0) "$label · $remaining" else label
        secondary.contentDescription = if (onAllow != null) "Allow $allowMinutes more minutes" else null
        fewer?.apply { isEnabled = allowMinutes > ALLOW_STEP; alpha = if (isEnabled) 1f else .4f }
        more?.apply { isEnabled = allowMinutes < ALLOW_MAX; alpha = if (isEnabled) 1f else .4f }
        secondary.alpha = if (remaining > 0) .45f else 1f
        secondary.isEnabled = remaining <= 0
        // Also stop a caller's entrance animator if reduced motion changes while visible.
        if (!motion()) {
            animate().cancel()
            alpha = 1f
            if (celebrating) { choosePrimary(); return }
        }
        if (schedule) handler.postDelayed({ tick() }, 250)
    }

    fun finish() {
        finished = true
        session.finish()
        handler.removeCallbacksAndMessages(null)
        cancelContentAnimations()
        animate().cancel()
        orb.stop()
        drift.stop()
    }

    override fun onDetachedFromWindow() { finish(); super.onDetachedFromWindow() }

    /** A slow diagonal wash through the scene's colours with a drifting light. */
    private inner class DriftingBackground(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        private var progress = 0f
        private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 10_000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                if (!motion()) { stop(); progress = 0f }
                else progress = it.animatedValue as Float
                invalidate()
            }
        }
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        // Never size a wrapping card; fill only the space its content (or the window) defines.
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
            setMeasuredDimension(resolveSize(0, widthMeasureSpec), resolveSize(0, heightMeasureSpec))
        fun start() { if (motion()) animator.start() }
        fun stop() { animator.cancel() }
        override fun onDraw(canvas: Canvas) {
            if (width == 0 || height == 0) return
            val w = width.toFloat(); val h = height.toFloat()
            val shift = w * progress * .35f
            val middle = ColorUtils.blendARGB(scene.top, scene.bottom, .35f + .3f * progress)
            paint.shader = LinearGradient(-shift, 0f, w + shift, h,
                intArrayOf(scene.top, middle, scene.bottom), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            // A soft light drifting across the scene.
            val cx = w * (.2f + .6f * progress); val cy = h * (.28f + .1f * progress)
            glow.shader = RadialGradient(cx, cy, w * .75f, intArrayOf(ColorUtils.setAlphaComponent(scene.glow, 70),
                ColorUtils.setAlphaComponent(scene.glow, 0)), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, glow)
        }
    }

    /** A soft glowing circle that grows over 5 s (breathe in) and shrinks over 5 s (breathe out). */
    private class BreathingOrb(context: Context, private val visual: Int, private val light: Int) : View(context) {
        private fun alpha(color: Int, value: Int) = (color and 0x00FFFFFF) or (value shl 24)
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val core = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = alpha(Color.WHITE, 110) }
        private var scale = 0.7f
        /** Guided breathing: in 4 s, hold 2 s, out 6 s (five calm breaths a minute). */
        var onPhase: ((String) -> Unit)? = null
        private var phase = ""
        private val animator = ValueAnimator.ofFloat(0f, 12f).apply {
            duration = 12_000; repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                if (!com.attentionstats.appearance.AppearanceOwner.motion(context)) {
                    stop(); scale = .85f
                } else {
                    val t = it.animatedValue as Float
                    fun ease(x: Float) = (1 - kotlin.math.cos(Math.PI * x).toFloat()) / 2
                    scale = when {
                        t < 4f -> .62f + .38f * ease(t / 4f)
                        t < 6f -> 1f
                        else -> 1f - .38f * ease((t - 6f) / 6f)
                    }
                    val next = if (t < 4f) "Breathe in" else if (t < 6f) "Hold" else "Breathe out"
                    if (next != phase) { phase = next; onPhase?.invoke(next) }
                }
                invalidate()
            }
        }
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        fun start() { if (visual == 0 && com.attentionstats.appearance.AppearanceOwner.motion(context)) animator.start() }
        fun stop() { animator.cancel() }
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
            glow.shader = RadialGradient(cx, cy, radius, intArrayOf(alpha(light, 120), alpha(light, 0)),
                null, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, radius, glow)
            core.shader = LinearGradient(cx - radius, cy - radius, cx + radius, cy + radius,
                alpha(Color.WHITE, 150), alpha(light, 200), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, radius * 0.48f, core)
            ring.strokeWidth = max * 0.01f + 1
            canvas.drawCircle(cx, cy, radius * 0.72f, ring)
        }
    }

    private companion object {
        const val ALLOW_STEP = 5
        const val ALLOW_MAX = 15
    }
}
