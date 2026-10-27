package org.attentionstats.collection.reconciliation

import kotlin.random.Random

private const val PROGRESS_APP = "com.example.fixture"
private val PROGRESS_SCOPE = Scope("g", "i", "s", 1, true, true, true, setOf(PROGRESS_APP),
    listOf(Window(0, 100)), listOf(Window(20, 30)))
private fun same(expected: Any?, actual: Any?) { check(expected == actual) { "Expected <$expected>, actual <$actual>" } }
private fun initial(scope: Scope = PROGRESS_SCOPE) = RangeProgress(scope, Window(0, 100), 0, emptyList())
private fun ticket(state: RangeProgress, maxSliceMs: Long = 100) =
    (nextProgress(state, state.scope, maxSliceMs) as ProgressNext.Work).ticket
private fun queried(window: Window) = RangeReceipt.Queried(window, 2000)
private fun accept(state: RangeProgress, receipt: RangeReceipt) =
    (acceptProgress(state, state.scope, ticket(state), receipt) as ProgressCommit.Accepted).snapshot
private fun gaps(state: RangeProgress) = (recoverProgress(state, state.scope) as Recovery.Ready).unresolved

/** Single assigned snapshot simulates the facts/receipts/health transaction. No disk or real crash. */
private data class AtomicFixture(val progress: RangeProgress, val rows: Set<Occurrence> = emptySet(), val healthAtMs: Long? = null)
private class RangeCoordinator(var saved: AtomicFixture = AtomicFixture(initial())) {
    var currentScope = saved.progress.scope
    var beforeWrite: () -> Unit = {}
    var refuseWrite = false
    var queryCount = 0
    fun exclude(work: ProgressTicket): Boolean {
        beforeWrite()
        val step = work.step as PlanStep.Excluded
        val accepted = acceptProgress(saved.progress, currentScope, work, RangeReceipt.Excluded(step.window, step.reason))
        if (refuseWrite || accepted !is ProgressCommit.Accepted) return false
        saved = saved.copy(progress = accepted.snapshot)
        return true
    }
    fun run(work: ProgressTicket, window: Window = work.step.window, values: List<Signature> = emptyList(),
            budget: Budget = Budget(), duringQuery: () -> Unit = {}): Outcome {
        val port = object : CommitPort {
            override fun scope(): Scope = currentScope
            override fun read(window: Window, maxOccurrences: Int): ReadState {
                val recovered = recoverProgress(saved.progress, currentScope)
                val firstGap = (recovered as? Recovery.Ready)?.unresolved?.firstOrNull()
                val admitted = work.step is PlanStep.Slice && work.step.window.contains(window) &&
                    window.startMs == work.step.window.startMs && work.revision == saved.progress.revision &&
                    work.scope == currentScope && work.requested == saved.progress.requested &&
                    firstGap != null && firstGap.contains(window) && firstGap.startMs == window.startMs
                return ReadState(work.revision, if (admitted) window.startMs else -1,
                    saved.rows.filter { window.contains(it.signature.atMs) }.groupingBy { it.signature }.eachCount())
            }
            override fun compareAndCommit(expectedScope: Scope, expectedRevision: Long, batch: Batch): Boolean {
                beforeWrite()
                if (expectedScope != work.scope || expectedRevision != work.revision || refuseWrite) return false
                val accepted = acceptProgress(saved.progress, currentScope, work,
                    RangeReceipt.Queried(batch.window, batch.queryCompletedAtMs))
                if (accepted !is ProgressCommit.Accepted) return false
                saved = AtomicFixture(accepted.snapshot, saved.rows + batch.additions, batch.queryCompletedAtMs)
                return true
            }
        }
        val source = Source {
            queryCount++
            duringQuery()
            Query.Events(object : EventCursor {
                var index = 0
                override fun hasNext(): Boolean = index < values.size
                override fun nextInto(buffer: EventBuffer) {
                    val value = values[index++]
                    buffer.atMs = value.atMs; buffer.packageName = value.packageName
                    buffer.className = value.className; buffer.transition = value.transition
                }
            })
        }
        return reconcile(window, source, port, Clock { Clocks(2000, 1000) }, budget)
    }
}

fun progressChecks(): Int {
    var passed = 0
    fun test(name: String, body: () -> Unit) {
        try { body(); passed++; println("PASS $name") }
        catch (failure: Throwable) { throw AssertionError("FAIL $name", failure) }
    }
    test("receipts distinguish queried ranges from exclusions without one history cursor") {
        var state = initial()
        state = accept(state, queried(Window(0, 20)))
        state = accept(state, RangeReceipt.Excluded(Window(20, 30), Exclusion.FENCED))
        same(listOf(Window(30, 100)), gaps(state))
        state = accept(state, queried(Window(30, 100)))
        same(ProgressNext.Done, nextProgress(state, state.scope))
        same(listOf(Window(0, 20), Window(30, 100)), state.receipts.filterIsInstance<RangeReceipt.Queried>().map { it.window })
    }
    test("unexecuted proposal pagination and tickets create no progress") {
        val state = initial()
        repeat(5) { ticket(state, 5) }
        planSlices(state.scope, state.requested, PlanLimits(maxSteps = 1))
        same(listOf(state.requested), gaps(state)); same(0L, state.revision)
    }
    test("left density child commits while a later sibling cannot skip unresolved work") {
        val state = initial(PROGRESS_SCOPE.copy(fences = emptyList()))
        val work = ticket(state)
        same(ProgressCommit.Refused(ProgressStop.INVALID_RECEIPT),
            acceptProgress(state, state.scope, work, queried(Window(50, 100))))
        val left = (acceptProgress(state, state.scope, work, queried(Window(0, 50))) as ProgressCommit.Accepted).snapshot
        same(listOf(Window(50, 100)), gaps(left))
        same(ProgressCommit.Refused(ProgressStop.CONFLICT), acceptProgress(left, left.scope, work, queried(Window(50, 100))))
    }
    test("excluded intervals never accept query receipts or a different exclusion reason") {
        val state = accept(initial(), queried(Window(0, 20)))
        for (receipt in listOf(queried(Window(20, 30)), RangeReceipt.Excluded(Window(20, 30), Exclusion.NOT_PERMITTED),
            RangeReceipt.Excluded(Window(20, 25), Exclusion.FENCED))) {
            same(ProgressCommit.Refused(ProgressStop.INVALID_RECEIPT), acceptProgress(state, state.scope, ticket(state), receipt))
        }
    }
    test("queried intervals never accept exclusion receipts or future-invalid query completion") {
        val state = initial()
        for (receipt in listOf(RangeReceipt.Excluded(Window(0, 20), Exclusion.NOT_PERMITTED), RangeReceipt.Queried(Window(0, 20), 19))) {
            same(ProgressCommit.Refused(ProgressStop.INVALID_RECEIPT), acceptProgress(state, state.scope, ticket(state), receipt))
        }
    }
    test("scope changes invalidate recovery and every previously issued ticket") {
        val state = accept(initial(), queried(Window(0, 20)))
        val changed = listOf(state.scope.copy(generation = "new"), state.scope.copy(installationEpoch = "new"),
            state.scope.copy(sourceId = "new"), state.scope.copy(consentRevision = 2), state.scope.copy(enabled = false),
            state.scope.copy(granted = false), state.scope.copy(firstUnlocked = false), state.scope.copy(packages = emptySet()),
            state.scope.copy(permitted = listOf(Window(50, 100))), state.scope.copy(fences = emptyList()))
        for (scope in changed) {
            same(Recovery.Stopped(ProgressStop.SCOPE_CHANGED), recoverProgress(state, scope))
            same(ProgressNext.Stopped(ProgressStop.SCOPE_CHANGED), nextProgress(state, scope))
            same(ProgressCommit.Refused(ProgressStop.SCOPE_CHANGED), acceptProgress(state, scope, ticket(state),
                RangeReceipt.Excluded(Window(20, 30), Exclusion.FENCED)))
        }
    }
    test("revision and proposal range mismatches refuse stale completions") {
        val state = initial()
        val work = ticket(state)
        for (changed in listOf(work.copy(revision = 1), work.copy(requested = Window(0, 99)), work.copy(scope = state.scope.copy(sourceId = "other")))) {
            same(ProgressCommit.Refused(ProgressStop.CONFLICT), acceptProgress(state, state.scope, changed, queried(Window(0, 20))))
        }
    }
    test("recovery accounts for unsorted disjoint receipts and retains every interior hole") {
        val state = initial(PROGRESS_SCOPE.copy(fences = emptyList())).copy(receipts = listOf(queried(Window(70, 90)), queried(Window(10, 30))))
        same(listOf(Window(0, 10), Window(30, 70), Window(90, 100)), gaps(state))
        same(Window(0, 10), ticket(state).step.window)
    }
    test("malformed overlapping duplicate out-of-range and false receipts fail closed") {
        val state = initial()
        for (receipts in listOf(listOf(queried(Window(0, 10)), queried(Window(5, 15))),
            listOf(queried(Window(0, 10)), queried(Window(0, 10))), listOf(queried(Window(90, 101))),
            listOf(queried(Window(0, 30))), listOf(RangeReceipt.Excluded(Window(0, 20), Exclusion.FENCED)),
            listOf(RangeReceipt.Excluded(Window(20, 40), Exclusion.FENCED)))) {
            same(Recovery.Stopped(ProgressStop.INVALID_STATE), recoverProgress(state.copy(receipts = receipts), state.scope))
        }
        same(Recovery.Stopped(ProgressStop.INVALID_STATE), recoverProgress(state.copy(revision = -1), state.scope))
    }
    test("receipt and scope limits stop without dropping accepted progress") {
        val state = accept(initial(), queried(Window(0, 20)))
        val limits = ProgressLimits(maxReceipts = 1)
        check(recoverProgress(state, state.scope, limits) is Recovery.Ready)
        same(ProgressNext.Stopped(ProgressStop.INPUT_LIMIT), nextProgress(state, state.scope, limits = limits))
        same(ProgressCommit.Refused(ProgressStop.INPUT_LIMIT), acceptProgress(state, state.scope, ticket(state),
            RangeReceipt.Excluded(Window(20, 30), Exclusion.FENCED), limits))
        val oversized = state.copy(receipts = state.receipts + state.receipts)
        same(Recovery.Stopped(ProgressStop.INPUT_LIMIT), recoverProgress(oversized, oversized.scope, limits))
        same(Recovery.Stopped(ProgressStop.INPUT_LIMIT), recoverProgress(state, state.scope, ProgressLimits(maxScopeEntries = 1)))
        same(listOf(Window(20, 100)), gaps(state))
    }
    test("revision maximum cannot wrap and finished snapshots need no new revision") {
        val state = initial().copy(revision = Long.MAX_VALUE)
        same(ProgressNext.Stopped(ProgressStop.REVISION_LIMIT), nextProgress(state, state.scope))
        same(ProgressCommit.Refused(ProgressStop.REVISION_LIMIT), acceptProgress(state, state.scope,
            ticket(initial()).copy(revision = Long.MAX_VALUE), queried(Window(0, 20))))
        val done = state.copy(scope = state.scope.copy(fences = emptyList()), receipts = listOf(queried(Window(0, 100))))
        same(ProgressNext.Done, nextProgress(done, done.scope))
    }
    test("bounded recovery copies mutable scope and receipt containers") {
        val packages = mutableSetOf(PROGRESS_APP)
        val ranges = mutableListOf(Window(0, 100))
        val receipts = mutableListOf<RangeReceipt>(queried(Window(0, 20)))
        val state = initial(PROGRESS_SCOPE.copy(packages = packages, permitted = ranges)).copy(receipts = receipts)
        val recovered = recoverProgress(state, state.scope) as Recovery.Ready
        val work = ticket(state)
        packages.clear(); ranges.clear(); receipts.clear()
        same(1, recovered.snapshot.receipts.size); same(setOf(PROGRESS_APP), recovered.snapshot.scope.packages)
        same(listOf(Window(0, 100)), work.scope.permitted)
    }
    test("disabled unknown-grant and first-unlock scopes dispatch nothing") {
        for (scope in listOf(PROGRESS_SCOPE.copy(enabled = false), PROGRESS_SCOPE.copy(granted = false),
            PROGRESS_SCOPE.copy(firstUnlocked = false), PROGRESS_SCOPE.copy(packages = emptySet()))) {
            same(ProgressNext.Stopped(ProgressStop.GATED), nextProgress(initial(scope), scope))
        }
    }
    test("Long maximum and one millisecond receipt edges recover exactly") {
        val window = Window(Long.MAX_VALUE - 2, Long.MAX_VALUE)
        val scope = PROGRESS_SCOPE.copy(permitted = listOf(window), fences = emptyList())
        var state = RangeProgress(scope, window, 0, emptyList())
        state = accept(state, queried(Window(window.startMs, window.endMs - 1)).copy(queryCompletedAtMs = Long.MAX_VALUE))
        same(listOf(Window(window.endMs - 1, window.endMs)), gaps(state))
        state = accept(state, queried(Window(window.endMs - 1, window.endMs)).copy(queryCompletedAtMs = Long.MAX_VALUE))
        same(emptyList<Window>(), gaps(state))
    }
    test("200 deterministic receipt partitions recover against independent point oracle") {
        val random = Random(83003)
        val scope = PROGRESS_SCOPE.copy(fences = emptyList())
        repeat(200) {
            val accounted = BooleanArray(100)
            val receipts = mutableListOf<RangeReceipt>()
            for (start in 0 until 100 step 5) if (random.nextBoolean()) {
                receipts += queried(Window(start.toLong(), start + 5L))
                for (point in start until start + 5) accounted[point] = true
            }
            receipts.shuffle(random)
            val unresolved = gaps(initial(scope).copy(receipts = receipts))
            for (point in accounted.indices) same(!accounted[point], unresolved.any { it.contains(point.toLong()) })
            same(100L, receipts.sumOf { it.window.endMs - it.window.startMs } + unresolved.sumOf { it.endMs - it.startMs })
        }
    }
    test("coordinator queries both sides of fence while exclusion changes no query health") {
        val coordinator = RangeCoordinator()
        check(coordinator.run(ticket(coordinator.saved.progress)) is Outcome.Committed)
        val health = coordinator.saved.healthAtMs
        check(coordinator.exclude(ticket(coordinator.saved.progress)))
        same(1, coordinator.queryCount); same(health, coordinator.saved.healthAtMs)
        check(coordinator.run(ticket(coordinator.saved.progress)) is Outcome.Committed)
        same(2, coordinator.queryCount); same(ProgressNext.Done, nextProgress(coordinator.saved.progress, coordinator.currentScope))
    }
    test("coordinator preserves preconsent pause and deletion reasons across a bounded pass") {
        val scope = PROGRESS_SCOPE.copy(permitted = listOf(Window(10, 40), Window(60, 90)))
        val coordinator = RangeCoordinator(AtomicFixture(initial(scope)))
        var operations = 0
        while (true) {
            val next = nextProgress(coordinator.saved.progress, scope, maxSliceMs = 15)
            if (next == ProgressNext.Done) break
            check(++operations <= 8)
            val work = (next as ProgressNext.Work).ticket
            when (work.step) {
                is PlanStep.Slice -> check(coordinator.run(work) is Outcome.Committed)
                is PlanStep.Excluded -> check(coordinator.exclude(work))
            }
        }
        same(4, coordinator.queryCount); same(8L, coordinator.saved.progress.revision)
        same(listOf(RangeReceipt.Excluded(Window(0, 10), Exclusion.NOT_PERMITTED),
            RangeReceipt.Excluded(Window(20, 30), Exclusion.FENCED),
            RangeReceipt.Excluded(Window(40, 60), Exclusion.NOT_PERMITTED),
            RangeReceipt.Excluded(Window(90, 100), Exclusion.NOT_PERMITTED)),
            coordinator.saved.progress.receipts.filterIsInstance<RangeReceipt.Excluded>())
    }
    test("failure before atomic assignment changes no facts receipts or health") {
        val coordinator = RangeCoordinator()
        val before = coordinator.saved
        coordinator.refuseWrite = true
        val value = Signature(10, PROGRESS_APP, "Fixture", Transition.RESUMED)
        same(Outcome.Stopped(Stop.CONFLICT), coordinator.run(ticket(before.progress), values = listOf(value)))
        same(before, coordinator.saved); same(listOf(Window(0, 100)), gaps(coordinator.saved.progress))
    }
    test("lost acknowledgement recovery uses committed receipt and never replays accepted child") {
        val original = RangeCoordinator(AtomicFixture(initial(PROGRESS_SCOPE.copy(fences = emptyList()))))
        val work = ticket(original.saved.progress)
        val child = Window(0, 50)
        val value = Signature(10, PROGRESS_APP, "Fixture", Transition.RESUMED)
        check(original.run(work, child, listOf(value)) is Outcome.Committed) // Caller loses this outcome.
        val restarted = RangeCoordinator(original.saved)
        same(Window(50, 100), ticket(restarted.saved.progress).step.window)
        same(Outcome.Stopped(Stop.INVALID_STATE), restarted.run(work, child, listOf(value)))
        same(0, restarted.queryCount)
        same(1, restarted.saved.rows.size); same(1, restarted.saved.progress.receipts.size)
        check(restarted.run(ticket(restarted.saved.progress)) is Outcome.Committed)
        same(ProgressNext.Done, nextProgress(restarted.saved.progress, restarted.currentScope))
    }
    test("stale concurrent ticket and final withdrawal races preserve earlier child transaction") {
        val coordinator = RangeCoordinator(AtomicFixture(initial(PROGRESS_SCOPE.copy(fences = emptyList()))))
        val stale = ticket(coordinator.saved.progress)
        check(coordinator.run(stale, Window(0, 50)) is Outcome.Committed)
        val accepted = coordinator.saved
        same(Outcome.Stopped(Stop.INVALID_STATE), coordinator.run(stale, Window(0, 50)))
        same(1, coordinator.queryCount)
        same(accepted, coordinator.saved)
        val next = ticket(coordinator.saved.progress)
        coordinator.beforeWrite = { coordinator.currentScope = coordinator.currentScope.copy(consentRevision = 2, enabled = false) }
        same(Outcome.Stopped(Stop.CONFLICT), coordinator.run(next))
        same(accepted, coordinator.saved)
        same(Recovery.Stopped(ProgressStop.SCOPE_CHANGED), recoverProgress(coordinator.saved.progress, coordinator.currentScope))
    }
    test("exclusion commit scope race cannot mark stale permission as accounted") {
        val coordinator = RangeCoordinator()
        check(coordinator.run(ticket(coordinator.saved.progress)) is Outcome.Committed)
        val before = coordinator.saved
        val work = ticket(before.progress)
        coordinator.beforeWrite = { coordinator.currentScope = coordinator.currentScope.copy(fences = emptyList(), consentRevision = 2) }
        check(!coordinator.exclude(work)); same(before, coordinator.saved)
    }
    test("competing commit after dispatch rejects stale facts at atomic revision fence") {
        val coordinator = RangeCoordinator(AtomicFixture(initial(PROGRESS_SCOPE.copy(fences = emptyList()))))
        val work = ticket(coordinator.saved.progress)
        val value = Signature(10, PROGRESS_APP, "Fixture", Transition.RESUMED)
        coordinator.beforeWrite = {
            val accepted = acceptProgress(coordinator.saved.progress, coordinator.currentScope, work, queried(Window(0, 50)))
                as ProgressCommit.Accepted
            coordinator.saved = coordinator.saved.copy(progress = accepted.snapshot)
        }
        same(Outcome.Stopped(Stop.CONFLICT), coordinator.run(work, values = listOf(value)))
        same(emptySet<Occurrence>(), coordinator.saved.rows); same(null, coordinator.saved.healthAtMs)
        same(listOf(Window(50, 100)), gaps(coordinator.saved.progress)); same(1L, coordinator.saved.progress.revision)
    }
    test("density truncation records no receipt and recovery retains committed children only") {
        val coordinator = RangeCoordinator(AtomicFixture(initial(PROGRESS_SCOPE.copy(fences = emptyList()))))
        val values = listOf(10L, 20L, 60L, 70L).map { Signature(it, PROGRESS_APP, "Fixture", Transition.RESUMED) }
        val work = ticket(coordinator.saved.progress)
        same(Outcome.Stopped(Stop.TRUNCATED), coordinator.run(work, values = values, budget = Budget(maxEvents = 2)))
        same(emptyList<RangeReceipt>(), coordinator.saved.progress.receipts)
        check(coordinator.run(work, Window(0, 50), values.take(2), Budget(maxEvents = 2)) is Outcome.Committed)
        val restarted = RangeCoordinator(coordinator.saved)
        same(listOf(Window(50, 100)), gaps(restarted.saved.progress))
        check(restarted.run(ticket(restarted.saved.progress), values = values.drop(2), budget = Budget(maxEvents = 2)) is Outcome.Committed)
        same(4, restarted.saved.rows.size); same(2, restarted.saved.progress.receipts.size)
    }
    test("retry ledger child commits use fresh coordinator tickets under one run budget") {
        val scope = PROGRESS_SCOPE.copy(fences = emptyList())
        val coordinator = RangeCoordinator(AtomicFixture(initial(scope)))
        val ledger = SliceRun(Window(0, 100), scope, RetryLimits(maxAttempts = 3), 0)
        val values = listOf(10L, 20L, 60L, 70L).map { Signature(it, PROGRESS_APP, "Fixture", Transition.RESUMED) }
        val attempted = mutableListOf<Window>()
        repeat(3) {
            val attempt = ledger.next(scope, 0) as NextSlice.Attempt
            attempted += attempt.window
            val work = ticket(coordinator.saved.progress)
            val result = coordinator.run(work, attempt.window, values.filter { attempt.window.contains(it.atMs) }, Budget(maxEvents = 2))
            ledger.complete(attempt, result, 0)
        }
        same(listOf(Window(0, 100), Window(0, 50), Window(50, 100)), attempted)
        same(NextSlice.Done, ledger.next(scope, 0)); same(4, coordinator.saved.rows.size)
        same(listOf(Window(0, 50), Window(50, 100)), coordinator.saved.progress.receipts.map { it.window })
        same(2L, coordinator.saved.progress.revision)
    }
    test("forged later or fence-crossing proposal cannot skip an unresolved prefix") {
        val state = initial()
        val original = ticket(state)
        for ((step, receipt) in listOf(
            PlanStep.Slice(Window(30, 100)) to queried(Window(30, 100)),
            PlanStep.Slice(Window(0, 100)) to queried(Window(0, 10)),
            PlanStep.Excluded(Window(0, 30), Exclusion.FENCED) to RangeReceipt.Excluded(Window(0, 30), Exclusion.FENCED))) {
            same(ProgressCommit.Refused(ProgressStop.INVALID_RECEIPT),
                acceptProgress(state, state.scope, original.copy(step = step), receipt))
        }
        same(listOf(state.requested), gaps(state))
    }
    test("invalid progress limits and slice limits fail at construction") {
        for (construct in listOf<() -> Unit>({ ProgressLimits(0) }, { ProgressLimits(1025) },
            { ProgressLimits(maxScopeEntries = 0) }, { nextProgress(initial(), PROGRESS_SCOPE, 0) })) {
            check(runCatching(construct).exceptionOrNull() is IllegalArgumentException)
        }
    }
    return passed
}
