package com.attentionstats.detectors

import org.attentionstats.reels.PageCategory
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface
import org.attentionstats.reels.ReelRoute

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
    val inlineAnchors: List<InlineAnchor> = emptyList(),
    val inlineComplete: Boolean = true,
    val inlineContext: Boolean = false,
    val inlineRootPage: Int? = null,
)

/** In-memory Android node keys are scope-local layout evidence, never persisted identities. */
internal data class InlineAnchor(val id: String, val key: Long, val bounds: LayoutBounds, val selected: Boolean)
internal data class InlineSelection(val item: InlineAnchor?, val indicator: InlineAnchor?, val photo: Boolean = false)

internal data class LayoutBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}
internal data class NumericRange(val id: String?, val role: String, val min: Double,
    val max: Double, val current: Double, val bounds: LayoutBounds)
internal enum class NodeRole { VIEW, BUTTON, RECYCLER, OTHER }
internal data class InterfaceLabel(val token: String, val role: NodeRole, val bounds: LayoutBounds,
    val selected: Boolean, val clickable: Boolean, val key: Long = 0)
internal data class ScrollEvidence(val sourceId: String?, val role: NodeRole, val bounds: LayoutBounds,
    val from: Int, val to: Int, val items: Int)

internal data class SurfaceObservation(
    val surface: Surface,
    val page: Long? = null,
    val category: PageCategory = PageCategory.UNCLASSIFIED,
    val route: ReelRoute = ReelRoute.VIEWER,
    val freshEntry: Boolean = false,
)

internal interface SurfaceDetector {
    val definition: DetectorDefinition
    /** Static layout anchors retained only during the separately consented temporary check. */
    val diagnosticAnchorIds: Set<String>
    val usesInterfaceLabels: Boolean get() = false
    val usesContentIdentity: Boolean get() = false
    val inlineAnchorIds: Set<String> get() = emptySet()
    val inlineRootPagerIds: Set<String> get() = emptySet()
    fun acceptsScroll(evidence: ScrollEvidence): Boolean = evidence.sourceId in definition.scrollSourceIds
    fun acceptsInlineScroll(evidence: ScrollEvidence): Boolean = false
    fun inlineSelection(evidence: SurfaceEvidence): InlineSelection? = null
    fun detect(evidence: SurfaceEvidence): SurfaceObservation
}
