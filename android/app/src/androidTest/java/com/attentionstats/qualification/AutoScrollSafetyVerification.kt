package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import com.attentionstats.autoscroll.AutoScrollSession
import org.attentionstats.reels.ReelApp

/** Synthetic session decisions on ART. The callback counts proposals; it never dispatches input. */
internal object AutoScrollSafetyVerification {
    fun run(instrumentation: Instrumentation): Int {
        val context = instrumentation.targetContext
        context.startActivity(Intent(context, com.attentionstats.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        SystemClock.sleep(2_000)
        var checks = 0
        var proposals = 0
        var observer: (() -> Unit)? = null
        var wasEnabled = false
        var hadNotice = false
        fun main(action: () -> Unit) {
            var failure: Throwable? = null
            instrumentation.runOnMainSync { try { action() } catch (caught: Throwable) { failure = caught } }
            failure?.let { throw it }
        }
        fun expect(value: Boolean) { check(value) { "auto-scroll safety ${checks + 1}" }; checks++ }
        main {
            check(!AutoScrollSession.running) {
                "active convenience run preserved; stop it before isolated checks"
            }
            wasEnabled = AutoScrollSession.enabled
            hadNotice = AutoScrollSession.noticeAccepted
            observer = AutoScrollSession.onChanged
            AutoScrollSession.onChanged = {}
        }
        fun feed(page: Long, position: Double?, kind: String = "unknown") = main {
            AutoScrollSession.frame(AutoScrollSession.Frame(ReelApp.INSTAGRAM, 1, page, 1,
                SystemClock.elapsedRealtime(), position, true, kind)) { _, _ -> proposals++ }
        }
        try {
            val key = com.attentionstats.autoscroll.domain.CompletionGate.Context("synthetic", 1, 1, 1)
            fun decisions(values: List<Double>, gapAt: Int = -1): List<Boolean> {
                val gate = com.attentionstats.autoscroll.domain.CompletionGate()
                return values.mapIndexed { index, value ->
                    gate.sample(key, 1_000L + index * 400L + if (index >= gapAt && gapAt >= 0) 2_000 else 0, value, true)
                }
            }
            val middleToEnd = (4..9).flatMap { listOf(it / 10.0, it / 10.0 + .05) } + listOf(.999, .999, .999)
            val terminal = decisions(middleToEnd)
            expect(terminal.count { it } == 1)
            expect(!terminal[terminal.lastIndex - 2] && terminal[terminal.lastIndex - 1] && !terminal.last())
            expect(decisions(List(20) { .999 }).none { it })
            expect(decisions(List(20) { .5 }).none { it })
            expect(decisions((0..9).map { it / 10.0 } + List(10) { .98 }).none { it })
            expect(decisions((0..5).map { it / 10.0 } + List(10) { .999 }).none { it })
            expect(decisions(middleToEnd, middleToEnd.lastIndex - 1).none { it })
            expect(decisions(listOf(Double.NaN) + List(10) { .999 }).none { it })
            expect(decisions(listOf(-.1, 1.1) + List(10) { .999 }).none { it })
            main { AutoScrollSession.configure(true, 1); expect(AutoScrollSession.start()) }
            feed(1, 0.0)
            for (step in 1..10) { SystemClock.sleep(250); feed(1, step / 10.0) }
            SystemClock.sleep(250); feed(1, 0.0)
            main { expect(proposals == 1) }
            main {
                val pending = AutoScrollSession.revision
                AutoScrollSession.transientUnavailable("synthetic_missing_root", ReelApp.INSTAGRAM, 1)
                expect(AutoScrollSession.current(pending))
                expect(AutoScrollSession.reason == "advancing")
                expect(!AutoScrollSession.canStart())
            }
            feed(2, null) // A new viewer with no numeric progress is not known to be a photo.
            main { expect(AutoScrollSession.confirmedTransitions == 1) }
            SystemClock.sleep(5_100); feed(2, null)
            main { expect(proposals == 1) } // Missing video progress must never become a five-second timer.
            main { AutoScrollSession.stop(); expect(!AutoScrollSession.running) }
            feed(2, 0.0)
            main { expect(proposals == 1) }

            // Starting on a known photo works even without a previous video transition.
            main { proposals = 0; AutoScrollSession.configure(true, 1); expect(AutoScrollSession.start()) }
            feed(3, null, "photo")
            main { expect(AutoScrollSession.reason == "still_item") }
            SystemClock.sleep(2_000); feed(3, null, "photo")
            main { expect(proposals == 0) }
            SystemClock.sleep(3_100); feed(3, null, "photo")
            main { expect(proposals == 1) }
            var token = 0L
            main { token = AutoScrollSession.revision; expect(AutoScrollSession.current(token)) }
            main {
                AutoScrollSession.transientUnavailable("synthetic_incomplete_layout", ReelApp.INSTAGRAM, 1)
                expect(AutoScrollSession.current(token))
                AutoScrollSession.expire(token, SystemClock.elapsedRealtime() + 2_500)
                expect(!AutoScrollSession.current(token))
                expect(AutoScrollSession.reason == "next_page_unconfirmed")
                expect(AutoScrollSession.running)
            }
            feed(4, null, "photo")
            SystemClock.sleep(5_100); feed(4, null, "video")
            main { expect(proposals == 1) } // A lost/reclassified photo cannot skip a video.
            main { AutoScrollSession.stop(); expect(!AutoScrollSession.current(token)) }
            feed(4, null, "photo")
            main { expect(proposals == 1) }

            // Starting from the last fresh photo also works; interruption restarts its dwell.
            main { proposals = 0; AutoScrollSession.configure(true, 1) }
            feed(5, null, "photo")
            main { expect(AutoScrollSession.canStart()); expect(AutoScrollSession.start()) }
            SystemClock.sleep(3_000)
            main { AutoScrollSession.suspendActions("manual_scroll") }
            feed(5, null, "photo")
            SystemClock.sleep(2_200); feed(5, null, "photo")
            main { expect(proposals == 0) }
            main { AutoScrollSession.stop() }
            SystemClock.sleep(3_000); feed(5, null, "photo")
            main { expect(proposals == 0) }
            // Acquisition gaps must never carry confirmation across a source or privacy epoch.
            for ((app, epoch) in listOf(ReelApp.TIKTOK to 1L, ReelApp.INSTAGRAM to 2L)) {
                main { AutoScrollSession.configure(true, 1); AutoScrollSession.start() }
                feed(6, null, "photo")
                SystemClock.sleep(5_100); feed(6, null, "photo")
                main {
                    val pending = AutoScrollSession.revision
                    expect(AutoScrollSession.current(pending))
                    AutoScrollSession.transientUnavailable("synthetic_changed_context", app, epoch)
                    expect(!AutoScrollSession.current(pending))
                    expect(AutoScrollSession.running)
                    AutoScrollSession.stop()
                }
            }
            // Pending proposal cancellation: a late arrival/deadline cannot revive its old token.
            // This exercises session fences, not a platform gesture already in flight.
            for (boundary in listOf("stop", "click", "locked", "service_destroyed")) {
                main { proposals = 0; AutoScrollSession.configure(true, 1); AutoScrollSession.start() }
                feed(10, null, "photo")
                SystemClock.sleep(5_100); feed(10, null, "photo")
                main {
                    val pending = AutoScrollSession.revision
                    expect(proposals == 1 && AutoScrollSession.current(pending))
                    when (boundary) {
                        "stop" -> AutoScrollSession.stop()
                        "click" -> AutoScrollSession.manualInteraction("manual_click")
                        "locked" -> AutoScrollSession.unavailable("locked")
                        else -> AutoScrollSession.clear()
                    }
                    expect(!AutoScrollSession.current(pending))
                    AutoScrollSession.expire(pending, SystemClock.elapsedRealtime() + 3_000)
                    AutoScrollSession.frame(AutoScrollSession.Frame(ReelApp.INSTAGRAM, 1, 11, 1,
                        SystemClock.elapsedRealtime(), 0.0, true)) { _, _ -> proposals++ }
                    expect(AutoScrollSession.confirmedTransitions == 0 && proposals == 1)
                    expect(!AutoScrollSession.current(pending))
                    if (boundary in listOf("stop", "service_destroyed")) expect(!AutoScrollSession.running)
                    AutoScrollSession.stop()
                }
            }
        } finally {
            main {
                AutoScrollSession.clear()
                if (hadNotice || wasEnabled) {
                    AutoScrollSession.configure(true, 1)
                    if (!wasEnabled) AutoScrollSession.configure(false, 1)
                }
                AutoScrollSession.onChanged = checkNotNull(observer)
            }
        }
        return checks
    }
}
