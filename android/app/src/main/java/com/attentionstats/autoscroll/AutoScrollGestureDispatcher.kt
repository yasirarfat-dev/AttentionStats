package com.attentionstats.autoscroll

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import org.attentionstats.detection.LayoutBounds

/** Sole platform action location. Caller must freshly revalidate the viewer before this bounded swipe. */
internal object AutoScrollGestureDispatcher {
    fun dispatch(service: AccessibilityService, bounds: LayoutBounds, token: Long, handler: Handler) {
        if (!AutoScrollSession.current(token)) {
            AutoScrollSession.onDispatchDiagnostic?.invoke("dispatch_token_expired"); return
        }
        val x = bounds.left + bounds.width * 0.5f
        val path = Path().apply {
            moveTo(x, bounds.top + bounds.height * 0.72f)
            lineTo(x, bounds.top + bounds.height * 0.28f)
        }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 300)).build()
        val accepted = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                AutoScrollSession.onDispatchDiagnostic?.invoke("gesture_completed")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                AutoScrollSession.onDispatchDiagnostic?.invoke("gesture_cancelled")
                if (AutoScrollSession.current(token)) AutoScrollSession.suspendActions("gesture_cancelled")
            }
        }, handler)
        AutoScrollSession.onDispatchDiagnostic?.invoke(if (accepted) "gesture_accepted" else "gesture_rejected")
        if (!accepted) AutoScrollSession.suspendActions("gesture_unavailable")
    }
}
