package com.attentionstats.detectors.social.facebook

import com.attentionstats.detectors.DetectorDefinition
import com.attentionstats.detectors.NodeRole
import com.attentionstats.detectors.ScrollEvidence
import com.attentionstats.detectors.SurfaceDetector
import com.attentionstats.detectors.SurfaceEvidence
import com.attentionstats.detectors.SurfaceObservation
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface

/** Exact-version English viewer candidate. Generic feed scrolling never admits a surface. */
internal object FacebookDetector : SurfaceDetector {
    override val definition = DetectorDefinition(ReelApp.FACEBOOK, "584.0.0.51.56", 2, emptySet())
    override val diagnosticAnchorIds = emptySet<String>()
    override val usesInterfaceLabels = true
    override fun acceptsScroll(evidence: ScrollEvidence): Boolean =
        evidence.role == NodeRole.RECYCLER &&
            evidence.bounds.width > 0 && evidence.bounds.height > evidence.bounds.width &&
            evidence.from >= 0 && evidence.to >= evidence.from && evidence.items > evidence.to

    override fun detect(evidence: SurfaceEvidence): SurfaceObservation {
        if (evidence.editable) return SurfaceObservation(Surface.EXCLUDED)
        if (!evidence.bounded) return SurfaceObservation(Surface.UNKNOWN)
        val screen = evidence.viewport
        if (screen.width <= 0 || screen.height <= screen.width) return SurfaceObservation(Surface.UNKNOWN)
        if (evidence.interfaceLabels.any { it.token in setOf("close", "comments") })
            return SurfaceObservation(Surface.EXCLUDED)
        val heading = evidence.interfaceLabels.singleOrNull {
            it.token == "reels" && it.role == NodeRole.VIEW && !it.clickable &&
                it.bounds.top >= screen.top && it.bounds.bottom <= screen.top + screen.height / 8 &&
                it.bounds.left >= screen.left && it.bounds.right < screen.left + screen.width / 2
        }
        // Home suggestions open a different viewer chrome: a Back button replaces the heading.
        // Require its top-edge full-screen pager as additional positive evidence, not just Back/Search.
        val back = evidence.interfaceLabels.singleOrNull {
            it.token == "back" && it.role == NodeRole.BUTTON && it.clickable &&
                it.bounds.top >= screen.top && it.bounds.bottom <= screen.top + screen.height / 8 &&
                it.bounds.left >= screen.left && it.bounds.right < screen.left + screen.width / 4
        }
        val anchor = heading ?: back ?: return SurfaceObservation(Surface.OUTSIDE)
        val search = evidence.interfaceLabels.singleOrNull {
            it.token == "search" && it.role == NodeRole.BUTTON && it.clickable &&
                it.bounds.left > screen.left + screen.width / 2 && it.bounds.right <= screen.right &&
                it.bounds.top <= anchor.bounds.bottom && it.bounds.bottom >= anchor.bounds.top
        } ?: return SurfaceObservation(Surface.UNKNOWN)
        val scroll = evidence.scroll ?: return SurfaceObservation(if (heading != null) Surface.REELS else Surface.UNKNOWN)
        val bounds = scroll.bounds
        val fullViewer = acceptsScroll(scroll) && bounds.left == screen.left && bounds.right == screen.right &&
            bounds.top <= search.bounds.top && bounds.bottom > screen.top + screen.height * 4 / 5 &&
            bounds.bottom < screen.bottom
        if (heading == null && (!fullViewer || bounds.top != screen.top))
            return SurfaceObservation(Surface.UNKNOWN)
        val settled = fullViewer && scroll.from == scroll.to && evidence.settledPage == scroll.from.toLong()
        return SurfaceObservation(Surface.REELS, if (settled) evidence.settledPage else null)
        // Categories remain unclassified: a viewer page can contain video, a photo or an advertisement.
    }
}
