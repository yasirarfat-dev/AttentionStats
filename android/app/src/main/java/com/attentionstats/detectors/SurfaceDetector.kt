package com.attentionstats.detectors

import org.attentionstats.reels.PageCategory
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface

/** Version admission and normalized evidence only; no Android nodes, storage, UI or actions. */
internal data class DetectorDefinition(
    val app: ReelApp,
    val versionName: String,
    val revision: Int,
    val scrollSourceIds: Set<String>,
)

internal data class SurfaceEvidence(
    val ids: Set<String>,
    val bounded: Boolean,
    val editable: Boolean,
    val settledPage: Long?,
)

internal data class SurfaceObservation(
    val surface: Surface,
    val page: Long? = null,
    val category: PageCategory = PageCategory.UNCLASSIFIED,
)

internal interface SurfaceDetector {
    val definition: DetectorDefinition
    /** Static layout anchors retained only during the separately consented temporary check. */
    val diagnosticAnchorIds: Set<String>
    fun detect(evidence: SurfaceEvidence): SurfaceObservation
}
