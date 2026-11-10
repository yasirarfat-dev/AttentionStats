package com.attentionstats.qualification

import android.app.Instrumentation
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.attentionstats.autoscroll.AutoScrollSession
import com.attentionstats.reels.CounterHudView
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.reels.PageCategory
import org.attentionstats.reels.ReelApp

/** Physical ART/view-state checks. Does not claim the source overlay was visually accepted. */
internal object HudBehaviorVerification {
    fun run(instrumentation: Instrumentation): Int {
        var checks = 0
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            try {
                check(!AutoScrollSession.running) { "existing active run preserved" }
                fun expect(value: Boolean) { check(value) { "HUD behavior ${checks + 1}" }; checks++ }
                val observer = AutoScrollSession.onChanged
                val wasEnabled = AutoScrollSession.enabled
                val hadNotice = AutoScrollSession.noticeAccepted
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
                    // Refreshes while paused must keep the actionable spoken state; resuming
                    // must immediately restore the latest counts without waiting for a scan.
                    val snapshot = ReelsCoordinator.OverlaySnapshot(ReelApp.INSTAGRAM, 7, 12, 1,
                        categories = listOf(
                            ReelsCoordinator.OverlayCategory(PageCategory.REEL, 2, 5),
                            ReelsCoordinator.OverlayCategory(PageCategory.UNCLASSIFIED, 3, 4)))
                    hud.update(snapshot)
                    val header = hud.getChildAt(0)
                    val todayIcon = (header as ViewGroup).getChildAt(3)
                    hud.setPausedState(true)
                    expect(header.contentDescription.toString().startsWith("Counting paused."))
                    hud.update(snapshot.copy(visit = 8, today = 13))
                    expect(header.contentDescription.toString().startsWith("Counting paused."))
                    expect(todayIcon.visibility == View.GONE)
                    hud.setPausedState(false)
                    expect(todayIcon.visibility == View.VISIBLE)
                    expect(header.contentDescription.toString().contains("apps 8.") &&
                        header.contentDescription.toString().contains("today 13."))
                    val details = hud.getChildAt(hud.childCount - 1) as ViewGroup
                    val videoRow = details.getChildAt(1)
                    expect(videoRow.contentDescription.toString().contains("apps 5.") &&
                        videoRow.contentDescription.toString().contains("today 9."))
                    hud.setPausedState(true)
                    hud.update(snapshot.copy(showIcons = false))
                    expect(todayIcon.visibility == View.GONE)
                    hud.setPausedState(false)
                    expect(todayIcon.visibility == View.GONE)
                } finally {
                    AutoScrollSession.clear()
                    if (hadNotice || wasEnabled) {
                        AutoScrollSession.configure(true, 1)
                        if (!wasEnabled) AutoScrollSession.configure(false, 1)
                    }
                    AutoScrollSession.onChanged = observer
                }
            } catch (caught: Throwable) { failure = caught }
        }
        failure?.let { throw it }
        return checks
    }
}
