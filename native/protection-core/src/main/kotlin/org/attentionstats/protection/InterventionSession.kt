package org.attentionstats.protection

/** One prompt lifetime, driven by a supplied monotonic clock. Owner confines calls to the UI thread. */
class InterventionSession(private val waitDurationMs: Long) {
    init { require(waitDurationMs in 0..60_000) }
    private var startedAtMs: Long? = null
    private var finished = false

    fun start(atMs: Long) { if (!finished && startedAtMs == null) startedAtMs = atMs }
    fun remainingSeconds(atMs: Long): Int {
        val elapsed = startedAtMs?.let { (atMs - it).coerceAtLeast(0) } ?: 0
        return ((waitDurationMs - elapsed).coerceAtLeast(0) + 999).div(1000).toInt()
    }
    fun choose(atMs: Long, waitRequired: Boolean): Boolean {
        if (finished || startedAtMs == null || (waitRequired && remainingSeconds(atMs) > 0)) return false
        finished = true
        return true
    }
    fun finish() { finished = true }
}
