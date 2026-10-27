package org.attentionstats.collection.reconciliation

import kotlin.random.Random

private const val CORRECTION_APP = "com.example.fixture"
private val CORRECTION_SCOPE = Scope("g", "i", "s", 1, true, true, true, setOf(CORRECTION_APP), listOf(Window(0, 1000)))
private fun eq(expected: Any?, actual: Any?) { check(expected == actual) { "Expected <$expected>, actual <$actual>" } }
private fun work(state: RangeProgress, maxSliceMs: Long = 1000) =
    (nextProgress(state, state.scope, maxSliceMs) as ProgressNext.Work).ticket
private fun complete(scope: Scope = CORRECTION_SCOPE, window: Window = Window(0, 100), revision: Long = 1) =
    RangeProgress(scope, window, revision, listOf(RangeReceipt.Queried(window, maxOf(2000, window.endMs))))
private fun nextPass(state: RangeProgress, target: Long = state.requested.endMs,
                     limits: CorrectionLimits = CorrectionLimits(20, 100)) =
    (beginCorrection(state, state.scope, target, limits) as CorrectionResult.Ready).progress
private fun event(at: Long, activity: String = "Fixture") = Signature(at, CORRECTION_APP, activity, Transition.RESUMED)
private fun RangeCoordinator.drain(values: List<Signature> = emptyList()): List<Outcome> {
    val outcomes = mutableListOf<Outcome>()
    repeat(256) {
        when (val next = nextProgress(saved.progress, currentScope, 15)) {
            is ProgressNext.Work -> when (next.ticket.step) {
                is PlanStep.Excluded -> check(exclude(next.ticket))
                is PlanStep.Slice -> {
                    val result = run(next.ticket, values = values.filter { next.ticket.step.window.contains(it.atMs) })
                    check(result is Outcome.Committed); outcomes += result
                }
            }
            ProgressNext.Done -> return outcomes
            is ProgressNext.Stopped -> error("Unexpected ${next.reason}")
        }
    }
    error("Fixture exceeded bounded receipt count")
}

/** Simulates a compare-and-assign around the pure proposal, retaining facts and query health. */
private fun RangeCoordinator.correct(target: Long = saved.progress.requested.endMs,
                                     limits: CorrectionLimits = CorrectionLimits(20, 100),
                                     expected: AtomicFixture = saved): Boolean {
    val candidate = beginCorrection(expected.progress, currentScope, target, limits)
    if (candidate !is CorrectionResult.Ready) return false
    beforeWrite()
    if (refuseWrite || saved != expected || currentScope != expected.progress.scope) return false
    saved = expected.copy(progress = candidate.progress)
    return true
}

/** Explicit TEST-ONLY decision after independently rebuilding effective consent; no migration API. */
private fun RangeCoordinator.installRebuiltPass(rebuilt: Scope, requested: Window) {
    check(currentScope == rebuilt && saved.progress.revision < Long.MAX_VALUE)
    saved = saved.copy(progress = RangeProgress(rebuilt.snapshot(), requested, saved.progress.revision + 1, emptyList()))
}

fun correctionChecks(): Int {
    var passed = 0
    fun test(name: String, body: () -> Unit) {
        try { body(); passed++; println("PASS $name") }
        catch (failure: Throwable) { throw AssertionError("FAIL $name", failure) }
    }
    test("completed receipts open a fresh tail pass without mutating prior evidence") {
        val prior = complete()
        val fresh = nextPass(prior)
        eq(Window(80, 100), fresh.requested); eq(2L, fresh.revision); eq(emptyList<RangeReceipt>(), fresh.receipts)
        eq(listOf(fresh.requested), (recoverProgress(fresh, fresh.scope) as Recovery.Ready).unresolved)
        eq(1, prior.receipts.size); eq(1L, prior.revision)
    }
    test("same-window pass revisions prevent old tickets reviving after receipts reset") {
        val initial = RangeProgress(CORRECTION_SCOPE, Window(80, 100), 0, emptyList())
        val stale = work(initial)
        val coordinator = RangeCoordinator(AtomicFixture(initial))
        check(coordinator.run(stale) is Outcome.Committed)
        check(coordinator.correct())
        eq(initial.requested, coordinator.saved.progress.requested)
        eq(Outcome.Stopped(Stop.INVALID_STATE), coordinator.run(stale, values = listOf(event(90))))
        eq(1, coordinator.queryCount); eq(2L, coordinator.saved.progress.revision)
        check(coordinator.run(work(coordinator.saved.progress), values = listOf(event(90))) is Outcome.Committed)
    }
    test("unfinished or malformed passes cannot be erased by correction planning") {
        val unfinished = RangeProgress(CORRECTION_SCOPE, Window(0, 100), 3,
            listOf(RangeReceipt.Queried(Window(0, 50), 2000)))
        eq(CorrectionResult.Stopped(CorrectionStop.UNFINISHED), beginCorrection(unfinished, unfinished.scope, 200))
        val malformed = unfinished.copy(receipts = unfinished.receipts + unfinished.receipts)
        eq(CorrectionResult.Stopped(CorrectionStop.INVALID_STATE), beginCorrection(malformed, malformed.scope, 200))
        eq(listOf(Window(50, 100)), (recoverProgress(unfinished, unfinished.scope) as Recovery.Ready).unresolved)
    }
    test("bounded correction advances a large backlog without skipping its first unresolved tail") {
        var previous = complete()
        val ranges = mutableListOf<Window>()
        repeat(4) {
            val fresh = nextPass(previous, 350)
            ranges += fresh.requested
            previous = complete(window = fresh.requested, revision = fresh.revision + 1)
        }
        eq(listOf(Window(80, 180), Window(160, 260), Window(240, 340), Window(320, 350)), ranges)
    }
    test("correction bounds clamp at epoch and handle Long maximum without overflow") {
        eq(Window(0, 5), nextPass(complete(window = Window(0, 5))).requested)
        val scope = CORRECTION_SCOPE.copy(permitted = listOf(Window(0, Long.MAX_VALUE)))
        val previous = complete(scope, Window(Long.MAX_VALUE - 5, Long.MAX_VALUE - 1))
        eq(Window(Long.MAX_VALUE - 21, Long.MAX_VALUE), nextPass(previous, Long.MAX_VALUE).requested)
        val huge = nextPass(previous, Long.MAX_VALUE, CorrectionLimits(Long.MAX_VALUE - 1, Long.MAX_VALUE))
        eq(Window(0, Long.MAX_VALUE), huge.requested)
    }
    test("time regression and revision exhaustion preserve completed state") {
        val previous = complete()
        eq(CorrectionResult.Stopped(CorrectionStop.TIME_REGRESSION), beginCorrection(previous, previous.scope, 99))
        eq(CorrectionResult.Stopped(CorrectionStop.TIME_REGRESSION), beginCorrection(previous, previous.scope, -1))
        val exhausted = previous.copy(revision = Long.MAX_VALUE)
        eq(CorrectionResult.Stopped(CorrectionStop.REVISION_LIMIT), beginCorrection(exhausted, exhausted.scope, 100))
        eq(ProgressNext.Done, nextProgress(exhausted, exhausted.scope))
    }
    test("correction validates scope and receipt limits before proposing new work") {
        val previous = complete().copy(receipts = listOf(RangeReceipt.Queried(Window(0, 50), 2000),
            RangeReceipt.Queried(Window(50, 100), 2000)))
        eq(CorrectionResult.Stopped(CorrectionStop.INPUT_LIMIT), beginCorrection(previous, previous.scope, 100,
            progressLimits = ProgressLimits(maxReceipts = 1)))
        val excessive = CORRECTION_SCOPE.copy(packages = setOf(CORRECTION_APP, "com.example.other"))
        eq(CorrectionResult.Stopped(CorrectionStop.INPUT_LIMIT), beginCorrection(complete(excessive), excessive, 100,
            progressLimits = ProgressLimits(maxScopeEntries = 1)))
    }
    test("a completed pass at its receipt cap can start bounded correction without carrying receipts") {
        val previous = complete()
        val fresh = beginCorrection(previous, previous.scope, 100, CorrectionLimits(20, 100), ProgressLimits(maxReceipts = 1))
        check(fresh is CorrectionResult.Ready)
        eq(emptyList<RangeReceipt>(), fresh.progress.receipts); eq(2L, fresh.progress.revision)
        eq(1, previous.receipts.size)
    }
    test("all scope changes refuse automatic correction including fence removal and identity changes") {
        val previous = complete()
        val variants = listOf(CORRECTION_SCOPE.copy(generation = "g2"), CORRECTION_SCOPE.copy(installationEpoch = "i2"),
            CORRECTION_SCOPE.copy(sourceId = "s2"), CORRECTION_SCOPE.copy(consentRevision = 2),
            CORRECTION_SCOPE.copy(enabled = false), CORRECTION_SCOPE.copy(granted = false),
            CORRECTION_SCOPE.copy(firstUnlocked = false), CORRECTION_SCOPE.copy(packages = setOf("com.example.other")),
            CORRECTION_SCOPE.copy(permitted = listOf(Window(50, 1000))), CORRECTION_SCOPE.copy(fences = listOf(Window(80, 90))))
        variants.forEach { eq(CorrectionResult.Stopped(CorrectionStop.SCOPE_CHANGED), beginCorrection(previous, it, 100)) }
        val fenced = CORRECTION_SCOPE.copy(fences = listOf(Window(20, 30)))
        val coordinator = RangeCoordinator(AtomicFixture(RangeProgress(fenced, Window(0, 100), 0, emptyList())))
        coordinator.drain()
        eq(CorrectionResult.Stopped(CorrectionStop.SCOPE_CHANGED), beginCorrection(coordinator.saved.progress, CORRECTION_SCOPE, 100))
    }
    test("disabled and unavailable same-scope passes never start a query") {
        listOf(CORRECTION_SCOPE.copy(enabled = false), CORRECTION_SCOPE.copy(granted = false),
            CORRECTION_SCOPE.copy(firstUnlocked = false), CORRECTION_SCOPE.copy(packages = emptySet())).forEach { scope ->
            val empty = RangeProgress(scope, Window(0, 100), 0, emptyList())
            eq(CorrectionResult.Stopped(CorrectionStop.GATED), beginCorrection(empty, scope, 100))
        }
    }
    test("correction snapshot captures mutable scope containers") {
        val permitted = mutableListOf(Window(0, 1000))
        val scope = CORRECTION_SCOPE.copy(permitted = permitted)
        val fresh = nextPass(complete(scope))
        permitted.clear()
        eq(listOf(Window(0, 1000)), fresh.scope.permitted)
        eq(CorrectionResult.Stopped(CorrectionStop.SCOPE_CHANGED), beginCorrection(complete(fresh.scope), scope, 100))
    }
    test("random correction chains conserve requested overlap and advance until the pinned target") {
        val random = Random(57)
        repeat(200) {
            val end = random.nextLong(1, 300)
            val target = end + random.nextLong(0, 300)
            val width = random.nextLong(2, 80)
            val overlap = random.nextLong(1, width)
            var prior = complete(window = Window(0, end))
            var steps = 0
            do {
                val fresh = nextPass(prior, target, CorrectionLimits(overlap, width))
                val expectedPoints = (0L until target).filter { it >= prior.requested.endMs - overlap }.take(width.toInt())
                eq(Window(expectedPoints.first(), expectedPoints.last() + 1), fresh.requested)
                check(fresh.requested.endMs <= target && fresh.requested.endMs - fresh.requested.startMs <= width)
                check(fresh.requested.endMs >= prior.requested.endMs)
                if (target > prior.requested.endMs) check(fresh.requested.endMs > prior.requested.endMs)
                prior = complete(window = fresh.requested, revision = fresh.revision + 1)
                check(++steps <= 301)
            } while (prior.requested.endMs < target)
        }
    }
    test("fresh overlap revisits a successful empty query and accepts a late event once") {
        val coordinator = RangeCoordinator(AtomicFixture(RangeProgress(CORRECTION_SCOPE, Window(0, 100), 0, emptyList())))
        coordinator.drain(); val health = coordinator.saved.healthAtMs
        check(coordinator.correct()); eq(health, coordinator.saved.healthAtMs)
        val late = event(90)
        coordinator.drain(listOf(late)); eq(setOf(Occurrence(late, 0)), coordinator.saved.rows)
        check(coordinator.correct()); coordinator.drain(listOf(late))
        eq(setOf(Occurrence(late, 0)), coordinator.saved.rows)
    }
    test("correction preserves maximum multiplicity and flags smaller or missing groups") {
        val a = event(85); val b = event(95)
        val coordinator = RangeCoordinator(AtomicFixture(complete(), setOf(Occurrence(a, 0), Occurrence(a, 1), Occurrence(b, 0)), 1900))
        check(coordinator.correct()); val grown = coordinator.drain(listOf(a, a, a, b))
        eq(4, coordinator.saved.rows.size)
        check(grown.all { (it as Outcome.Committed).batch.correspondence == Correspondence.UNRESOLVED_REPLAY })
        val retained = coordinator.saved.rows
        check(coordinator.correct()); val shrunk = coordinator.drain(listOf(a))
        check(shrunk.any { (it as Outcome.Committed).batch.multiplicityDecreased }); eq(retained, coordinator.saved.rows)
        check(coordinator.correct()); val missing = coordinator.drain()
        check(missing.any { (it as Outcome.Committed).batch.multiplicityDecreased }); eq(retained, coordinator.saved.rows)
    }
    test("late events outside the bounded overlap remain unknown to the next pass") {
        val coordinator = RangeCoordinator(AtomicFixture(complete()))
        check(coordinator.correct()); coordinator.drain(listOf(event(79), event(80), event(99), event(100)))
        eq(setOf(80L, 99L), coordinator.saved.rows.map { it.signature.atMs }.toSet())
        eq(Window(80, 100), coordinator.saved.progress.requested)
    }
    test("new pass recomputes preconsent pause and deletion exclusions without querying them") {
        val scope = CORRECTION_SCOPE.copy(permitted = listOf(Window(10, 40), Window(60, 100)), fences = listOf(Window(20, 30)))
        val coordinator = RangeCoordinator(AtomicFixture(RangeProgress(scope, Window(0, 100), 0, emptyList())))
        coordinator.drain(); val before = coordinator.queryCount
        check(coordinator.correct(limits = CorrectionLimits(100, 101)))
        eq(emptyList<RangeReceipt>(), coordinator.saved.progress.receipts)
        coordinator.drain(listOf(event(15), event(35), event(65)))
        eq(5, coordinator.queryCount - before)
        eq(listOf(Window(0, 10), Window(20, 30), Window(40, 60)),
            coordinator.saved.progress.receipts.filterIsInstance<RangeReceipt.Excluded>().map { it.window })
        eq(setOf(15L, 35L, 65L), coordinator.saved.rows.map { it.signature.atMs }.toSet())
    }
    test("correction proposal refusal scope race and competing revision leave prior snapshot intact") {
        val coordinator = RangeCoordinator(AtomicFixture(complete(), setOf(Occurrence(event(90), 0)), 1900))
        val before = coordinator.saved
        coordinator.refuseWrite = true; check(!coordinator.correct()); eq(before, coordinator.saved)
        coordinator.refuseWrite = false
        coordinator.beforeWrite = { coordinator.currentScope = CORRECTION_SCOPE.copy(consentRevision = 2) }
        check(!coordinator.correct()); eq(before, coordinator.saved)
        coordinator.currentScope = CORRECTION_SCOPE
        coordinator.beforeWrite = { coordinator.saved = before.copy(progress = before.progress.copy(revision = 2)) }
        check(!coordinator.correct()); eq(2L, coordinator.saved.progress.revision)
        eq(before.rows, coordinator.saved.rows); eq(before.healthAtMs, coordinator.saved.healthAtMs)
    }
    test("reconstructing after pass installation retains unresolved overlap and refuses the old proposal") {
        val coordinator = RangeCoordinator(AtomicFixture(complete(), setOf(Occurrence(event(90), 0)), 1900))
        val prior = coordinator.saved
        check(coordinator.correct()); val installed = coordinator.saved
        val restarted = RangeCoordinator(installed)
        check(!restarted.correct(expected = prior)); eq(installed, restarted.saved)
        eq(listOf(Window(80, 100)), (recoverProgress(installed.progress, installed.progress.scope) as Recovery.Ready).unresolved)
        restarted.drain(listOf(event(90), event(95))); eq(2, restarted.saved.rows.size)
    }
    test("a truncated correction writes no receipt and child recovery retains new facts") {
        val coordinator = RangeCoordinator(AtomicFixture(complete()))
        check(coordinator.correct()); val before = coordinator.saved
        val ticket = work(before.progress)
        eq(Outcome.Stopped(Stop.TRUNCATED), coordinator.run(ticket, values = listOf(event(85), event(95)), budget = Budget(maxEvents = 1)))
        eq(before, coordinator.saved)
        check(coordinator.run(ticket, Window(80, 90), listOf(event(85))) is Outcome.Committed)
        val restarted = RangeCoordinator(coordinator.saved)
        eq(CorrectionResult.Stopped(CorrectionStop.UNFINISHED), beginCorrection(restarted.saved.progress, restarted.currentScope, 100))
        restarted.drain(listOf(event(95))); eq(setOf(85L, 95L), restarted.saved.rows.map { it.signature.atMs }.toSet())
    }
    test("scope withdrawal at correction commit cannot add facts receipt or health") {
        val coordinator = RangeCoordinator(AtomicFixture(complete(), healthAtMs = 1900))
        check(coordinator.correct()); val before = coordinator.saved
        coordinator.beforeWrite = { coordinator.currentScope = CORRECTION_SCOPE.copy(enabled = false, consentRevision = 2) }
        eq(Outcome.Stopped(Stop.CONFLICT), coordinator.run(work(before.progress), values = listOf(event(90))))
        eq(before, coordinator.saved)
    }
    test("a proposal beyond the query clock cannot query or manufacture future completion") {
        val scope = CORRECTION_SCOPE.copy(permitted = listOf(Window(0, 3000)))
        val coordinator = RangeCoordinator(AtomicFixture(complete(scope, Window(1900, 1990)), healthAtMs = 2000))
        check(coordinator.correct(2100, CorrectionLimits(20, 200)))
        val before = coordinator.saved
        eq(Outcome.Stopped(Stop.INVALID_SLICE), coordinator.run(work(before.progress)))
        eq(0, coordinator.queryCount); eq(before, coordinator.saved)
    }
    test("explicit rebuilt consent pass excludes the disabled gap and rejects all old tickets") {
        val coordinator = RangeCoordinator(AtomicFixture(RangeProgress(CORRECTION_SCOPE, Window(0, 100), 0, emptyList())))
        val stale = work(coordinator.saved.progress)
        check(coordinator.run(stale, Window(0, 50), listOf(event(40))) is Outcome.Committed)
        val original = coordinator.saved
        val rebuilt = CORRECTION_SCOPE.copy(consentRevision = 3, permitted = listOf(Window(0, 50), Window(80, 120)), fences = listOf(Window(30, 35)))
        coordinator.currentScope = rebuilt
        eq(Recovery.Stopped(ProgressStop.SCOPE_CHANGED), recoverProgress(original.progress, rebuilt))
        check(!coordinator.correct(120)); eq(original, coordinator.saved)
        coordinator.installRebuiltPass(rebuilt, Window(0, 120))
        eq(Outcome.Stopped(Stop.GATED), coordinator.run(stale))
        coordinator.drain(listOf(event(40), event(60), event(90)))
        eq(setOf(40L, 90L), coordinator.saved.rows.map { it.signature.atMs }.toSet())
        eq(listOf(Window(30, 35), Window(50, 80)), coordinator.saved.progress.receipts.filterIsInstance<RangeReceipt.Excluded>().map { it.window })
        eq(listOf(Window(0, 50)), original.progress.receipts.map { it.window })
    }
    test("removing a deletion fence does not migrate excluded or queried receipts into rebuilt consent") {
        val fenced = CORRECTION_SCOPE.copy(fences = listOf(Window(20, 30)))
        val coordinator = RangeCoordinator(AtomicFixture(RangeProgress(fenced, Window(0, 100), 0, emptyList())))
        coordinator.drain(); val old = coordinator.saved
        // The independent consent reconstruction retains the no-backfill gap even without that fence.
        val rebuilt = CORRECTION_SCOPE.copy(consentRevision = 2, permitted = listOf(Window(0, 20), Window(30, 1000)))
        coordinator.currentScope = rebuilt; check(!coordinator.correct())
        coordinator.installRebuiltPass(rebuilt, Window(0, 100))
        coordinator.drain(listOf(event(25), event(35)))
        eq(setOf(35L), coordinator.saved.rows.map { it.signature.atMs }.toSet())
        eq(listOf(RangeReceipt.Excluded(Window(20, 30), Exclusion.NOT_PERMITTED)), coordinator.saved.progress.receipts.filterIsInstance<RangeReceipt.Excluded>())
        eq(listOf(RangeReceipt.Excluded(Window(20, 30), Exclusion.FENCED)), old.progress.receipts.filterIsInstance<RangeReceipt.Excluded>())
    }
    test("filter changes read only current packages while retaining other historical facts") {
        val other = event(90).copy(packageName = "com.example.other")
        val originalScope = CORRECTION_SCOPE.copy(packages = setOf(CORRECTION_APP, other.packageName))
        val coordinator = RangeCoordinator(AtomicFixture(complete(originalScope), setOf(Occurrence(other, 0)), 1900))
        val rebuilt = CORRECTION_SCOPE.copy(consentRevision = 2)
        coordinator.currentScope = rebuilt; coordinator.installRebuiltPass(rebuilt, Window(80, 100))
        coordinator.drain(listOf(event(95)))
        eq(setOf(Occurrence(other, 0), Occurrence(event(95), 0)), coordinator.saved.rows)
    }
    test("scope returning to an earlier value cannot revive a ticket from before replacement") {
        val coordinator = RangeCoordinator(AtomicFixture(RangeProgress(CORRECTION_SCOPE, Window(80, 100), 0, emptyList())))
        val stale = work(coordinator.saved.progress)
        val other = CORRECTION_SCOPE.copy(consentRevision = 2)
        coordinator.currentScope = other; coordinator.installRebuiltPass(other, Window(80, 100))
        coordinator.currentScope = CORRECTION_SCOPE; coordinator.installRebuiltPass(CORRECTION_SCOPE, Window(80, 100))
        eq(Outcome.Stopped(Stop.INVALID_STATE), coordinator.run(stale)); eq(0, coordinator.queryCount)
    }
    test("invalid overlap limits cannot permit a nonadvancing backlog") {
        listOf({ CorrectionLimits(0, 10) }, { CorrectionLimits(10, 10) }, { CorrectionLimits(2, 1) },
            { CorrectionLimits(-1, 10) }).forEach { invalid ->
            check(runCatching { invalid() }.exceptionOrNull() is IllegalArgumentException)
        }
    }
    return passed
}
