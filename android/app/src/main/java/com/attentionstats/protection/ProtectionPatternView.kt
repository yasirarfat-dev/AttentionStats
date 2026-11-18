package com.attentionstats.protection

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.attentionstats.appearance.NativePalette
import kotlin.math.hypot
import kotlin.math.min
import org.attentionstats.protection.ParentSecretRules

/**
 * App-own 3x3 parent pattern, separate from the phone's lock-screen pattern. Touch-drag connects dots and
 * auto-includes skipped middle dots ([ParentSecretRules.extend]). The path lives only in this view until the
 * owner takes a copy in [onComplete]; [clear] wipes it.
 */
@SuppressLint("ViewConstructor")
internal class ProtectionPatternView(context: Context, private val palette: NativePalette) : View(context) {
    /** Receives a copy of the finished path; the owner clears it after use. */
    var onComplete: ((IntArray) -> Unit)? = null
    var onStart: (() -> Unit)? = null
    var inputEnabled = true
        set(value) { field = value; alpha = if (value) 1f else .55f }
    private var path = IntArray(0)
    private var error = false
    private var tracking = false
    private var fingerX = 0f
    private var fingerY = 0f
    private val density = resources.displayMetrics.density
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 5 * density
    }
    private val trace = Path()

    init {
        isFocusable = true
        isHapticFeedbackEnabled = true
        contentDescription = "Pattern grid with 9 dots. Drag through at least 4 dots."
        if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }

    private fun side() = min(width, height).toFloat()
    private fun left() = (width - side()) / 2f
    private fun top() = (height - side()) / 2f
    private fun cellX(cell: Int) = left() + side() / 3f * (cell % 3 + .5f)
    private fun cellY(cell: Int) = top() + side() / 3f * (cell / 3 + .5f)
    private fun cellAt(x: Float, y: Float): Int? {
        val radius = side() / 3f * .34f
        return (0 until ParentSecretRules.PATTERN_CELLS).firstOrNull { hypot(x - cellX(it), y - cellY(it)) <= radius }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec)
        val square = min(available, (300 * density).toInt())
        setMeasuredDimension(available, square)
    }

    /** Copy of the drawn path. */
    fun snapshot(): IntArray = path.copyOf()
    fun showError() { error = true; invalidate() }
    fun clear() { path.fill(0); path = IntArray(0); error = false; tracking = false; invalidate() }

    private fun visit(x: Float, y: Float) {
        val cell = cellAt(x, y) ?: return
        val next = ParentSecretRules.extend(path, cell)
        if (next.size != path.size) {
            path.fill(0); path = next
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    @SuppressLint("ClickableViewAccessibility") // performClick is called when a drawing ends.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!inputEnabled || !isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                clear(); tracking = true
                parent?.requestDisallowInterceptTouchEvent(true) // Drawing must not scroll the page.
                onStart?.invoke()
                visit(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.historySize) visit(event.getHistoricalX(i), event.getHistoricalY(i))
                visit(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> {
                tracking = false
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                if (path.isNotEmpty()) onComplete?.invoke(path.copyOf())
            }
            MotionEvent.ACTION_CANCEL -> { parent?.requestDisallowInterceptTouchEvent(false); clear() }
            else -> return true
        }
        fingerX = event.x; fingerY = event.y
        invalidate()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val active = if (error) palette.danger else palette.accent
        if (path.isNotEmpty()) {
            trace.reset()
            trace.moveTo(cellX(path[0]), cellY(path[0]))
            for (i in 1 until path.size) trace.lineTo(cellX(path[i]), cellY(path[i]))
            if (tracking) trace.lineTo(fingerX, fingerY)
            line.color = active; line.alpha = 200
            canvas.drawPath(trace, line)
        }
        for (cell in 0 until ParentSecretRules.PATTERN_CELLS) {
            val selected = cell in path
            if (selected) {
                halo.color = active; halo.alpha = 48
                canvas.drawCircle(cellX(cell), cellY(cell), 22 * density, halo)
            }
            dot.color = if (selected) active else palette.muted
            canvas.drawCircle(cellX(cell), cellY(cell), (if (selected) 9 else 6) * density, dot)
        }
    }

    override fun onDetachedFromWindow() { clear(); super.onDetachedFromWindow() }
}
