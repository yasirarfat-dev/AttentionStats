package org.attentionstats.collection.reconciliation

import kotlin.random.Random

private const val APP = "com.example.fixture"
private const val OTHER = "com.example.excluded"
private val SLICE = Window(0, 100)
private val SCOPE = Scope("generation-1", "installation-1", "usage", 1, true, true, true, setOf(APP), listOf(Window(0, 1000)))
private val CLOCK = Clock { Clocks(2000, 1000) }
private var passed = 0

private fun test(name: String, body: () -> Unit) {
    try { body(); passed++; println("PASS $name") }
    catch (failure: Throwable) { throw AssertionError("FAIL $name", failure) }
}

private fun equal(expected: Any?, actual: Any?) { check(expected == actual) { "Expected <$expected>, actual <$actual>" } }
private fun event(atMs: Long, name: String = "First", transition: Transition = Transition.RESUMED, pkg: String = APP) =
    Signature(atMs, pkg, name, transition)

/** In-memory injected coordinator only; no durable history or production implementation. */
private class FakePort(var currentScope: Scope = SCOPE) : CommitPort {
    var positionMs = 0L
    var revision = 0L
    var queryCompletedAtMs: Long? = null
    var lastBatch: Batch? = null
    var rows: Set<Occurrence> = emptySet()
    var permitCommit = true
    var beforeCommit: () -> Unit = {}
    var overrideRead: ReadState? = null
    var readCount = 0
    var commitAttempts = 0
    override fun scope(): Scope = currentScope
    override fun read(window: Window, maxOccurrences: Int): ReadState {
        readCount++
        return overrideRead ?: ReadState(revision, positionMs,
            rows.filter { window.contains(it.signature.atMs) }.groupingBy { it.signature }.eachCount())
    }
    override fun compareAndCommit(expectedScope: Scope, expectedRevision: Long, batch: Batch): Boolean {
        commitAttempts++
        beforeCommit()
        if (!permitCommit || revision != expectedRevision || currentScope.snapshot() != expectedScope ||
            !currentScope.permits(batch.window)
        ) return false
        val nextRows = rows + batch.additions
        // Single simulated transaction: no partial state changes on refusal.
        rows = nextRows
        positionMs = batch.completedThroughMs
        queryCompletedAtMs = batch.queryCompletedAtMs
        lastBatch = batch
        revision++
        return true
    }
}

private class FixtureSource(val events: List<Signature> = emptyList()) : Source {
    var queryCount = 0
    var nextCount = 0
    var queriedWindow: Window? = null
    var onQuery: () -> Unit = {}
    var afterNext: () -> Unit = {}
    var beforeHasNext: () -> Unit = {}
    var response: Query? = null
    override fun query(window: Window): Query {
        queryCount++
        queriedWindow = window
        onQuery()
        return response ?: Query.Events(object : EventCursor {
            var index = 0
            override fun hasNext(): Boolean { beforeHasNext(); return index < events.size }
            override fun nextInto(buffer: EventBuffer) {
                val value = events[index++]
                buffer.atMs = value.atMs
                buffer.packageName = value.packageName
                buffer.className = value.className
                buffer.transition = value.transition
                nextCount++
                afterNext()
            }
        })
    }
}

private fun run(port: FakePort, source: Source = FixtureSource(), window: Window = SLICE,
                clock: Clock = CLOCK, budget: Budget = Budget(), cancelled: () -> Boolean = { false }): Outcome =
    reconcile(window, source, port, clock, budget, cancelled)

private fun committed(result: Outcome): Batch = (result as Outcome.Committed).batch
private fun stopped(reason: Stop, result: Outcome) = equal(Outcome.Stopped(reason), result)
private fun untouched(port: FakePort) {
    equal(0L, port.positionMs); equal(0L, port.revision); equal(emptySet<Occurrence>(), port.rows)
    equal(null, port.queryCompletedAtMs)
}

fun main() {
    test("identical replay preserves rows and ordinals") {
        val port = FakePort()
        val values = listOf(event(10), event(30, transition = Transition.PAUSED))
        equal(2, committed(run(port, FixtureSource(values))).additions.size)
        val rows = port.rows
        val replay = committed(run(port, FixtureSource(values)))
        equal(emptyList<Occurrence>(), replay.additions); equal(rows, port.rows)
        equal(Correspondence.UNRESOLVED_REPLAY, replay.correspondence)
    }
    test("shifted windows do not depend on query-local index") {
        val port = FakePort()
        run(port, FixtureSource(listOf(event(10), event(90))))
        val old = port.rows
        val batch = committed(run(port, FixtureSource(listOf(event(90), event(120))), Window(50, 150)))
        equal(listOf(Occurrence(event(120), 0)), batch.additions)
        check(port.rows.containsAll(old)); equal(150L, port.positionMs)
    }
    test("equal-time identical occurrences keep multiplicity") {
        val port = FakePort()
        val same = event(10)
        run(port, FixtureSource(listOf(same, same, same)))
        equal(setOf(0, 1, 2), port.rows.map { it.ordinal }.toSet())
        equal(0, committed(run(port, FixtureSource(listOf(same, same, same)))).additions.size)
    }
    test("equal-time group permutations are order independent") {
        val values = listOf(event(10), event(10), event(10, "Second"), event(10, transition = Transition.PAUSED))
        repeat(100) { seed ->
            val port = FakePort()
            run(port, FixtureSource(values.shuffled(Random(seed))))
            val expected = port.rows
            run(port, FixtureSource(values.shuffled(Random(seed + 100))))
            equal(expected, port.rows)
        }
    }
    test("late distinct arrival inside processed overlap is admitted") {
        val port = FakePort()
        run(port, FixtureSource(listOf(event(20))))
        equal(listOf(Occurrence(event(40), 0)), committed(run(port, FixtureSource(listOf(event(20), event(40))))).additions)
    }
    test("late indistinguishable arrival extends observed multiplicity with uncertainty") {
        val port = FakePort()
        run(port, FixtureSource(listOf(event(20))))
        val batch = committed(run(port, FixtureSource(listOf(event(20), event(20)))))
        equal(listOf(Occurrence(event(20), 1)), batch.additions)
        equal(Correspondence.UNRESOLVED_REPLAY, batch.correspondence)
    }
    test("shrinking or missing groups preserve prior facts and flag discrepancy") {
        val port = FakePort()
        run(port, FixtureSource(listOf(event(20), event(20), event(40))))
        val previous = port.rows
        check(committed(run(port, FixtureSource(listOf(event(20))))).multiplicityDecreased)
        equal(previous, port.rows)
        check(committed(run(port)).multiplicityDecreased); equal(previous, port.rows)
    }
    test("indistinguishable replacement cannot establish exact identity") {
        val port = FakePort()
        run(port, FixtureSource(listOf(event(20))))
        // A replaced real event with identical public scalars is observationally indistinguishable.
        val batch = committed(run(port, FixtureSource(listOf(event(20)))))
        equal(Correspondence.UNRESOLVED_REPLAY, batch.correspondence)
        equal(0, batch.additions.size)
    }
    test("mutable iterator scratch is copied before advancing") {
        val port = FakePort()
        val values = listOf(event(10), event(20, "Second", Transition.PAUSED), event(30, "Third", Transition.STOPPED))
        run(port, FixtureSource(values))
        equal(values.toSet(), port.rows.map { it.signature }.toSet())
    }
    test("half-open boundaries accept start and reject end without partial commit") {
        val port = FakePort()
        run(port, FixtureSource(listOf(event(0), event(99))))
        equal(2, port.rows.size)
        val failed = FakePort()
        stopped(Stop.INVALID_EVENT, run(failed, FixtureSource(listOf(event(0), event(100)))))
        untouched(failed)
    }
    test("pre-window event cannot backfill missing start") {
        val port = FakePort(); port.positionMs = 50
        stopped(Stop.INVALID_EVENT, run(port, FixtureSource(listOf(event(49))), Window(50, 100)))
        equal(50L, port.positionMs); equal(0L, port.revision)
    }
    test("package filter rejects excluded events before persistence") {
        val port = FakePort()
        run(port, FixtureSource(listOf(event(10, pkg = OTHER), event(20))))
        equal(setOf(Occurrence(event(20), 0)), port.rows)
    }
    test("unsupported event cannot reuse previous mutable scalar fields") {
        val port = FakePort()
        val source = Source { Query.Events(object : EventCursor {
            var index = 0
            override fun hasNext(): Boolean = index < 3
            override fun nextInto(buffer: EventBuffer) {
                when (index++) {
                    0 -> { buffer.atMs = 10; buffer.packageName = APP; buffer.transition = Transition.RESUMED }
                    1 -> { buffer.atMs = 20; buffer.packageName = APP /* unsupported kind */ }
                    else -> { buffer.atMs = 30 /* absent package and kind */ }
                }
            }
        }) }
        committed(run(port, source))
        equal(setOf(Occurrence(Signature(10, APP, null, Transition.RESUMED), 0)), port.rows)
    }
    test("oversized allowed scalar cannot enter the staged group map") {
        val port = FakePort()
        stopped(Stop.INVALID_EVENT, run(port, FixtureSource(listOf(event(10, "x".repeat(513))))))
        untouched(port)
    }
    test("empty exhausted query updates position and health without metric coverage assertion") {
        val port = FakePort()
        val batch = committed(run(port))
        equal(100L, port.positionMs); equal(2000L, port.queryCompletedAtMs)
        equal(emptyList<Occurrence>(), batch.additions)
        // Batch has query health only: no metric-zero or complete-coverage field.
    }
    for ((name, query, reason) in listOf(
        Triple("null", Query.Unavailable, Stop.UNAVAILABLE),
        Triple("denial", Query.Denied, Stop.DENIED),
        Triple("source failure", Query.Failed, Stop.SOURCE_FAILED),
    )) test("$name preserves state and differs from successful empty") {
        val port = FakePort(); val source = FixtureSource(); source.response = query
        stopped(reason, run(port, source)); untouched(port)
    }
    for ((name, scope) in listOf(
        "disabled" to SCOPE.copy(enabled = false), "no grant" to SCOPE.copy(granted = false),
        "before first unlock" to SCOPE.copy(firstUnlocked = false), "no packages" to SCOPE.copy(packages = emptySet()),
        "before effective consent" to SCOPE.copy(permitted = listOf(Window(10, 1000))),
        "pause" to SCOPE.copy(permitted = listOf(Window(0, 40), Window(60, 1000))),
        "deletion fence" to SCOPE.copy(fences = listOf(Window(30, 40))),
    )) test("$name prevents source query") {
        val port = FakePort(scope); val source = FixtureSource()
        stopped(Stop.GATED, run(port, source)); equal(0, source.queryCount); equal(0, port.readCount); untouched(port)
    }
    test("adjacent deletion fences do not exclude permitted half-open slice") {
        val port = FakePort(SCOPE.copy(fences = listOf(Window(100, 200))))
        committed(run(port)); equal(100L, port.positionMs)
    }
    test("slice and cursor gaps are refused before query") {
        val port = FakePort(); val source = FixtureSource()
        stopped(Stop.INVALID_SLICE, run(port, source, budget = Budget(maxSliceMs = 99)))
        stopped(Stop.INVALID_STATE, run(port, source, window = Window(10, 100)))
        equal(0, source.queryCount); untouched(port)
    }
    test("future end cannot be marked processed") {
        val port = FakePort(SCOPE.copy(permitted = listOf(Window(0, 10_000)))); val source = FixtureSource()
        stopped(Stop.INVALID_SLICE, run(port, source, window = Window(0, 2001)))
        equal(0, source.queryCount); untouched(port)
    }
    test("pinned window is reused even while clocks advance") {
        var tick = 0L
        val source = FixtureSource(listOf(event(10)))
        committed(run(FakePort(), source, clock = Clock { Clocks(2000 + tick, 1000 + tick++) }))
        equal(SLICE, source.queriedWindow)
    }
    test("exact iteration cap may complete but partial equal-time group cannot commit") {
        val port = FakePort()
        committed(run(port, FixtureSource(List(3) { event(10) }), budget = Budget(maxEvents = 3)))
        val truncated = FakePort(); val source = FixtureSource(List(4) { event(10) })
        stopped(Stop.TRUNCATED, run(truncated, source, budget = Budget(maxEvents = 3)))
        equal(3, source.nextCount); untouched(truncated)
        committed(run(truncated, FixtureSource(List(4) { event(10) }), budget = Budget(maxEvents = 4)))
        equal(4, truncated.rows.size)
    }
    test("excluded event flood still consumes raw iteration budget") {
        val port = FakePort(); val source = FixtureSource(List(20) { event(10, pkg = OTHER) })
        stopped(Stop.TRUNCATED, run(port, source, budget = Budget(maxEvents = 3)))
        equal(3, source.nextCount); untouched(port)
    }
    test("truncation after previous commit changes neither facts nor query health") {
        val port = FakePort()
        run(port, FixtureSource(listOf(event(60))))
        val previousRows = port.rows; val previousRevision = port.revision
        val previousHealth = port.queryCompletedAtMs
        stopped(Stop.TRUNCATED, run(port, FixtureSource(List(4) { event(60) }), Window(50, 150), budget = Budget(maxEvents = 3)))
        equal(previousRows, port.rows); equal(previousRevision, port.revision)
        equal(previousHealth, port.queryCompletedAtMs); equal(100L, port.positionMs)
    }
    test("cancellation before query and during iteration commits nothing") {
        val port = FakePort(); val source = FixtureSource(listOf(event(10), event(20)))
        stopped(Stop.CANCELLED, run(port, source, cancelled = { true })); equal(0, source.queryCount)
        var cancel = false
        source.afterNext = { cancel = true }
        stopped(Stop.CANCELLED, run(port, source, cancelled = { cancel })); untouched(port)
    }
    test("deadline expiring during query commits nothing") {
        var elapsed = 1000L
        val source = FixtureSource(); source.onQuery = { elapsed += 50 }
        val port = FakePort()
        stopped(Stop.DEADLINE, run(port, source, clock = Clock { Clocks(elapsed + 1000, elapsed) }, budget = Budget(deadlineMs = 50)))
        untouched(port)
    }
    for (jump in listOf(-1500L, 5000L)) test("wall jump $jump is explicit anomaly") {
        var wall = 2000L
        val source = FixtureSource(listOf(event(10))); source.afterNext = { wall += jump }
        val port = FakePort()
        stopped(Stop.CLOCK_ANOMALY, run(port, source, clock = Clock { Clocks(wall, 1000) })); untouched(port)
    }
    test("elapsed clock regression aborts rather than inventing boot provenance") {
        var elapsed = 1000L
        val source = FixtureSource(); source.onQuery = { elapsed-- }
        val port = FakePort()
        stopped(Stop.CLOCK_ANOMALY, run(port, source, clock = Clock { Clocks(2000, elapsed) })); untouched(port)
    }
    for ((name, changed) in listOf(
        "withdrawal" to SCOPE.copy(enabled = false, consentRevision = 2),
        "grant loss" to SCOPE.copy(granted = false),
        "generation" to SCOPE.copy(generation = "generation-2"),
        "installation" to SCOPE.copy(installationEpoch = "installation-2"),
        "filter" to SCOPE.copy(packages = setOf(OTHER)),
        "deletion" to SCOPE.copy(fences = listOf(SLICE)),
    )) test("$name during iteration refuses all staged facts") {
        val port = FakePort(); val source = FixtureSource(listOf(event(10)))
        source.afterNext = { port.currentScope = changed }
        stopped(Stop.CONFLICT, run(port, source)); untouched(port)
    }
    test("mutable configuration collections cannot bypass captured scope") {
        val packages = mutableSetOf(APP)
        val port = FakePort(SCOPE.copy(packages = packages))
        val source = FixtureSource(listOf(event(10))); source.afterNext = { packages.add(OTHER) }
        stopped(Stop.CONFLICT, run(port, source)); untouched(port)
    }
    test("withdrawal in final race is rejected by atomic coordinator") {
        val port = FakePort(); port.beforeCommit = { port.currentScope = SCOPE.copy(enabled = false) }
        stopped(Stop.CONFLICT, run(port, FixtureSource(listOf(event(10)))))
        equal(1, port.commitAttempts); untouched(port)
    }
    test("concurrent history revision and simulated transaction refusal preserve facts and cursor") {
        val port = FakePort(); port.beforeCommit = { port.revision++ }
        stopped(Stop.CONFLICT, run(port, FixtureSource(listOf(event(10)))))
        equal(0L, port.positionMs); equal(emptySet<Occurrence>(), port.rows); equal(null, port.queryCompletedAtMs)
        val refused = FakePort(); refused.permitCommit = false
        stopped(Stop.CONFLICT, run(refused, FixtureSource(listOf(event(10))))); untouched(refused)
    }
    test("query and iterator exceptions expose fixed reason only") {
        for (failure in listOf(SecurityException("private diagnostic"), IllegalStateException("private diagnostic"))) {
            val expected = if (failure is SecurityException) Stop.DENIED else Stop.SOURCE_FAILED
            val first = FakePort()
            stopped(expected, run(first, Source { throw failure })); untouched(first)
            val source = FixtureSource(listOf(event(10))); source.beforeHasNext = { throw failure }
            val second = FakePort()
            stopped(expected, run(second, source)); untouched(second)
        }
    }
    test("oversized or foreign prior state is refused before query") {
        for (counts in listOf(mapOf(event(10) to 4), mapOf(event(100) to 1), mapOf(event(10, pkg = OTHER) to 1), mapOf(event(10) to 0))) {
            val port = FakePort(); port.overrideRead = ReadState(0, 0, counts)
            val source = FixtureSource()
            stopped(Stop.INVALID_STATE, run(port, source, budget = Budget(maxEvents = 3)))
            equal(0, source.queryCount); untouched(port)
        }
    }
    test("older overlap never rewinds completed position") {
        val port = FakePort(); port.positionMs = 200
        committed(run(port)); equal(200L, port.positionMs)
    }
    test("dense slice splitting reconciles half-open children without partial parent facts") {
        val port = FakePort()
        val values = listOf(event(10), event(30), event(50), event(90), event(100))
        val queried = mutableListOf<Window>()
        val source = Source { window ->
            queried += window
            FixtureSource(values.filter { window.contains(it.atMs) }).query(window)
        }
        val ledger = SliceRun(SLICE, SCOPE, RetryLimits(), 1000)
        var decision = ledger.next(port.currentScope, 1000)
        while (decision is NextSlice.Attempt) {
            val outcome = run(port, source, decision.window, budget = Budget(maxEvents = 2))
            if (outcome is Outcome.Stopped) untouched(port)
            ledger.complete(decision, outcome, 1000)
            decision = ledger.next(port.currentScope, 1000)
        }
        equal(NextSlice.Done, decision)
        equal(listOf(SLICE, Window(0, 50), Window(50, 100)), queried)
        equal(values.dropLast(1).toSet(), port.rows.map { it.signature }.toSet())
        equal(100L, port.positionMs); equal(2L, port.revision)
    }
    test("unsplittable equal-time density retains all unresolved time and no partial commit") {
        val port = FakePort()
        val ledger = SliceRun(Window(0, 8), SCOPE, RetryLimits(), 0)
        var decision = ledger.next(SCOPE, 0)
        var queries = 0
        while (decision is NextSlice.Attempt) {
            queries++
            val result = run(port, FixtureSource(List(4) { event(0) }), decision.window, budget = Budget(maxEvents = 2))
            stopped(Stop.TRUNCATED, result); untouched(port)
            ledger.complete(decision, result, 0)
            decision = ledger.next(SCOPE, 0)
        }
        val halt = decision as NextSlice.Halted
        equal(RetryStop.DENSE_LIMIT, halt.reason); equal(4, queries)
        equal(listOf(Window(0, 1), Window(1, 2), Window(2, 4), Window(4, 8)), halt.unresolved)
    }
    test("attempt exhaustion preserves earlier child commits and never jumps an unresolved sibling") {
        val port = FakePort()
        val values = listOf(event(10)) + List(4) { event(75) }
        val source = Source { window -> FixtureSource(values.filter { window.contains(it.atMs) }).query(window) }
        val ledger = SliceRun(SLICE, SCOPE, RetryLimits(maxAttempts = 4), 0)
        var decision = ledger.next(SCOPE, 0)
        while (decision is NextSlice.Attempt) {
            ledger.complete(decision, run(port, source, decision.window, budget = Budget(maxEvents = 2)), 0)
            decision = ledger.next(SCOPE, 0)
        }
        val halt = decision as NextSlice.Halted
        equal(RetryStop.ATTEMPT_LIMIT, halt.reason); equal(listOf(Window(75, 100)), halt.unresolved)
        equal(setOf(Occurrence(event(10), 0)), port.rows); equal(75L, port.positionMs); equal(2L, port.revision)
    }
    test("planned exclusions cannot authorize jumping the single-slice history cursor") {
        val scope = SCOPE.copy(fences = listOf(Window(20, 30)))
        val port = FakePort(scope)
        val plan = planSlices(scope, SLICE) as PlanResult.Page
        equal(listOf(PlanStep.Slice(Window(0, 20)), PlanStep.Excluded(Window(20, 30), Exclusion.FENCED),
            PlanStep.Slice(Window(30, 100))), plan.steps)
        committed(run(port, window = Window(0, 20)))
        val laterSource = FixtureSource()
        stopped(Stop.INVALID_STATE, run(port, laterSource, Window(30, 100)))
        equal(0, laterSource.queryCount); equal(20L, port.positionMs); equal(1L, port.revision)
        // A future coordinator needs per-range progress/exclusion evidence; pagination cannot impersonate it.
    }
    test("scope change between committed children stops dispatch before another query") {
        val port = FakePort()
        val ledger = SliceRun(SLICE, SCOPE, RetryLimits(), 0)
        val first = ledger.next(SCOPE, 0) as NextSlice.Attempt
        ledger.complete(first, Outcome.Stopped(Stop.TRUNCATED), 0)
        val left = ledger.next(SCOPE, 0) as NextSlice.Attempt
        ledger.complete(left, run(port, window = left.window), 0)
        port.currentScope = SCOPE.copy(consentRevision = 2, enabled = false)
        val stop = ledger.next(port.currentScope, 0) as NextSlice.Halted
        equal(RetryStop.SCOPE_CHANGED, stop.reason); equal(listOf(Window(50, 100)), stop.unresolved)
        equal(50L, port.positionMs); equal(1L, port.revision)
    }
    passed += planningChecks()
    passed += progressChecks()
    println("PASS: $passed synthetic reconciliation/planning/progress checks; no Android source, storage or coverage admission")
}
