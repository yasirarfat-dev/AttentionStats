package com.attentionstats.reels

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import org.attentionstats.reels.PageCategory

/** One bounded overlay: icon/count glance, tap for committed visit/day category projections. */
internal class CounterHudView(context: Context, onOpen: () -> Unit, onPause: () -> Unit,
    onHide: () -> Unit) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private val iconColor = Color.rgb(216, 226, 244)
    private val header = LinearLayout(context)
    private val details = LinearLayout(context)
    private val visit = number()
    private val today = number()
    private val sessionIcon = icon("session")
    private val todayIcon = icon("today")
    private val periods = TextView(context).apply {
        setTextColor(iconColor); textSize = 12f; gravity = Gravity.CENTER
        setPadding(dp(8), dp(4), dp(8), dp(4))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val categoryRows = mutableMapOf<PageCategory, Triple<LinearLayout, ScrollingCountView, ScrollingCountView>>()
    var expanded = false
        private set
    var moveCounter: (Float, Float) -> Unit = { _, _ -> }
    var resetPosition: () -> Unit = {}

    init {
        orientation = VERTICAL
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        background = GradientDrawable().apply {
            setColor(Color.argb(163, 18, 24, 34)); cornerRadius = dp(24).toFloat()
            setStroke(dp(1), Color.argb(110, 181, 205, 245))
        }
        elevation = dp(4).toFloat()
        clipToOutline = true
        header.apply {
            gravity = Gravity.CENTER; minimumHeight = dp(48)
            setPadding(dp(14), 0, dp(14), 0)
            isClickable = true; isFocusable = true
            accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_NONE
            addView(sessionIcon, LayoutParams(dp(20), dp(20)))
            addView(visit, LayoutParams(LayoutParams.WRAP_CONTENT, dp(48)).apply { marginStart = dp(6) })
            addView(View(context).apply { setBackgroundColor(Color.rgb(73, 85, 104)) },
                LayoutParams(dp(1), dp(18)).apply { marginStart = dp(12); marginEnd = dp(12) })
            addView(todayIcon, LayoutParams(dp(20), dp(20)))
            addView(today, LayoutParams(LayoutParams.WRAP_CONTENT, dp(48)).apply { marginStart = dp(6) })
            setOnClickListener { setExpanded(!expanded) }
            var downX = 0f; var downY = 0f; var lastX = 0f; var lastY = 0f
            var dragging = false
            val slop = ViewConfiguration.get(context).scaledTouchSlop
            setOnTouchListener { target, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX; downY = event.rawY; lastX = downX; lastY = downY; dragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging && (kotlin.math.abs(event.rawX - downX) > slop || kotlin.math.abs(event.rawY - downY) > slop)) dragging = true
                        if (dragging) { moveCounter(event.rawX - lastX, event.rawY - lastY); lastX = event.rawX; lastY = event.rawY }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (dragging) moveCounter(event.rawX - lastX, event.rawY - lastY) else target.performClick()
                        dragging = false; true
                    }
                    MotionEvent.ACTION_CANCEL -> { dragging = false; true }
                    else -> false
                }
            }
            val moves = listOf(Triple(View.generateViewId(), "Move counter left", Pair(-48f, 0f)),
                Triple(View.generateViewId(), "Move counter right", Pair(48f, 0f)),
                Triple(View.generateViewId(), "Move counter up", Pair(0f, -48f)),
                Triple(View.generateViewId(), "Move counter down", Pair(0f, 48f)))
            val resetAction = View.generateViewId()
            accessibilityDelegate = object : AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = android.widget.Button::class.java.name
                    for ((id, label) in moves.map { Pair(it.first, it.second) })
                        info.addAction(AccessibilityNodeInfo.AccessibilityAction(id, label))
                    info.addAction(AccessibilityNodeInfo.AccessibilityAction(resetAction, "Move counter to top center"))
                }
                override fun performAccessibilityAction(host: View, action: Int, args: android.os.Bundle?): Boolean {
                    moves.firstOrNull { it.first == action }?.let {
                        moveCounter(it.third.first * density, it.third.second * density); return true
                    }
                    if (action == resetAction) { resetPosition(); return true }
                    return super.performAccessibilityAction(host, action, args)
                }
            }
        }
        addView(header, LayoutParams(LayoutParams.WRAP_CONTENT, dp(48)))
        details.apply {
            orientation = VERTICAL; visibility = GONE
            setPadding(dp(8), 0, dp(8), dp(6))
        }
        details.addView(periods, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val icons = mapOf(PageCategory.REEL to "video", PageCategory.PHOTO to "photo",
            PageCategory.AD to "ad", PageCategory.UNCLASSIFIED to "unknown")
        for ((category, symbol) in icons) {
            val sessionValue = number(); val dayValue = number()
            val row = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(40)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
                isFocusable = true
                setPadding(dp(8), 0, dp(8), 0)
                addView(icon(symbol), LayoutParams(dp(20), dp(20)))
                addView(sessionValue, LayoutParams(0, dp(40), 1f).apply { marginStart = dp(8) })
                addView(dayValue, LayoutParams(0, dp(40), 1f))
            }
            categoryRows[category] = Triple(row, sessionValue, dayValue)
            details.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))
        }
        val actions = LinearLayout(context).apply { gravity = Gravity.CENTER }
        for ((symbol, label, action) in listOf(Triple("dashboard", "Open dashboard", onOpen),
                Triple("pause", "Pause observation", onPause), Triple("hide", "Hide counter; keep observation on", onHide))) {
            actions.addView(icon(symbol).apply {
                isClickable = true; isFocusable = true
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
                contentDescription = label
                setPadding(dp(14), dp(14), dp(14), dp(14))
                setOnClickListener { action() }
            }, LayoutParams(dp(48), dp(48)))
        }
        details.addView(actions)
        addView(details, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun number() = ScrollingCountView(context)

    private fun icon(symbol: String) = SymbolView(context, symbol, iconColor).apply {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setExpanded(value: Boolean) {
        expanded = value
        details.visibility = if (value) VISIBLE else GONE
        (background as GradientDrawable).setColor(Color.argb(if (value) 214 else 163, 18, 24, 34))
        header.isSelected = value
        if (android.os.Build.VERSION.SDK_INT >= 30)
            header.stateDescription = if (value) "Expanded" else "Collapsed"
    }

    fun update(state: ReelsCoordinator.OverlaySnapshot) {
        sessionIcon.visibility = if (state.showIcons) VISIBLE else GONE
        todayIcon.visibility = if (state.showIcons) VISIBLE else GONE
        for (number in listOf(visit, today)) {
            val params = number.layoutParams as LayoutParams
            val gap = if (state.showIcons) dp(6) else 0
            if (params.marginStart != gap) { params.marginStart = gap; number.layoutParams = params }
        }
        val scopeLabel = if (state.allAppsToday) "All observed apps today" else
            "${state.app.displayName} today"
        periods.text = "This visit | $scopeLabel"
        visit.update(state.visit, state.scrollBack)
        today.update(state.today, state.scrollBack)
        header.contentDescription = "Estimated viewer pages including ads. This visit ${state.visit}. $scopeLabel ${state.today}. Tap to expand or collapse statistics."
        for (item in state.categories) categoryRows[item.category]?.let { (row, sessionValue, dayValue) ->
            sessionValue.update(item.visit, state.scrollBack); dayValue.update(item.today, state.scrollBack)
            val label = when (item.category) {
                PageCategory.REEL -> "Video layouts"
                PageCategory.PHOTO -> "Photo or carousel pages"
                PageCategory.AD -> "Recognized image ads"
                else -> "Unclassified pages"
            }
            row.contentDescription = "$label. This visit ${item.visit}. $scopeLabel ${item.today}. Partial estimates."
        }
    }

    private class SymbolView(context: Context, private val symbol: String, color: Int) : View(context) {
        override fun getAccessibilityClassName(): CharSequence =
            if (isClickable) android.widget.Button::class.java.name else super.getAccessibilityClassName()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.6f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val size = minOf(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat()
            if (size <= 0) return
            canvas.save()
            canvas.translate(paddingLeft + (width - paddingLeft - paddingRight - size) / 2,
                paddingTop + (height - paddingTop - paddingBottom - size) / 2)
            canvas.scale(size / 24, size / 24)
            fun line(x: Float, y: Float, xx: Float, yy: Float) = canvas.drawLine(x, y, xx, yy, paint)
            fun path(vararg points: Float) {
                val p = Path().apply { moveTo(points[0], points[1]); for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1]) }
                canvas.drawPath(p, paint)
            }
            when (symbol) {
                "session" -> { canvas.drawArc(4f, 4f, 20f, 20f, -65f, 300f, false, paint)
                    path(3f, 5f, 4f, 10f, 9f, 9f); path(12f, 7f, 12f, 12f, 16f, 14f) }
                "today" -> { canvas.drawRoundRect(4f, 5f, 20f, 21f, 3f, 3f, paint)
                    line(8f, 3f, 8f, 7f); line(16f, 3f, 16f, 7f); line(4f, 10f, 20f, 10f)
                    canvas.drawCircle(12f, 15f, 1.5f, paint) }
                "video" -> { canvas.drawRoundRect(3f, 4f, 21f, 20f, 3f, 3f, paint); path(10f, 8f, 16f, 12f, 10f, 16f, 10f, 8f) }
                "photo" -> { canvas.drawRoundRect(3f, 4f, 21f, 20f, 3f, 3f, paint)
                    canvas.drawCircle(8f, 9f, 1.5f, paint); path(4f, 18f, 11f, 12f, 16f, 17f, 20f, 14f) }
                "ad" -> { path(4f, 10f, 9f, 10f, 19f, 5f, 19f, 19f, 9f, 14f, 4f, 14f, 4f, 10f)
                    path(8f, 14f, 9f, 20f, 12f, 20f, 11f, 15f) }
                "unknown" -> { canvas.drawCircle(12f, 12f, 9f, paint)
                    for (x in listOf(8f, 12f, 16f)) canvas.drawCircle(x, 12f, 0.6f, paint) }
                "dashboard" -> for (x in listOf(4f, 14f)) for (y in listOf(4f, 14f)) canvas.drawRoundRect(x, y, x+6, y+6, 1f, 1f, paint)
                "pause" -> { canvas.drawRoundRect(6f, 4f, 9f, 20f, 1f, 1f, paint); canvas.drawRoundRect(15f, 4f, 18f, 20f, 1f, 1f, paint) }
                "hide" -> { canvas.drawOval(3f, 7f, 21f, 17f, paint); canvas.drawCircle(12f, 12f, 3f, paint); line(3f, 3f, 21f, 21f) }
            }
            canvas.restore()
        }
    }
}
