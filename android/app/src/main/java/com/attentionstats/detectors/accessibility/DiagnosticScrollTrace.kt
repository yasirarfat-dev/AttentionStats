package com.attentionstats.detectors.accessibility

import org.attentionstats.reels.ReelApp
import org.json.JSONArray
import org.json.JSONObject

/** Temporary metadata only. Brackets delivered events; never claims an atomic Android tree. */
internal class DiagnosticScrollTrace {
    internal data class Scope(val app: ReelApp, val epoch: Long, val window: Int)
    internal data class Ticket(val scope: Scope, val generation: Long, val sequence: Int, val started: Long)
    private data class Sample(val sequence: Int, val received: Long, val delay: Long, val value: JSONObject)
    private var scope: Scope? = null
    private var generation = 0L
    private var sequence = 0
    private val samples = ArrayDeque<Sample>()

    @Synchronized fun clear() {
        scope = null
        generation++
        sequence = 0
        samples.clear()
    }

    private fun select(next: Scope) {
        if (scope != next) { clear(); scope = next }
    }

    @Synchronized fun record(next: Scope, received: Long, eventTime: Long, value: JSONObject) {
        select(next)
        sequence++ // Even an old/invalid-time event invalidates a scan bracket.
        if (received < 0 || eventTime < 0 || eventTime > received || received - eventTime > 2_000) return
        if (samples.size == 16) samples.removeFirst()
        samples.addLast(Sample(sequence, received, received - eventTime, value))
    }

    @Synchronized fun begin(next: Scope, now: Long): Ticket {
        select(next)
        return Ticket(next, generation, sequence, now)
    }

    @Synchronized fun finish(ticket: Ticket, now: Long): JSONObject? {
        if (scope != ticket.scope || generation != ticket.generation || now < ticket.started ||
            now - ticket.started > 2_000) return null
        val rows = samples.filter { it.received <= ticket.started && ticket.started - it.received <= 2_000 }
        return JSONObject().put("revision", 1).put("startSequence", ticket.sequence)
            .put("endSequence", sequence).put("durationMs", now - ticket.started)
            .put("stable", ticket.sequence == sequence).put("events", JSONArray(rows.map {
                JSONObject(it.value.toString()).put("sequence", it.sequence)
                    .put("ageMs", ticket.started - it.received).put("deliveryDelayMs", it.delay)
            }))
    }
}
