package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.os.SystemClock
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.detection.AdapterRegistry
import org.attentionstats.detection.PlaybackTimeParser
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.ReelRoute
import org.attentionstats.reels.Surface
import org.json.JSONObject

/** Bounded no-action probe: only numeric availability/loop aggregates leave memory. */
internal object PlaybackProbeVerification {
    fun run(instrumentation: Instrumentation, key: String): String {
        val app = checkNotNull(ReelApp.byKey(key))
        val owner = ReelsCoordinator.get(instrumentation.targetContext)
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        check(owner.allowed(app)) { "source consent unavailable" }
        check(!com.attentionstats.autoscroll.AutoScrollSession.running) { "active convenience run preserved" }
        var eligible = 0
        var progressFrames = 0
        var numericPairs = 0
        var rangeFrames = 0
        var nearEnds = 0
        var resets = 0
        var identityChanges = 0
        var continuousIdentityChanges = 0
        var allResets = 0
        var proposals = 0
        var firstPosition: Double? = null
        var lastPosition: Double? = null
        var rangeMaximum: Double? = null
        val gate = com.attentionstats.autoscroll.domain.CompletionGate()
        var previousPage: Long? = null
        var previousPosition: Double? = null
        val positions = mutableSetOf<Double>()
        val rangeClasses = mutableSetOf<String>()
        var maxRanges = 0
        repeat(300) { sample ->
            check(owner.allowed(app)) { "source consent changed" }
            check(!com.attentionstats.autoscroll.AutoScrollSession.running) { "convenience run started; probe stopped" }
            val root = checkNotNull(automation.rootInActiveWindow) { "source window unavailable" }
            val snapshot = try {
                check(app.owns(root.packageName?.toString())) { "source left foreground" }
                SnapshotReader.read(root, app.packages)
            } finally { SnapshotReader.recycle(root) }
            check(snapshot.complete && !snapshot.typing) { "incomplete or editable context" }
            val frame = AdapterRegistry.of(app).read(snapshot)
            if (frame.surface == Surface.REELS && frame.route == ReelRoute.VIEWER) {
                eligible++
                val position = frame.progress
                if (position != null) {
                    progressFrames++; positions.add(position)
                    if (firstPosition == null) firstPosition = position
                    lastPosition = position
                    if (position >= .98) nearEnds++
                    if (previousPosition?.let { it > .5 && position < .1 } == true) allResets++
                    if (previousPage == frame.identity && previousPosition?.let { it > .5 && position < .1 } == true) resets++
                }
                if (previousPage != null && frame.identity != null && previousPage != frame.identity) {
                    identityChanges++
                    if (position != null && previousPosition?.let { position >= it && position - it < .15 } == true) continuousIdentityChanges++
                }
                val identity = frame.identity
                if (position != null && identity != null) {
                    if (gate.sample(com.attentionstats.autoscroll.domain.CompletionGate.Context(app.key,
                            snapshot.windowId, identity, owner.epoch()), SystemClock.elapsedRealtime(), position, true)) proposals++
                } else gate.reset()
                previousPage = frame.identity; previousPosition = position
            } else gate.reset()
            if (snapshot.nodes.any { PlaybackTimeParser.parse(it.label) != null }) numericPairs++
            val ranges = snapshot.nodes.filter { it.range != null }
            if (ranges.isNotEmpty()) rangeFrames++
            maxRanges = maxOf(maxRanges, ranges.size)
            ranges.singleOrNull()?.range?.let { rangeMaximum = it.max.toDouble() }
            ranges.forEach { rangeClasses.add(it.cls.take(100)) }
            if ((sample + 1) % 50 == 0) instrumentation.sendStatus(1, android.os.Bundle().apply {
                putString("stream", "Playback probe samples=${sample + 1}; viewer=$eligible; progress=$progressFrames; nearEnd=$nearEnds; resets=$allResets; proposals=$proposals")
            })
            SystemClock.sleep(300)
        }
        return JSONObject().put("samples", 300).put("eligibleViewerSamples", eligible)
            .put("progressSamples", progressFrames).put("distinctProgress", positions.size)
            .put("nearEndSamples", nearEnds).put("samePageNumericResets", resets)
            .put("identityChanges", identityChanges).put("numericTimePairSamples", numericPairs)
            .put("continuousProgressIdentityChanges", continuousIdentityChanges).put("allNumericResets", allResets)
            .put("firstProgress", firstPosition).put("lastProgress", lastPosition)
            .put("minimumProgress", positions.minOrNull()).put("maximumProgress", positions.maxOrNull())
            .put("rangeMaximum", rangeMaximum).put("gateProposals", proposals)
            .put("rangeSamples", rangeFrames).put("maxRanges", maxRanges)
            .put("rangeClasses", org.json.JSONArray(rangeClasses.sorted()))
            .put("realGestures", 0).toString()
    }
}
