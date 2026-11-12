package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.os.SystemClock
import com.attentionstats.autoscroll.AutoScrollSession

/** Actual own-overlay controls. No source text, screenshots or source gestures. */
internal object LiveAutoScrollControlsVerification {
    fun run(instrumentation: Instrumentation): Int {
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun main(action: () -> Unit) {
            var failure: Throwable? = null
            instrumentation.runOnMainSync { try { action() } catch (caught: Throwable) { failure = caught } }
            failure?.let { throw it }
        }
        var checks = 0
        fun expect(value: Boolean) { check(value) { "actual auto controls ${checks + 1}" }; checks++ }
        fun state() = LiveHudVerification.state(instrumentation, automation)
        fun choose(label: String) { LiveHudVerification.choose(instrumentation, automation, label); SystemClock.sleep(200) }
        var wasEnabled = false
        var hadNotice = false
        main {
            check(!AutoScrollSession.running) { "existing active run preserved" }
            wasEnabled = AutoScrollSession.enabled; hadNotice = AutoScrollSession.noticeAccepted
        }
        val until = SystemClock.elapsedRealtime() + 10_000
        while (state().header == null && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100)
        expect(state().header != null)
        val initial = state()
        check(!initial.paused) { "counting pause preserved; resume separately before expanded-control trial" }
        val wasExpanded = initial.expanded
        try {
            main { AutoScrollSession.configure(true, 1) }
            SystemClock.sleep(200)
            if (!wasExpanded) choose("header")
            expect(state().controls.singleOrNull()?.first == "off")
            choose("off")
            main { AutoScrollSession.suspendActions("control_verification") }
            expect(AutoScrollSession.running && state().controls.singleOrNull()?.first == "on")
            expect(!state().waiting)
            expect(!state().expanded)
            val compact = state()
            val header = checkNotNull(compact.header)
            val button = compact.controls.single().second
            val density = instrumentation.targetContext.resources.displayMetrics.density
            expect(button.top == header.bottom)
            expect(button.bottom - header.top <= (64 * density).toInt() + 2)
            if (android.os.Build.VERSION.SDK_INT >= 29) main {
                val hud = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .filterIsInstance<com.attentionstats.reels.CounterHudView>().single()
                val bitmap = android.graphics.Bitmap.createBitmap(hud.width, hud.height, android.graphics.Bitmap.Config.ARGB_8888)
                try {
                    hud.draw(android.graphics.Canvas(bitmap))
                    java.io.File(instrumentation.targetContext.cacheDir, "compact-hud-attached.png").outputStream().use {
                        check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally { bitmap.recycle() }
            }
            expect(AutoScrollSession.running && state().controls.singleOrNull()?.first == "on")
            choose("on")
            expect(!AutoScrollSession.running && state().controls.isEmpty() && !state().waiting)
            expect(AutoScrollSession.attempts == 0)
        } finally {
            try {
                val current = state()
                if (current.header != null && !current.paused && current.expanded != wasExpanded) choose("header")
            } finally {
                main {
                    AutoScrollSession.clear()
                    if (hadNotice || wasEnabled) {
                        AutoScrollSession.configure(true, 1)
                        if (!wasEnabled) AutoScrollSession.configure(false, 1)
                    }
                }
            }
        }
        return checks
    }
}
