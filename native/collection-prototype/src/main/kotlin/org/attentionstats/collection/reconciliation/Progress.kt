package org.attentionstats.collection.reconciliation

/** Experimental receipts, not metric coverage, durable schema or permission to backfill. */
sealed interface RangeReceipt {
    val window: Window
    data class Queried(override val window: Window, val queryCompletedAtMs: Long) : RangeReceipt
    data class Excluded(override val window: Window, val reason: Exclusion) : RangeReceipt
}

data class ProgressLimits(val maxReceipts: Int = 256, val maxScopeEntries: Int = 256) {
    init { require(maxReceipts in 1..1024 && maxScopeEntries in 1..1024) }
}

/** Coordinator-owned snapshot; receipts retain their original scope and never become a scalar cursor. */
data class RangeProgress(val scope: Scope, val requested: Window, val revision: Long, val receipts: List<RangeReceipt>)
enum class ProgressStop { SCOPE_CHANGED, GATED, INPUT_LIMIT, INVALID_STATE, CONFLICT, INVALID_RECEIPT, REVISION_LIMIT }
sealed interface Recovery {
    data class Ready(val snapshot: RangeProgress, val unresolved: List<Window>) : Recovery
    data class Stopped(val reason: ProgressStop) : Recovery
}

private fun Scope.within(limits: ProgressLimits): Boolean = packages.size <= limits.maxScopeEntries &&
    permitted.size.toLong() + fences.size <= limits.maxScopeEntries

private fun validReceipt(scope: Scope, receipt: RangeReceipt, limits: ProgressLimits): Boolean = when (receipt) {
    is RangeReceipt.Queried -> scope.permits(receipt.window) && receipt.queryCompletedAtMs >= receipt.window.endMs
    is RangeReceipt.Excluded -> {
        val page = planSlices(scope, receipt.window, PlanLimits(Long.MAX_VALUE, 256, limits.maxScopeEntries))
        page is PlanResult.Page && page.nextPlanningAtMs == receipt.window.endMs &&
            page.steps.all { it is PlanStep.Excluded && it.reason == receipt.reason }
    }
}

/** Bounded restart calculation from accepted receipts only. No speculative tickets/retries are restored. */
fun recoverProgress(state: RangeProgress, currentScope: Scope, limits: ProgressLimits = ProgressLimits()): Recovery {
    if (state.receipts.size > limits.maxReceipts || !state.scope.within(limits) || !currentScope.within(limits)) {
        return Recovery.Stopped(ProgressStop.INPUT_LIMIT)
    }
    val captured = state.copy(scope = state.scope.snapshot(), receipts = state.receipts.toList())
    if (captured.scope != currentScope.snapshot()) return Recovery.Stopped(ProgressStop.SCOPE_CHANGED)
    if (captured.revision < 0) return Recovery.Stopped(ProgressStop.INVALID_STATE)
    val unresolved = mutableListOf<Window>()
    var position = captured.requested.startMs
    for (receipt in captured.receipts.sortedBy { it.window.startMs }) {
        if (!captured.requested.contains(receipt.window) || receipt.window.startMs < position ||
            !validReceipt(captured.scope, receipt, limits)
        ) return Recovery.Stopped(ProgressStop.INVALID_STATE)
        if (position < receipt.window.startMs) unresolved += Window(position, receipt.window.startMs)
        position = receipt.window.endMs
    }
    if (position < captured.requested.endMs) unresolved += Window(position, captured.requested.endMs)
    return Recovery.Ready(captured, unresolved.toList())
}

/** A proposal pinned to coordinator scope/revision. Issuing it mutates nothing. */
data class ProgressTicket(val scope: Scope, val requested: Window, val revision: Long, val step: PlanStep)
sealed interface ProgressNext {
    data class Work(val ticket: ProgressTicket) : ProgressNext
    data object Done : ProgressNext
    data class Stopped(val reason: ProgressStop) : ProgressNext
}

fun nextProgress(state: RangeProgress, currentScope: Scope, maxSliceMs: Long = 60_000,
                 limits: ProgressLimits = ProgressLimits()): ProgressNext {
    require(maxSliceMs > 0)
    val recovery = recoverProgress(state, currentScope, limits)
    if (recovery is Recovery.Stopped) return ProgressNext.Stopped(recovery.reason)
    recovery as Recovery.Ready
    val gap = recovery.unresolved.firstOrNull() ?: return ProgressNext.Done
    if (state.receipts.size == limits.maxReceipts) return ProgressNext.Stopped(ProgressStop.INPUT_LIMIT)
    if (state.revision == Long.MAX_VALUE) return ProgressNext.Stopped(ProgressStop.REVISION_LIMIT)
    val page = planSlices(recovery.snapshot.scope, gap, PlanLimits(maxSliceMs, 1, limits.maxScopeEntries))
    if (page !is PlanResult.Page) return ProgressNext.Stopped(
        if (page is PlanResult.Gated) ProgressStop.GATED else ProgressStop.INPUT_LIMIT)
    return ProgressNext.Work(ProgressTicket(page.scope, state.requested, state.revision, page.steps.single()))
}

sealed interface ProgressCommit {
    data class Accepted(val snapshot: RangeProgress) : ProgressCommit
    data class Refused(val reason: ProgressStop) : ProgressCommit
}

/**
 * Pure acceptance calculation. A real coordinator must apply this AND facts/health in one transaction.
 * A queried receipt may accept a left density child, never a later sibling or an exclusion as queried.
 */
fun acceptProgress(state: RangeProgress, currentScope: Scope, ticket: ProgressTicket, receipt: RangeReceipt,
                   limits: ProgressLimits = ProgressLimits()): ProgressCommit {
    val recovery = recoverProgress(state, currentScope, limits)
    if (recovery is Recovery.Stopped) return ProgressCommit.Refused(recovery.reason)
    recovery as Recovery.Ready
    if (ticket.scope != recovery.snapshot.scope || ticket.revision != state.revision || ticket.requested != state.requested) {
        return ProgressCommit.Refused(ProgressStop.CONFLICT)
    }
    if (state.revision == Long.MAX_VALUE) return ProgressCommit.Refused(ProgressStop.REVISION_LIMIT)
    if (state.receipts.size == limits.maxReceipts) return ProgressCommit.Refused(ProgressStop.INPUT_LIMIT)
    val gap = recovery.unresolved.firstOrNull()
    val step = ticket.step
    val matches = gap != null && gap.contains(step.window) && step.window.startMs == gap.startMs &&
        receipt.window.startMs == step.window.startMs && when (step) {
            is PlanStep.Slice -> receipt is RangeReceipt.Queried && step.window.contains(receipt.window) &&
                recovery.snapshot.scope.permits(step.window)
            is PlanStep.Excluded -> receipt is RangeReceipt.Excluded && receipt.window == step.window && receipt.reason == step.reason
        }
    if (!matches || !validReceipt(recovery.snapshot.scope, receipt, limits)) {
        return ProgressCommit.Refused(ProgressStop.INVALID_RECEIPT)
    }
    return ProgressCommit.Accepted(recovery.snapshot.copy(revision = state.revision + 1,
        receipts = recovery.snapshot.receipts + receipt))
}
