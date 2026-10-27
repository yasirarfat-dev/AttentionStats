package org.attentionstats.collection.reconciliation

/** AS-003 experiment only. No Android source, scheduler, store or production composition. */
data class Window(val startMs: Long, val endMs: Long) {
    init { require(startMs >= 0 && endMs > startMs) }
    fun contains(other: Window): Boolean = startMs <= other.startMs && endMs >= other.endMs
    fun overlaps(other: Window): Boolean = startMs < other.endMs && other.startMs < endMs
    fun contains(atMs: Long): Boolean = atMs >= startMs && atMs < endMs
}

data class Scope(
    val generation: String,
    val installationEpoch: String,
    val sourceId: String,
    val consentRevision: Long,
    val enabled: Boolean,
    val granted: Boolean,
    val firstUnlocked: Boolean,
    val packages: Set<String>,
    val permitted: List<Window>,
    val fences: List<Window> = emptyList(),
) {
    fun permits(window: Window): Boolean = enabled && granted && firstUnlocked &&
        packages.isNotEmpty() && permitted.any { it.contains(window) } && fences.none { it.overlaps(window) }

    fun snapshot(): Scope = copy(packages = packages.toSet(), permitted = permitted.toList(), fences = fences.toList())
}

enum class Transition { RESUMED, PAUSED, STOPPED }

/** Adapter fills every allowed scalar. Caller resets/copies the scratch value between events. */
class EventBuffer {
    var atMs: Long = 0
    var packageName: String? = null
    var className: String? = null
    var transition: Transition? = null
}

data class Signature(val atMs: Long, val packageName: String, val className: String?, val transition: Transition)

/** Ordinals label locally observed multiplicity; they are not public-source event identities. */
data class Occurrence(val signature: Signature, val ordinal: Int)

interface EventCursor {
    fun hasNext(): Boolean
    fun nextInto(buffer: EventBuffer)
}

sealed interface Query {
    data class Events(val cursor: EventCursor) : Query
    data object Unavailable : Query
    data object Denied : Query
    data object Failed : Query
}

fun interface Source { fun query(window: Window): Query }

data class Clocks(val wallAtMs: Long, val elapsedMs: Long)
fun interface Clock { fun read(): Clocks }

data class Budget(val maxEvents: Int = 1000, val maxSliceMs: Long = 60_000, val deadlineMs: Long = 5_000,
                  val clockToleranceMs: Long = 1000, val maxScalarChars: Int = 512) {
    init { require(maxEvents in 1..10_000 && maxSliceMs > 0 && deadlineMs > 0 && clockToleranceMs >= 0 && maxScalarChars in 1..1024) }
}

/** Port must supply only this slice's bounded counts, with no source text or global history dump. */
data class ReadState(val revision: Long, val completedThroughMs: Long, val multiplicities: Map<Signature, Int>)

enum class Correspondence { FIRST_OBSERVATION, UNRESOLVED_REPLAY }

data class Batch(
    val window: Window,
    val additions: List<Occurrence>,
    val completedThroughMs: Long,
    val queryCompletedAtMs: Long,
    val correspondence: Correspondence,
    val multiplicityDecreased: Boolean,
)

interface CommitPort {
    fun scope(): Scope
    fun read(window: Window, maxOccurrences: Int): ReadState
    /** Atomically recheck scope/revision/fences and apply the entire batch, or apply nothing. */
    fun compareAndCommit(expectedScope: Scope, expectedRevision: Long, batch: Batch): Boolean
}

enum class Stop {
    GATED, INVALID_SLICE, INVALID_STATE, UNAVAILABLE, DENIED, SOURCE_FAILED,
    CANCELLED, DEADLINE, CLOCK_ANOMALY, TRUNCATED, INVALID_EVENT, CONFLICT,
}

sealed interface Outcome {
    data class Committed(val batch: Batch) : Outcome
    data class Stopped(val reason: Stop) : Outcome
}

private class Halt(val reason: Stop) : RuntimeException()

/** One pinned, wholly permitted slice. Caller must split pauses/deletions; no gap is queried. */
fun reconcile(
    window: Window,
    source: Source,
    port: CommitPort,
    clock: Clock,
    budget: Budget = Budget(),
    cancelled: () -> Boolean = { false },
): Outcome {
    try {
        if (window.endMs - window.startMs > budget.maxSliceMs) throw Halt(Stop.INVALID_SLICE)
        val scope = port.scope().snapshot()
        if (!scope.permits(window)) throw Halt(Stop.GATED)
        val initial = clock.read()
        var lastElapsedMs = initial.elapsedMs
        fun guard(): Clocks {
            if (cancelled()) throw Halt(Stop.CANCELLED)
            if (port.scope().snapshot() != scope) throw Halt(Stop.CONFLICT)
            val now = clock.read()
            if (initial.wallAtMs < 0 || initial.elapsedMs < 0 || now.wallAtMs < 0 || now.elapsedMs < lastElapsedMs) {
                throw Halt(Stop.CLOCK_ANOMALY)
            }
            val elapsedDelta = now.elapsedMs - initial.elapsedMs
            val drift = try { Math.subtractExact(now.wallAtMs - initial.wallAtMs, elapsedDelta) }
                catch (_: ArithmeticException) { throw Halt(Stop.CLOCK_ANOMALY) }
            if (drift < -budget.clockToleranceMs || drift > budget.clockToleranceMs) throw Halt(Stop.CLOCK_ANOMALY)
            if (elapsedDelta >= budget.deadlineMs) throw Halt(Stop.DEADLINE)
            lastElapsedMs = now.elapsedMs
            return now
        }
        guard()
        if (window.endMs > initial.wallAtMs) throw Halt(Stop.INVALID_SLICE)
        val state = port.read(window, budget.maxEvents).let { it.copy(multiplicities = it.multiplicities.toMap()) }
        if (state.completedThroughMs < window.startMs || state.multiplicities.size > budget.maxEvents ||
            state.multiplicities.any { (signature, count) -> count !in 1..budget.maxEvents ||
                !window.contains(signature.atMs) || signature.packageName !in scope.packages } ||
            state.multiplicities.values.sumOf { it.toLong() } > budget.maxEvents
        ) throw Halt(Stop.INVALID_STATE)
        guard()
        val query = try { source.query(window) }
            catch (_: SecurityException) { throw Halt(Stop.DENIED) }
            catch (_: Exception) { throw Halt(Stop.SOURCE_FAILED) }
        guard()
        val cursor = when (query) {
            is Query.Events -> query.cursor
            Query.Unavailable -> throw Halt(Stop.UNAVAILABLE)
            Query.Denied -> throw Halt(Stop.DENIED)
            Query.Failed -> throw Halt(Stop.SOURCE_FAILED)
        }
        val counts = mutableMapOf<Signature, Int>()
        val buffer = EventBuffer()
        var iterated = 0
        try {
            while (true) {
                guard()
                val hasNext = cursor.hasNext()
                guard()
                if (!hasNext) break
                if (iterated >= budget.maxEvents) throw Halt(Stop.TRUNCATED)
                buffer.atMs = -1
                buffer.packageName = null
                buffer.className = null
                buffer.transition = null
                cursor.nextInto(buffer)
                iterated++
                guard()
                val packageName = buffer.packageName ?: continue
                if (packageName !in scope.packages) continue
                val transition = buffer.transition ?: continue
                if (!window.contains(buffer.atMs) || packageName.length > budget.maxScalarChars ||
                    (buffer.className?.length ?: 0) > budget.maxScalarChars
                ) throw Halt(Stop.INVALID_EVENT)
                val signature = Signature(buffer.atMs, packageName, buffer.className, transition)
                counts[signature] = counts.getOrDefault(signature, 0) + 1
            }
        } catch (halt: Halt) { throw halt }
          catch (_: SecurityException) { throw Halt(Stop.DENIED) }
          catch (_: Exception) { throw Halt(Stop.SOURCE_FAILED) }
        val additions = counts.flatMap { (signature, count) ->
            val previous = state.multiplicities.getOrDefault(signature, 0)
            (previous until count).map { Occurrence(signature, it) }
        }
        val completed = guard()
        val batch = Batch(
            window, additions.toList(), maxOf(state.completedThroughMs, window.endMs), completed.wallAtMs,
            if (state.multiplicities.isEmpty()) Correspondence.FIRST_OBSERVATION else Correspondence.UNRESOLVED_REPLAY,
            state.multiplicities.any { (signature, count) -> counts.getOrDefault(signature, 0) < count },
        )
        // This port call, not the earlier guard, must provide the atomic fence/revision check.
        if (!port.compareAndCommit(scope, state.revision, batch)) throw Halt(Stop.CONFLICT)
        return Outcome.Committed(batch)
    } catch (halt: Halt) {
        return Outcome.Stopped(halt.reason)
    }
}
