package com.attentionstats.qualification

import android.app.Instrumentation
import com.attentionstats.reels.ReminderDismissal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Actual saturated-channel admission; no coordinator singleton, history, grants or preferences. */
internal object ReminderDismissalVerification {
    fun run(instrumentation: Instrumentation): Int = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = Channel<suspend () -> Unit>(256)
        val resets = AtomicInteger()
        val acknowledgements = AtomicInteger()
        val drained = AtomicInteger()
        val submitted = CountDownLatch(1)
        var checks = 0
        fun expect(value: Boolean) { check(value) { "dismissal check ${checks + 1}" }; checks++ }
        try {
            repeat(256) { check(queue.trySend { drained.incrementAndGet() }.isSuccess) }
            // Establish the old failure condition before invoking the production dismissal path.
            expect(queue.trySend { error("rejected work must not execute") }.isFailure)
            instrumentation.runOnMainSync {
                ReminderDismissal.enqueue(scope, queue, { resets.incrementAndGet() }) {
                    check(resets.get() == 1 && drained.get() == 256)
                    acknowledgements.incrementAndGet()
                }.invokeOnCompletion { submitted.countDown() }
            }
            // A second main-thread round trip proves waiting for capacity did not block the UI.
            instrumentation.runOnMainSync { }
            expect(resets.get() == 0 && acknowledgements.get() == 0)
            withTimeout(5_000) {
                repeat(256) { queue.receive().invoke() }
                queue.receive().invoke()
            }
            expect(submitted.await(5, TimeUnit.SECONDS))
            expect(drained.get() == 256)
            expect(resets.get() == 1 && acknowledgements.get() == 1)
            expect(queue.tryReceive().isFailure)

            // Normal-capacity dismissal also acknowledges exactly once, after reset execution.
            val normal = ReminderDismissal.enqueue(scope, queue, { resets.incrementAndGet() }) {
                check(resets.get() == 2)
                acknowledgements.incrementAndGet()
            }
            withTimeout(5_000) { normal.join() }
            expect(resets.get() == 1 && acknowledgements.get() == 1)
            withTimeout(5_000) { queue.receive().invoke() }
            expect(resets.get() == 2 && acknowledgements.get() == 2)
            expect(queue.tryReceive().isFailure)
            checks
        } finally {
            scope.cancel()
            queue.cancel()
        }
    }
}
