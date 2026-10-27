package com.attentionstats.reels

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.TextSwitcher
import android.widget.TextView

/** Two clipped pages: the committed old value exits as the new value enters vertically. */
internal class ScrollingCountView(context: Context) : TextSwitcher(context) {
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        clipChildren = true
        clipToPadding = true
        measureAllChildren = true
        setAnimateFirstView(false)
        setFactory {
            TextView(context).apply {
                setTextColor(Color.rgb(247, 249, 253)); textSize = 14f
                setShadowLayer(1f, 0f, 0.5f, Color.argb(150, 0, 0, 0))
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                fontFeatureSettings = "tnum"
                gravity = Gravity.CENTER; setSingleLine()
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                maxWidth = (110 * resources.displayMetrics.density).toInt()
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }
        }
    }

    fun update(value: Long, scrollBack: Boolean) {
        val text = "~$value"
        val previous = (currentView as TextView).text
        if (previous.toString() == text) return
        val animate = previous.isNotEmpty() && isShown &&
            (if (Build.VERSION.SDK_INT >= 26) android.animation.ValueAnimator.areAnimatorsEnabled()
            else Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f)
        for (index in 0 until childCount) getChildAt(index).clearAnimation()
        if (animate) {
            val direction = if (scrollBack) -1f else 1f
            fun slide(from: Float, to: Float) = TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, from, Animation.RELATIVE_TO_SELF, to).apply {
                duration = 240
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
            inAnimation = slide(direction, 0f)
            outAnimation = slide(0f, -direction)
            setText(text)
        } else {
            // Initial/hidden/reduced-motion updates replace the visible page without a transition.
            setCurrentText(text)
            for (index in 0 until childCount)
                getChildAt(index).visibility = if (getChildAt(index) === currentView) View.VISIBLE else View.GONE
        }
    }

    override fun onDetachedFromWindow() {
        for (index in 0 until childCount) getChildAt(index).clearAnimation()
        super.onDetachedFromWindow()
    }
}
