package com.attentionstats.reels

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Serial, event-wakeable sampling. A null delay sleeps until the next source event. */
internal class SurfaceSamplingLoop(
    private val worker: ExecutorService = Executors.newSingleThreadExecutor(),
    private val sample: () -> Long?,
    private val failed: () -> Long?,
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false
    private var scanning = false
    private var requestedAt: Long? = null
    private var configuration: Pair<Long, Long>? = null
    private val tick = Runnable {
        requestedAt = null
        scanning = true
        worker.execute {
            val next = try { if (closed) null else sample() }
            catch (_: RuntimeException) { if (closed) null else failed() }
            main.post {
                scanning = false
                if (!closed) {
                    if (next != null) request(next)
                    arm()
                }
            }
        }
    }

    fun request(delayMs: Long) {
        if (closed) return
        if (Looper.myLooper() != main.looper) { main.post { request(delayMs) }; return }
        val deadline = SystemClock.uptimeMillis() + delayMs.coerceAtLeast(0)
        if (requestedAt?.let { it <= deadline } == true) return
        requestedAt = deadline
        arm()
    }

    /** Observation publications repaint the HUD; only changed settings/epochs wake acquisition. */
    fun configurationChanged(epoch: Long, revision: Long) {
        if (closed) return
        if (Looper.myLooper() != main.looper) { main.post { configurationChanged(epoch, revision) }; return }
        val next = epoch to revision
        if (configuration == next) return
        configuration = next
        request(0)
    }

    private fun arm() {
        if (closed || scanning) return
        main.removeCallbacks(tick)
        requestedAt?.let { main.postAtTime(tick, it) }
    }

    /** Called by the owning service on the main thread; a late worker cannot restart it. */
    fun close() {
        check(Looper.myLooper() == main.looper)
        closed = true
        main.removeCallbacksAndMessages(null)
        requestedAt = null
        worker.shutdownNow()
    }
}
