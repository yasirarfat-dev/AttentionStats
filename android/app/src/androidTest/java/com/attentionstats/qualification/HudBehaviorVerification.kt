package com.attentionstats.qualification

import android.app.Instrumentation
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.attentionstats.autoscroll.AutoScrollSession
import com.attentionstats.reels.CounterHudView
import org.attentionstats.reels.ReelApp

/** Physical ART/view-state checks. Does not claim the source overlay was visually accepted. */
internal object HudBehaviorVerification {
    fun run(instrumentation: Instrumentation): Int {
        var checks = 0
        instrumentation.runOnMainSync {
            fun expect(value: Boolean) { check(value) { "HUD behavior ${checks + 1}" }; checks++ }
            val observer = AutoScrollSession.onChanged
            AutoScrollSession.onChanged = {}
            try {
                AutoScrollSession.clear()
                lateinit var hud: CounterHudView
                var toggles = 0
                hud = CounterHudView(instrumentation.targetContext, {}, {}, {}, {
                    toggles++
                    if (AutoScrollSession.running) AutoScrollSession.stop() else {
                        if (!AutoScrollSession.enabled) AutoScrollSession.configure(true, 1)
                        AutoScrollSession.start()
                    }
                    hud.setAutoScrollControl(true, AutoScrollSession.running, AutoScrollSession.canStart())
                })
                fun texts(view: View): List<TextView> = if (view is TextView) listOf(view) else if (view is ViewGroup)
                    (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList()
                hud.setAutoScrollControl(true, false, false)
                val controls = texts(hud).filter { it.text == "Autoscroll" }
                expect(controls.size == 1)
                val control = controls.single()
                expect(control.text == "Autoscroll" && !control.isSelected && control.compoundDrawablesRelative[2] != null && control.visibility == View.GONE)
                hud.setExpanded(true)
                expect(control.visibility == View.VISIBLE && !AutoScrollSession.running)
                control.performClick()
                expect(toggles == 1 && AutoScrollSession.running)
                expect(control.text == "Autoscroll" && control.isSelected && control.compoundDrawablesRelative[2] != null && control.visibility == View.VISIBLE)
                hud.getChildAt(0).performClick()
                expect(!hud.expanded && control.visibility == View.VISIBLE && toggles == 1)
                val started = AutoScrollSession.revision
                expect(!AutoScrollSession.current(started)) // On choice without a qualified frame cannot dispatch.
                var dispatches = 0
                fun frame(eligible: Boolean) = AutoScrollSession.Frame(ReelApp.FACEBOOK, 1, 1, 1,
                    SystemClock.elapsedRealtime(), .1, eligible)
                AutoScrollSession.frame(frame(false)) { _, _ -> dispatches++ }
                expect(AutoScrollSession.running && dispatches == 0)
                AutoScrollSession.frame(frame(true)) { _, _ -> dispatches++ }
                val qualified = AutoScrollSession.revision
                expect(AutoScrollSession.current(qualified) && dispatches == 0)
                AutoScrollSession.manualInteraction("manual_scroll")
                expect(AutoScrollSession.running && !AutoScrollSession.current(qualified))
                AutoScrollSession.unavailable("sensitive_context")
                expect(AutoScrollSession.running && !AutoScrollSession.canStart() && dispatches == 0)
                hud.setAutoScrollControl(true, AutoScrollSession.running, false)
                expect(control.text == "Autoscroll" && control.isSelected && control.compoundDrawablesRelative[2] != null && control.visibility == View.VISIBLE)
                hud.getChildAt(0).performClick()
                expect(hud.expanded && AutoScrollSession.running && toggles == 1)
                control.performClick()
                expect(!AutoScrollSession.running && toggles == 2)
                hud.setExpanded(false)
                expect(control.visibility == View.GONE)
                expect(texts(hud).count { it.text == "Autoscroll" } == 1 && dispatches == 0)
            } finally {
                AutoScrollSession.clear()
                AutoScrollSession.onChanged = observer
            }
        }
        return checks
    }
}
