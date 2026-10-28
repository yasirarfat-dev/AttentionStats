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
    val viewport: LayoutBounds = LayoutBounds(0, 0, 0, 0),
    val interfaceLabels: List<InterfaceLabel> = emptyList(),
    val scroll: ScrollEvidence? = null,
)

internal data class LayoutBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}
internal enum class NodeRole { VIEW, BUTTON, RECYCLER, OTHER }
internal data class InterfaceLabel(val token: String, val role: NodeRole, val bounds: LayoutBounds,
    val selected: Boolean, val clickable: Boolean)
internal data class ScrollEvidence(val sourceId: String?, val role: NodeRole, val bounds: LayoutBounds,
    val from: Int, val to: Int, val items: Int)

internal data class SurfaceObservation(
    val surface: Surface,
    val page: Long? = null,
    val category: PageCategory = PageCategory.UNCLASSIFIED,
)

internal interface SurfaceDetector {
    val definition: DetectorDefinition
    /** Static layout anchors retained only during the separately consented temporary check. */
    val diagnosticAnchorIds: Set<String>
    val usesInterfaceLabels: Boolean get() = false
    val usesContentIdentity: Boolean get() = false
    fun acceptsScroll(evidence: ScrollEvidence): Boolean = evidence.sourceId in definition.scrollSourceIds
    fun detect(evidence: SurfaceEvidence): SurfaceObservation
}
