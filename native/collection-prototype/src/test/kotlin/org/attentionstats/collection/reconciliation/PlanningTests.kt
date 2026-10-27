package org.attentionstats.collection.reconciliation

import kotlin.random.Random

private val PLAN_SCOPE = Scope("g", "i", "s", 1, true, true, true, setOf("com.example.fixture"), listOf(Window(0, 100)))
private fun expect(expected: Any?, actual: Any?) { check(expected == actual) { "Expected <$expected>, actual <$actual>" } }
private fun page(scope: Scope = PLAN_SCOPE, window: Window = Window(0, 100), limits: PlanLimits = PlanLimits()) =
    planSlices(scope, window, limits) as PlanResult.Page
private fun success(window: Window) = Outcome.Committed(Batch(window, emptyList(), window.endMs, 1000,
    Correspondence.FIRST_OBSERVATION, false))

fun planningChecks(): Int {
    var passed = 0
    fun test(name: String, body: () -> Unit) {
        try { body(); passed++; println("PASS $name") }
        catch (failure: Throwable) { throw AssertionError("FAIL $name", failure) }
    }
    test("planner preserves explicit preconsent pause and deletion intervals") {
        val scope = PLAN_SCOPE.copy(permitted = listOf(Window(10, 40), Window(60, 90)), fences = listOf(Window(20, 30)))
        expect(listOf(
            PlanStep.Excluded(Window(0, 10), Exclusion.NOT_PERMITTED), PlanStep.Slice(Window(10, 20)),
            PlanStep.Excluded(Window(20, 30), Exclusion.FENCED), PlanStep.Slice(Window(30, 40)),
            PlanStep.Excluded(Window(40, 60), Exclusion.NOT_PERMITTED), PlanStep.Slice(Window(60, 90)),
            PlanStep.Excluded(Window(90, 100), Exclusion.NOT_PERMITTED),
        ), page(scope).steps)
        // The page has no processed-history, health, coverage or metric-zero output.
    }
    test("planner clips fences and overlapping unsorted consent without bridging originals") {
        val scope = PLAN_SCOPE.copy(permitted = listOf(Window(40, 100), Window(0, 50), Window(0, 50)),
            fences = listOf(Window(90, 110), Window(10, 20), Window(15, 25)))
        val planned = page(scope)
        expect(100L, planned.nextPlanningAtMs)
        planned.steps.filterIsInstance<PlanStep.Slice>().forEach { check(scope.permits(it.window)) }
        expect(listOf(Window(0, 10), Window(25, 40), Window(40, 50), Window(50, 90)),
            planned.steps.filterIsInstance<PlanStep.Slice>().map { it.window })
    }
    test("planner pagination is bounded and its position does not imply execution") {
        val limits = PlanLimits(maxSliceMs = 13, maxSteps = 2)
        val first = page(limits = limits)
        expect(listOf(PlanStep.Slice(Window(0, 13)), PlanStep.Slice(Window(13, 26))), first.steps)
        expect(26L, first.nextPlanningAtMs)
        val second = page(window = Window(first.nextPlanningAtMs, 100), limits = limits)
        expect(Window(26, 39), second.steps.first().window)
    }
    test("disabled grant unlock and empty package gates emit no plan") {
        for (scope in listOf(PLAN_SCOPE.copy(enabled = false), PLAN_SCOPE.copy(granted = false),
            PLAN_SCOPE.copy(firstUnlocked = false), PLAN_SCOPE.copy(packages = emptySet()))) {
            expect(PlanResult.Gated, planSlices(scope, Window(0, 100)))
        }
    }
    test("all excluded ranges remain explicit and wholly fenced proposals have no slices") {
        expect(listOf(PlanStep.Excluded(Window(0, 100), Exclusion.NOT_PERMITTED)),
            page(PLAN_SCOPE.copy(permitted = emptyList())).steps)
        expect(listOf(PlanStep.Excluded(Window(0, 100), Exclusion.FENCED)),
            page(PLAN_SCOPE.copy(fences = listOf(Window(0, 200)))).steps)
    }
    test("scope entry limits reject rather than silently dropping fences or packages") {
        val limits = PlanLimits(maxScopeEntries = 2)
        expect(PlanResult.InputLimit, planSlices(PLAN_SCOPE.copy(fences = List(2) { Window(1, 2) }), Window(0, 100), limits))
        expect(PlanResult.InputLimit, planSlices(PLAN_SCOPE.copy(packages = setOf("a", "b", "c")), Window(0, 100), limits))
        check(planSlices(PLAN_SCOPE.copy(fences = listOf(Window(1, 2))), Window(0, 100), limits) is PlanResult.Page)
    }
    test("planning uses captured collection values") {
        val ranges = mutableListOf(Window(0, 100))
        val packages = mutableSetOf("com.example.fixture")
        val scope = PLAN_SCOPE.copy(permitted = ranges, packages = packages)
        val planned = page(scope)
        ranges.clear(); packages.clear()
        check(planned.scope.permits(Window(0, 100))); expect(PLAN_SCOPE, planned.scope)
    }
    test("planner handles Long maximum without end arithmetic overflow") {
        val scope = PLAN_SCOPE.copy(permitted = listOf(Window(0, Long.MAX_VALUE)))
        val result = page(scope, Window(Long.MAX_VALUE - 9, Long.MAX_VALUE), PlanLimits(maxSliceMs = 4))
        expect(listOf(4L, 4L, 1L), result.steps.map { it.window.endMs - it.window.startMs })
        expect(Long.MAX_VALUE, result.nextPlanningAtMs)
    }
    test("300 deterministic interval fixtures match an independent millisecond oracle") {
        repeat(300) { seed ->
            val random = Random(seed)
            fun intervals() = List(random.nextInt(0, 7)) {
                val start = random.nextInt(0, 40).toLong()
                Window(start, random.nextLong(start + 1, 51))
            }
            val scope = PLAN_SCOPE.copy(permitted = intervals(), fences = intervals())
            val limits = PlanLimits(maxSliceMs = random.nextLong(1, 12), maxSteps = random.nextInt(1, 5))
            val all = mutableListOf<PlanStep>()
            var start = 0L
            var pages = 0
            while (start < 50) {
                val result = page(scope, Window(start, 50), limits)
                check(result.steps.size in 1..limits.maxSteps)
                check(result.nextPlanningAtMs > start && result.nextPlanningAtMs <= 50)
                all += result.steps; start = result.nextPlanningAtMs
                check(++pages <= 50)
            }
            var position = 0L
            for (step in all) {
                expect(position, step.window.startMs)
                if (step is PlanStep.Slice) {
                    check(scope.permits(step.window)); check(step.window.endMs - step.window.startMs <= limits.maxSliceMs)
                }
                for (at in step.window.startMs until step.window.endMs) {
                    val fenced = scope.fences.any { at >= it.startMs && at < it.endMs }
                    val allowed = scope.permitted.any { at >= it.startMs && at < it.endMs } && !fenced
                    expect(allowed, step is PlanStep.Slice)
                    if (step is PlanStep.Excluded) expect(if (fenced) Exclusion.FENCED else Exclusion.NOT_PERMITTED, step.reason)
                }
                position = step.window.endMs
            }
            expect(50L, position)
        }
    }
    test("retry dispatch is idempotent until the issued attempt completes") {
        val run = SliceRun(Window(0, 100), PLAN_SCOPE, RetryLimits(), 0)
        val attempt = run.next(PLAN_SCOPE, 0) as NextSlice.Attempt
        check(attempt === run.next(PLAN_SCOPE, 1))
        run.complete(attempt, success(attempt.window), 1)
        expect(NextSlice.Done, run.next(PLAN_SCOPE, 2))
    }
    test("density splits are left first and share one attempt budget") {
        val run = SliceRun(Window(0, 9), PLAN_SCOPE, RetryLimits(maxAttempts = 3), 0)
        val first = run.next(PLAN_SCOPE, 0) as NextSlice.Attempt
        run.complete(first, Outcome.Stopped(Stop.TRUNCATED), 0)
        val left = run.next(PLAN_SCOPE, 0) as NextSlice.Attempt
        expect(Window(0, 4), left.window); expect(2, left.number)
        run.complete(left, success(left.window), 0)
        val right = run.next(PLAN_SCOPE, 0) as NextSlice.Attempt
        expect(Window(4, 9), right.window); expect(3, right.number)
        run.complete(right, Outcome.Stopped(Stop.TRUNCATED), 0)
        val stopped = run.next(PLAN_SCOPE, 0) as NextSlice.Halted
        expect(RetryStop.ATTEMPT_LIMIT, stopped.reason)
        expect(listOf(Window(4, 6), Window(6, 9)), stopped.unresolved)
    }
    test("one millisecond and minimum-size dense groups stop without skipping time") {
        for ((window, minimum) in listOf(Window(10, 11) to 1L, Window(10, 17) to 4L)) {
            val run = SliceRun(window, PLAN_SCOPE, RetryLimits(minSliceMs = minimum), 0)
            val attempt = run.next(PLAN_SCOPE, 0) as NextSlice.Attempt
            run.complete(attempt, Outcome.Stopped(Stop.TRUNCATED), 0)
            val stop = run.next(PLAN_SCOPE, 0) as NextSlice.Halted
            expect(RetryStop.DENSE_LIMIT, stop.reason); expect(listOf(window), stop.unresolved)
        }
    }
    test("only qualified failures use exponential capped completion-relative backoff") {
        val run = SliceRun(Window(0, 100), PLAN_SCOPE,
            RetryLimits(maxTransientRetries = 4, initialBackoffMs = 10, maxBackoffMs = 25), 0)
        var now = 0L
        for (delay in listOf(10L, 20L, 25L, 25L)) {
            val attempt = run.next(PLAN_SCOPE, now) as NextSlice.Attempt
            now += 7 // failed work takes time; delay begins at completion
            run.complete(attempt, Outcome.Stopped(Stop.SOURCE_FAILED), now, retryableSourceFailure = true)
            expect(NextSlice.Waiting(delay), run.next(PLAN_SCOPE, now))
            expect(NextSlice.Waiting(1), run.next(PLAN_SCOPE, now + delay - 1))
            now += delay
        }
        val last = run.next(PLAN_SCOPE, now) as NextSlice.Attempt
        run.complete(last, Outcome.Stopped(Stop.SOURCE_FAILED), now, retryableSourceFailure = true)
        val stop = run.next(PLAN_SCOPE, now) as NextSlice.Halted
        expect(RetryStop.NON_RETRYABLE, stop.reason); expect(Stop.SOURCE_FAILED, stop.sourceReason)
    }
    test("all other stop reasons terminate without query or permission retry loops") {
        for (reason in Stop.entries.filter { it != Stop.TRUNCATED }) {
            val run = SliceRun(Window(0, 100), PLAN_SCOPE, RetryLimits(), 0)
            val attempt = run.next(PLAN_SCOPE, 0) as NextSlice.Attempt
            // Even a caller flag cannot make denial, unavailable or a deadline retryable.
            run.complete(attempt, Outcome.Stopped(reason), 0, retryableSourceFailure = reason != Stop.SOURCE_FAILED)
            val stop = run.next(PLAN_SCOPE, 0) as NextSlice.Halted
            expect(RetryStop.NON_RETRYABLE, stop.reason); expect(reason, stop.sourceReason)
        }
    }
    test("waiting consumes no attempts and cannot bypass a run deadline") {
        val run = SliceRun(Window(0, 100), PLAN_SCOPE, RetryLimits(runDeadlineMs = 50), 0)
        val attempt = run.next(PLAN_SCOPE, 0) as NextSlice.Attempt
        run.complete(attempt, Outcome.Stopped(Stop.SOURCE_FAILED), 0, true)
        repeat(49) { expect(NextSlice.Waiting(100L - it), run.next(PLAN_SCOPE, it.toLong())) }
        expect(RetryStop.RUN_DEADLINE, (run.next(PLAN_SCOPE, 50) as NextSlice.Halted).reason)
    }
    test("cancellation and scope change fence queued children and backoff") {
        for (waiting in listOf(false, true)) for (cancel in listOf(false, true)) {
            val run = SliceRun(Window(0, 100), PLAN_SCOPE, RetryLimits(), 0)
            val attempt = run.next(PLAN_SCOPE, 0) as NextSlice.Attempt
            run.complete(attempt, Outcome.Stopped(if (waiting) Stop.SOURCE_FAILED else Stop.TRUNCATED), 0, true)
            val changed = if (cancel) PLAN_SCOPE else PLAN_SCOPE.copy(consentRevision = 2)
            val stop = run.next(changed, 0, cancelled = cancel) as NextSlice.Halted
            expect(if (cancel) RetryStop.CANCELLED else RetryStop.SCOPE_CHANGED, stop.reason)
        }
    }
    test("run deadline spans parent and completed children without resetting") {
        val run = SliceRun(Window(0, 100), PLAN_SCOPE, RetryLimits(runDeadlineMs = 10), 0)
        val parent = run.next(PLAN_SCOPE, 0) as NextSlice.Attempt
        run.complete(parent, Outcome.Stopped(Stop.TRUNCATED), 3)
        val left = run.next(PLAN_SCOPE, 3) as NextSlice.Attempt
        run.complete(left, success(left.window), 7)
        val halt = run.next(PLAN_SCOPE, 10) as NextSlice.Halted
        expect(RetryStop.RUN_DEADLINE, halt.reason); expect(listOf(Window(50, 100)), halt.unresolved)
    }
    test("128 density-budget fixtures retain an exact unresolved partition and bounded attempts") {
        for (limit in 1..128) {
            val root = Window(0, Long.MAX_VALUE)
            val scope = PLAN_SCOPE.copy(permitted = listOf(root))
            val run = SliceRun(root, scope, RetryLimits(maxAttempts = limit), 0)
            var dispatched = 0
            var decision = run.next(scope, 0)
            while (decision is NextSlice.Attempt) {
                check(++dispatched <= limit)
                run.complete(decision, Outcome.Stopped(Stop.TRUNCATED), 0)
                decision = run.next(scope, 0)
            }
            val stop = decision as NextSlice.Halted
            check(stop.reason in setOf(RetryStop.ATTEMPT_LIMIT, RetryStop.DENSE_LIMIT))
            check(stop.unresolved.size <= dispatched + 1)
            var position = 0L
            for (window in stop.unresolved) { expect(position, window.startMs); position = window.endMs }
            expect(Long.MAX_VALUE, position)
        }
    }
    test("a delayed completion after cancellation cannot revive a terminal ledger") {
        val run = SliceRun(Window(0, 100), PLAN_SCOPE, RetryLimits(), 0)
        val attempt = run.next(PLAN_SCOPE, 0) as NextSlice.Attempt
        val stopped = run.next(PLAN_SCOPE, 0, cancelled = true)
        run.complete(attempt, success(attempt.window), 1)
        expect(stopped, run.next(PLAN_SCOPE, 1))
        // The ledger cannot undo an external commit: the source/commit ports still enforce withdrawal.
    }
    test("clock regression and backoff overflow terminate safely") {
        val regression = SliceRun(Window(0, 100), PLAN_SCOPE, RetryLimits(), 10)
        expect(RetryStop.CLOCK_ANOMALY, (regression.next(PLAN_SCOPE, 9) as NextSlice.Halted).reason)
        val run = SliceRun(Window(0, 100), PLAN_SCOPE, RetryLimits(), Long.MAX_VALUE - 1)
        val attempt = run.next(PLAN_SCOPE, Long.MAX_VALUE - 1) as NextSlice.Attempt
        run.complete(attempt, Outcome.Stopped(Stop.SOURCE_FAILED), Long.MAX_VALUE - 1, true)
        expect(RetryStop.CLOCK_ANOMALY, (run.next(PLAN_SCOPE, Long.MAX_VALUE) as NextSlice.Halted).reason)
    }
    test("stale forged or mismatched completion cannot consume queued work") {
        for (mode in 0..2) {
            val run = SliceRun(Window(0, 100), PLAN_SCOPE, RetryLimits(), 0)
            val attempt = run.next(PLAN_SCOPE, 0) as NextSlice.Attempt
            when (mode) {
                0 -> run.complete(attempt.copy(), success(attempt.window), 0)
                1 -> run.complete(attempt, success(Window(0, 50)), 0)
                else -> {
                    run.complete(attempt, Outcome.Stopped(Stop.TRUNCATED), 0)
                    run.next(PLAN_SCOPE, 0)
                    run.complete(attempt, success(attempt.window), 0)
                }
            }
            expect(RetryStop.PROTOCOL, (run.next(PLAN_SCOPE, 0) as NextSlice.Halted).reason)
        }
    }
    test("run captures scope and terminal decisions remain terminal") {
        val packages = mutableSetOf("com.example.fixture")
        val scope = PLAN_SCOPE.copy(packages = packages)
        val run = SliceRun(Window(0, 100), scope, RetryLimits(), 0)
        packages.clear()
        val stop = run.next(scope, 0) as NextSlice.Halted
        expect(RetryStop.SCOPE_CHANGED, stop.reason)
        expect(stop, run.next(PLAN_SCOPE, 100))
    }
    test("invalid budgets and a fenced root are refused at construction") {
        val constructions: List<() -> Any> = listOf(
            { PlanLimits(maxSteps = 0) }, { PlanLimits(maxScopeEntries = 1025) }, { PlanLimits(maxSliceMs = 0) },
            { RetryLimits(maxAttempts = 129) }, { RetryLimits(minSliceMs = 0) }, { RetryLimits(maxTransientRetries = 9) },
            { RetryLimits(initialBackoffMs = 2, maxBackoffMs = 1) }, { RetryLimits(runDeadlineMs = 0) },
            { SliceRun(Window(0, 100), PLAN_SCOPE.copy(fences = listOf(Window(10, 11))), RetryLimits(), 0) },
        )
        for (construct in constructions) {
            var rejected = false
            try { construct() } catch (_: IllegalArgumentException) { rejected = true }
            check(rejected)
        }
    }
    return passed
}
