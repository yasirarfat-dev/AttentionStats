package com.attentionstats.autoscroll

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import com.attentionstats.detectors.LayoutBounds

/** Sole platform action location. Caller must freshly revalidate the viewer before this bounded swipe. */
internal object AutoScrollGestureDispatcher {
    fun dispatch(service: AccessibilityService, bounds: LayoutBounds, token: Long, handler: Handler) {
        if (!AutoScrollSession.current(token)) return
        val x = bounds.left + bounds.width * 0.5f
        val path = Path().apply {
            moveTo(x, bounds.top + bounds.height * 0.72f)
            lineTo(x, bounds.top + bounds.height * 0.28f)
        }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 300)).build()
        val accepted = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (AutoScrollSession.current(token)) AutoScrollSession.stop("gesture_cancelled")
            }
        }, handler)
        if (!accepted) AutoScrollSession.stop("gesture_unavailable")
    }
}
