package com.attentionstats.qualification

import android.app.Instrumentation
import android.os.SystemClock
import com.attentionstats.autoscroll.AutoScrollSession
import org.attentionstats.reels.ReelApp

/** Synthetic runtime controls; no source activation, database writes or platform dispatcher. */
internal object AutoRuntimeProbe {
    fun verify(instrumentation: Instrumentation): Int {
        var checks = 0
        instrumentation.runOnMainSync {
            val auto = AutoScrollSession
            check(!auto.enabled && !auto.running)
            fun expect(value: Boolean) { check(value); checks++ }
            fun frame() = AutoScrollSession.Frame(ReelApp.INSTAGRAM, 7, 10, 3, SystemClock.elapsedRealtime(), 0.0, true)
            var actions = 0
            fun feed(value: AutoScrollSession.Frame) = auto.frame(value) { _, _ -> actions++ }
            try {
                auto.clear(); expect(!auto.start())
                auto.configure(true, 1); expect(!auto.running && !auto.start())
                feed(frame().copy(page = null)); expect(!auto.canStart())
                feed(frame().copy(position = null)); expect(!auto.canStart())
                for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1)) {
                    feed(frame().copy(position = invalid)); expect(!auto.canStart() && !auto.start())
                }
                feed(frame().copy(eligible = false)); expect(!auto.canStart())
                feed(frame().copy(atMs = SystemClock.elapsedRealtime() - 2_000)); expect(!auto.start())
                feed(frame()); expect(auto.canStart() && auto.start())
                auto.manualInteraction("manual_click"); expect(!auto.running && actions == 0)
                for (change in listOf<(AutoScrollSession.Frame) -> AutoScrollSession.Frame>(
                    { it.copy(app = ReelApp.FACEBOOK) }, { it.copy(window = 8) }, { it.copy(epoch = 4) },
                    { it.copy(atMs = it.atMs - 2_000) }, { it.copy(position = Double.NaN) },
                    { it.copy(page = 11) }, { it.copy(page = null) }, { it.copy(position = null) }, { it.copy(eligible = false) })) {
                    auto.clear(); auto.configure(true, 1)
                    val initial = frame(); feed(initial); expect(auto.start())
                    feed(change(initial)); expect(!auto.running && actions == 0)
                }
                auto.clear(); auto.configure(true, 1); feed(frame()); expect(auto.start())
                auto.configure(false, 1); expect(!auto.enabled && !auto.running && auto.noticeAccepted)
                auto.configure(true, 1); expect(auto.enabled && !auto.running)
                expect(!auto.canStart())
                feed(frame()); expect(auto.canStart())
                auto.unavailable("outside_viewer"); expect(!auto.canStart())
                auto.clear(); expect(!auto.enabled && !auto.running && !auto.noticeAccepted && !auto.canStart())
                expect(actions == 0)
            } finally { auto.clear() }
        }
        // Use real monotonic time without blocking the main thread. The callback is
        // an in-memory fake; this suite never invokes the platform dispatcher.
        val auto = AutoScrollSession
        var actions = 0
        var token = -1L
        fun onMain(action: () -> Unit) = instrumentation.runOnMainSync { action() }
        fun expect(value: Boolean) { check(value); checks++ }
        fun feed(page: Long, position: Double) = auto.frame(AutoScrollSession.Frame(
            ReelApp.INSTAGRAM, 7, page, 3, SystemClock.elapsedRealtime(), position, true)) { _, revision ->
                actions++; token = revision
            }
        fun cycle() {
            for (step in 0..10) {
                onMain { feed(10, step / 10.0) }
                SystemClock.sleep(220)
            }
            onMain { feed(10, 0.0) }
        }
        try {
            onMain { auto.configure(true, 1); feed(10, 0.0); expect(auto.start()) }
            cycle()
            onMain {
                expect(actions == 1 && auto.running && auto.current(token))
                auto.frame(AutoScrollSession.Frame(ReelApp.INSTAGRAM, 7, null, 3,
                    SystemClock.elapsedRealtime(), null, true)) { _, _ -> actions++ }
                expect(auto.running && actions == 1) // Own swipe may temporarily withhold page/progress.
                // A queued page redraw must not produce another action.
                feed(10, 0.0); expect(actions == 1)
                feed(11, 0.0); expect(auto.confirmedTransitions == 1 && auto.running)
                auto.manualInteraction("manual_scroll"); expect(auto.running) // Bounded own-swipe tail.
                auto.stop(); expect(!auto.current(token))
            }
            onMain { auto.configure(true, 1); feed(10, 0.0); expect(auto.start()) }
            cycle()
            SystemClock.sleep(2_600)
            onMain {
                // Even a different page arriving after the deadline cannot confirm it.
                feed(11, 0.0)
                expect(!auto.running && auto.confirmedTransitions == 0 && auto.reason == "next_page_unconfirmed")
            }
            onMain { auto.configure(true, 1); feed(10, 0.0); expect(auto.start()) }
            cycle()
            onMain {
                auto.expire(token, SystemClock.elapsedRealtime() + 2_600)
                expect(!auto.running && auto.reason == "next_page_unconfirmed")
                expect(actions == 3)
            }
            onMain { auto.configure(true, 1); feed(10, 0.0); expect(auto.start()) }
            cycle()
            onMain {
                auto.manualInteraction("manual_click")
                expect(!auto.running && auto.reason == "manual_click") // Even during pending confirmation.
            }
            onMain { auto.configure(true, 1); feed(10, 0.0); expect(auto.start()) }
            cycle()
            onMain { feed(11, 0.0); expect(auto.running) }
            SystemClock.sleep(800)
            onMain {
                auto.manualInteraction("manual_scroll")
                expect(!auto.running && auto.reason == "manual_scroll")
            }
            onMain { auto.configure(true, 1); feed(10, 0.0); expect(auto.start()) }
            cycle()
            onMain {
                val delivered = actions
                auto.frame(AutoScrollSession.Frame(ReelApp.INSTAGRAM, 7, 11, 3,
                    SystemClock.elapsedRealtime(), null, true)) { _, _ -> actions++ }
                expect(auto.confirmedTransitions == 1 && !auto.running && auto.reason == "playback_unavailable")
                expect(actions == delivered && !auto.canStart())
                auto.expire(token, SystemClock.elapsedRealtime() + 2_600)
                expect(auto.reason == "playback_unavailable")
            }
        } finally { onMain { auto.clear() } }
        return checks
    }
}
