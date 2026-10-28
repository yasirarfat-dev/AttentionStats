package com.attentionstats.detectors.social.tiktok

import com.attentionstats.detectors.DetectorDefinition
import com.attentionstats.detectors.SurfaceDetector
import com.attentionstats.detectors.SurfaceEvidence
import com.attentionstats.detectors.SurfaceObservation
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface

/** Caption transitions are partial encounter estimates, never unique videos or playback. */
internal object TikTokDetector : SurfaceDetector {
    override val definition = DetectorDefinition(ReelApp.TIKTOK, "47.5.3", 1, emptySet())
    private val required = setOf("viewpager", "long_press_layout", "user_avatar", "desc")
    override val diagnosticAnchorIds = required + "title"
    override val usesContentIdentity = true
    override fun detect(evidence: SurfaceEvidence): SurfaceObservation = when {
        evidence.editable -> SurfaceObservation(Surface.EXCLUDED)
        !evidence.bounded -> SurfaceObservation(Surface.UNKNOWN)
        !evidence.ids.containsAll(required) -> SurfaceObservation(Surface.OUTSIDE)
        else -> SurfaceObservation(Surface.REELS, evidence.settledPage)
    }
}
