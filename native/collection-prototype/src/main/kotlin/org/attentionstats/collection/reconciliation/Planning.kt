package org.attentionstats.collection.reconciliation

/** Limits are synthetic inputs, not accepted device budgets. */
data class PlanLimits(val maxSliceMs: Long = 60_000, val maxSteps: Int = 64, val maxScopeEntries: Int = 256) {
    init { require(maxSliceMs > 0 && maxSteps in 1..256 && maxScopeEntries in 1..1024) }
}

enum class Exclusion { NOT_PERMITTED, FENCED }
sealed interface PlanStep {
    val window: Window
    data class Slice(override val window: Window) : PlanStep
    data class Excluded(override val window: Window, val reason: Exclusion) : PlanStep
}

sealed interface PlanResult {
    /** nextPlanningAtMs is pagination through a proposal, never a processed-history cursor. */
    data class Page(val scope: Scope, val requested: Window, val steps: List<PlanStep>, val nextPlanningAtMs: Long) : PlanResult
    data object Gated : PlanResult
    data object InputLimit : PlanResult
}

/** Partition a pinned proposal at every permission/fence boundary, then at the slice limit. */
fun planSlices(scope: Scope, requested: Window, limits: PlanLimits = PlanLimits()): PlanResult {
    if (scope.packages.size > limits.maxScopeEntries ||
        scope.permitted.size.toLong() + scope.fences.size > limits.maxScopeEntries
    ) return PlanResult.InputLimit
    val captured = scope.snapshot()
    if (!captured.enabled || !captured.granted || !captured.firstUnlocked || captured.packages.isEmpty()) {
        return PlanResult.Gated
    }
    val boundaries = (captured.permitted + captured.fences).flatMap { listOf(it.startMs, it.endMs) }
        .filter { it > requested.startMs && it < requested.endMs }.plus(requested.endMs).distinct().sorted()
    val steps = mutableListOf<PlanStep>()
    var position = requested.startMs
    for (boundary in boundaries) {
        while (position < boundary && steps.size < limits.maxSteps) {
            val atom = Window(position, boundary)
            val step = when {
                captured.fences.any { it.overlaps(atom) } -> PlanStep.Excluded(atom, Exclusion.FENCED)
                !captured.permits(atom) -> PlanStep.Excluded(atom, Exclusion.NOT_PERMITTED)
                else -> PlanStep.Slice(Window(position, position + minOf(boundary - position, limits.maxSliceMs)))
            }
            steps += step
            position = step.window.endMs
        }
        if (steps.size == limits.maxSteps) break
    }
    return PlanResult.Page(captured, requested, steps.toList(), position)
}

data class RetryLimits(
    val maxAttempts: Int = 16,
    val minSliceMs: Long = 1,
    val maxTransientRetries: Int = 2,
    val initialBackoffMs: Long = 100,
    val maxBackoffMs: Long = 1000,
    val runDeadlineMs: Long = 10_000,
) {
    init {
        require(maxAttempts in 1..128 && minSliceMs > 0 && maxTransientRetries in 0..8 &&
            initialBackoffMs in 1..maxBackoffMs && maxBackoffMs <= 60_000 && runDeadlineMs > 0)
    }
}

enum class RetryStop { CANCELLED, SCOPE_CHANGED, CLOCK_ANOMALY, RUN_DEADLINE, ATTEMPT_LIMIT, DENSE_LIMIT, NON_RETRYABLE, PROTOCOL }
sealed interface NextSlice {
    data class Attempt(val window: Window, val number: Int) : NextSlice
    data class Waiting(val remainingMs: Long) : NextSlice
    data object Done : NextSlice
    data class Halted(val reason: RetryStop, val unresolved: List<Window>, val sourceReason: Stop? = null) : NextSlice
}

/**
 * In-memory decision ledger for ONE permitted planned slice. It performs no query, sleep or commit.
 * A run-wide attempt/deadline budget is shared by both children of every density split.
 * Completed child commits stand independently; Done says nothing about excluded time or coverage.
 */
class SliceRun(window: Window, scope: Scope, private val limits: RetryLimits, private val startedAtMs: Long) {
    private val captured = scope.snapshot()
    private val pending = mutableListOf(window)
    private var issued: NextSlice.Attempt? = null
    private var attempts = 0
    private var transientRetries = 0
    private var readyAtMs = startedAtMs
    private var lastElapsedMs = startedAtMs
    private var terminal: NextSlice? = null

    init { require(startedAtMs >= 0 && captured.permits(window)) }

    private fun halt(reason: RetryStop, sourceReason: Stop? = null): NextSlice.Halted =
        NextSlice.Halted(reason, pending.toList(), sourceReason).also { terminal = it; issued = null }

    private fun validClock(elapsedMs: Long): Boolean {
        if (elapsedMs < lastElapsedMs) { halt(RetryStop.CLOCK_ANOMALY); return false }
        lastElapsedMs = elapsedMs
        return true
    }

    fun next(scope: Scope, elapsedMs: Long, cancelled: Boolean = false): NextSlice {
        terminal?.let { return it }
        if (cancelled) return halt(RetryStop.CANCELLED)
        if (scope.snapshot() != captured) return halt(RetryStop.SCOPE_CHANGED)
        if (!validClock(elapsedMs)) return terminal!!
        if (elapsedMs - startedAtMs >= limits.runDeadlineMs) return halt(RetryStop.RUN_DEADLINE)
        if (pending.isEmpty()) return NextSlice.Done.also { terminal = it }
        issued?.let { return it }
        if (attempts >= limits.maxAttempts) return halt(RetryStop.ATTEMPT_LIMIT)
        if (elapsedMs < readyAtMs) return NextSlice.Waiting(readyAtMs - elapsedMs)
        return NextSlice.Attempt(pending.first(), ++attempts).also { issued = it }
    }

    /** Only a caller-qualified SOURCE_FAILED may back off; denial/null/etc never auto-retry. */
    fun complete(attempt: NextSlice.Attempt, outcome: Outcome, elapsedMs: Long, retryableSourceFailure: Boolean = false) {
        if (terminal != null) return
        if (issued !== attempt) { halt(RetryStop.PROTOCOL); return }
        if (!validClock(elapsedMs)) return
        issued = null
        when (outcome) {
            is Outcome.Committed -> {
                if (outcome.batch.window != attempt.window || outcome.batch.completedThroughMs < attempt.window.endMs) {
                    halt(RetryStop.PROTOCOL); return
                }
                pending.removeAt(0)
                transientRetries = 0
                readyAtMs = elapsedMs
            }
            is Outcome.Stopped -> when {
                outcome.reason == Stop.TRUNCATED -> {
                    val width = attempt.window.endMs - attempt.window.startMs
                    if (width / 2 < limits.minSliceMs) { halt(RetryStop.DENSE_LIMIT, outcome.reason); return }
                    val middle = attempt.window.startMs + width / 2
                    pending.removeAt(0)
                    pending.add(0, Window(middle, attempt.window.endMs))
                    pending.add(0, Window(attempt.window.startMs, middle))
                    transientRetries = 0
                    readyAtMs = elapsedMs
                }
                outcome.reason == Stop.SOURCE_FAILED && retryableSourceFailure && transientRetries < limits.maxTransientRetries -> {
                    var delay = limits.initialBackoffMs
                    repeat(transientRetries) { delay = minOf(limits.maxBackoffMs, delay * 2) }
                    if (elapsedMs > Long.MAX_VALUE - delay) { halt(RetryStop.CLOCK_ANOMALY, outcome.reason); return }
                    readyAtMs = elapsedMs + delay
                    transientRetries++
                }
                else -> halt(RetryStop.NON_RETRYABLE, outcome.reason)
            }
        }
    }
}
