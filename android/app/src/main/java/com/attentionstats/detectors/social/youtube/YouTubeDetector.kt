package com.attentionstats.detectors.social.youtube

import com.attentionstats.detectors.DetectorDefinition
import com.attentionstats.detectors.SurfaceDetector
import com.attentionstats.detectors.SurfaceEvidence
import com.attentionstats.detectors.SurfaceObservation
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface

/** Version-pinned Shorts encounter candidate; no playback, completion or ad inference. */
internal object YouTubeDetector : SurfaceDetector {
    override val definition = DetectorDefinition(
        ReelApp.YOUTUBE, "21.40.165", 1, setOf("reel_recycler"),
    )
    private val required = setOf("reel_recycler", "reel_player_page_container", "reel_time_bar")
    override val diagnosticAnchorIds = required

    override fun detect(evidence: SurfaceEvidence): SurfaceObservation = when {
        evidence.editable -> SurfaceObservation(Surface.EXCLUDED)
        !evidence.bounded -> SurfaceObservation(Surface.UNKNOWN)
        !evidence.ids.containsAll(required) -> SurfaceObservation(Surface.OUTSIDE)
        "reel_playback_loading_spinner" in evidence.ids -> SurfaceObservation(Surface.REELS)
        else -> SurfaceObservation(Surface.REELS, evidence.settledPage)
    }
}
