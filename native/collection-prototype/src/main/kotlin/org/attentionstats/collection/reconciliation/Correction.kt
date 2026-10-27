package org.attentionstats.collection.reconciliation

/** Event-time bounds only; neither a scheduler cadence nor a platform response/latency guarantee. */
data class CorrectionLimits(val overlapMs: Long = 60_000, val maxPassMs: Long = 600_000) {
    init { require(maxPassMs >= 2 && overlapMs in 1 until maxPassMs) }
}

enum class CorrectionStop { SCOPE_CHANGED, GATED, INPUT_LIMIT, INVALID_STATE, UNFINISHED, TIME_REGRESSION, REVISION_LIMIT }
sealed interface CorrectionResult {
    data class Ready(val progress: RangeProgress) : CorrectionResult
    data class Stopped(val reason: CorrectionStop) : CorrectionResult
}

/**
 * Explicit next-pass proposal, never a write or permission to query. Finish unresolved work first.
 * Old receipts do not suppress overlap reads. Retain facts/health and atomically install this candidate
 * after comparing the previous scope/revision. Revisions must never reset between passes.
 * Changed scope requires independent consent reconstruction; no receipt migration is provided here.
 */
fun beginCorrection(previous: RangeProgress, currentScope: Scope, targetEndMs: Long,
                    limits: CorrectionLimits = CorrectionLimits(),
                    progressLimits: ProgressLimits = ProgressLimits()): CorrectionResult {
    val recovered = recoverProgress(previous, currentScope, progressLimits)
    if (recovered is Recovery.Stopped) return CorrectionResult.Stopped(when (recovered.reason) {
        ProgressStop.SCOPE_CHANGED -> CorrectionStop.SCOPE_CHANGED
        ProgressStop.INPUT_LIMIT -> CorrectionStop.INPUT_LIMIT
        else -> CorrectionStop.INVALID_STATE
    })
    recovered as Recovery.Ready
    val state = recovered.snapshot
    val scope = state.scope
    if (!scope.enabled || !scope.granted || !scope.firstUnlocked || scope.packages.isEmpty()) {
        return CorrectionResult.Stopped(CorrectionStop.GATED)
    }
    if (recovered.unresolved.isNotEmpty()) return CorrectionResult.Stopped(CorrectionStop.UNFINISHED)
    if (targetEndMs < state.requested.endMs) return CorrectionResult.Stopped(CorrectionStop.TIME_REGRESSION)
    if (state.revision == Long.MAX_VALUE) return CorrectionResult.Stopped(CorrectionStop.REVISION_LIMIT)
    val start = maxOf(0, state.requested.endMs - limits.overlapMs)
    val end = start + minOf(targetEndMs - start, limits.maxPassMs)
    return CorrectionResult.Ready(RangeProgress(scope, Window(start, end), state.revision + 1, emptyList()))
}
