package com.attentionstats.qualification

import android.app.Instrumentation
import android.os.SystemClock
import com.attentionstats.reels.SurfaceSamplingLoop
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real Handler/executor ordering with synthetic reads; no coordinator, source or history. */
internal object SurfaceSamplingVerification {
    fun run(instrumentation: Instrumentation): Int {
        var checks = 0
        fun expect(value: Boolean) { check(value) { "surface sampling ${checks + 1}" }; checks++ }
        fun await(latch: CountDownLatch) = expect(latch.await(5, TimeUnit.SECONDS))
        fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
        val loops = mutableListOf<SurfaceSamplingLoop>()
        fun loop(sample: () -> Long?, failed: () -> Long? = { error("unexpected read failure") }) =
            SurfaceSamplingLoop(sample = sample, failed = failed).also { loops.add(it) }
        try {
            // Missing-root retries continue without callbacks; an unrelated owner returns null.
            val reads = AtomicInteger()
            val recovered = CountDownLatch(1)
            val retry = loop({
                if (reads.incrementAndGet() < 3) 20L else { recovered.countDown(); null }
            })
            retry.request(0)
            await(recovered)
            SystemClock.sleep(100)
            expect(reads.get() == 3)
            main { retry.close() }

            val errors = AtomicInteger()
            val afterError = CountDownLatch(1)
            val errorReads = AtomicInteger()
            val failing = loop({
                if (errorReads.incrementAndGet() == 1) throw IllegalStateException("synthetic stale root")
                afterError.countDown(); null
            }, { errors.incrementAndGet(); 20L })
            failing.request(0)
            await(afterError)
            expect(errors.get() == 1 && errorReads.get() == 2)
            main { failing.close() }

            // An entry event must expedite a pending idle poll, rather than wait its full delay.
            val expedited = CountDownLatch(1)
            val wake = loop({ expedited.countDown(); null })
            main { wake.request(60_000); wake.request(0); wake.request(60_000) }
            await(expedited)
            main { wake.close() }

            // Burst events during a blocked read coalesce into one following read, never overlap.
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val again = CountDownLatch(1)
            val serialReads = AtomicInteger()
            val serial = loop({
                if (serialReads.incrementAndGet() == 1) {
                    entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
                } else again.countDown()
                null
            })
            serial.request(0); await(entered)
            main { repeat(20) { serial.request(0) } }
            // A second UI round trip is possible while the source read is blocked.
            main { }
            expect(serialReads.get() == 1)
            release.countDown(); await(again)
            SystemClock.sleep(100)
            expect(serialReads.get() == 2)
            main { serial.close() }

            // Teardown fences both a queued tick and a late uninterruptible source-read result.
            val cancelledReads = AtomicInteger()
            val cancelled = loop({ cancelledReads.incrementAndGet(); null })
            main { cancelled.request(100); cancelled.close(); cancelled.request(0) }
            SystemClock.sleep(180)
            expect(cancelledReads.get() == 0)
            val lateEntered = CountDownLatch(1)
            val lateRelease = CountDownLatch(1)
            val lateFinished = CountDownLatch(1)
            val lateReads = AtomicInteger()
            val late = loop({
                lateReads.incrementAndGet(); lateEntered.countDown()
                while (true) {
                    try { check(lateRelease.await(5, TimeUnit.SECONDS)); break }
                    catch (_: InterruptedException) { /* Simulate a binder read completing after close. */ }
                }
                lateFinished.countDown(); 0L
            })
            late.request(0); await(lateEntered)
            main { late.close() }
            lateRelease.countDown(); await(lateFinished)
            SystemClock.sleep(100)
            expect(lateReads.get() == 1)
            return checks
        } finally {
            main { loops.forEach { it.close() } }
        }
    }
}
