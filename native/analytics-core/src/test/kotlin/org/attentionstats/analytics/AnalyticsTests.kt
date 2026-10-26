package org.attentionstats.analytics

import org.attentionstats.analytics.activity.AppInterval
import org.attentionstats.analytics.activity.SurfaceInterval
import org.attentionstats.analytics.activity.SurfaceBreakdown
import org.attentionstats.analytics.activity.surfaceBreakdown
import org.attentionstats.analytics.network.NetworkBucket
import org.attentionstats.analytics.network.NetworkBucketKey
import org.attentionstats.analytics.network.Transport
import org.attentionstats.analytics.network.networkUsage
import org.attentionstats.analytics.observation.Eligibility
import org.attentionstats.analytics.observation.ExposureObservation
import org.attentionstats.analytics.observation.InteractionAction
import org.attentionstats.analytics.observation.InteractionEvidence
import org.attentionstats.analytics.observation.InteractionObservation
import org.attentionstats.analytics.observation.exposureCount
import org.attentionstats.analytics.observation.interactionCounts
import org.attentionstats.analytics.summary.EvidenceKind
import org.attentionstats.analytics.summary.Granularity
import org.attentionstats.analytics.summary.MetricUnit
import org.attentionstats.analytics.summary.SummaryFact
import org.attentionstats.analytics.summary.TimeEquivalent
import org.attentionstats.analytics.summary.calendarBucket
import org.attentionstats.analytics.summary.rollupFacts
import org.attentionstats.analytics.summary.timeEquivalent
import org.attentionstats.analytics.time.TimeRange
import org.attentionstats.analytics.time.splitByDay
import org.attentionstats.analytics.time.totalMs
import org.attentionstats.analytics.time.union

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

private const val APP = "com.example.social"
private const val OTHER = "com.example.reader"
private const val REELS = "short_video"
private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private val UTC = ZoneId.of("UTC")
private var passed = 0

private fun test(name: String, body: () -> Unit) {
    try {
        body()
        passed++
        println("PASS $name")
    } catch (error: Throwable) {
        throw AssertionError("FAIL $name", error)
    }
}

private fun equal(expected: Any?, actual: Any?) {
    check(expected == actual) { "Expected <$expected>, actual <$actual>" }
}

private fun rejects(body: () -> Unit) {
    var rejected = false
    try { body() } catch (_: IllegalArgumentException) { rejected = true }
    check(rejected) { "Expected invalid input to be rejected" }
}

private fun at(value: String): Long = Instant.parse(value).toEpochMilli()
private fun exposure(ordinal: Long, time: Long = ordinal + 10, eligibility: Eligibility = Eligibility.QUALIFIED,
                     app: String = APP, episode: String = "visit-a") =
    ExposureObservation(app, REELS, episode, ordinal, time, eligibility)

private fun bucket(start: Long = 0, end: Long = HOUR, rx: Long = 100, tx: Long = 20,
                   uid: String = "synthetic-install-a", transport: Transport = Transport.WIFI) =
    NetworkBucket(NetworkBucketKey(uid, transport, TimeRange(start, end), -1, -1, -1, -1), rx, tx)

private fun network(buckets: List<NetworkBucket>, query: TimeRange = TimeRange(0, 2 * HOUR),
                    permission: Boolean = true, success: Boolean = true, unique: Boolean = true) =
    networkUsage("synthetic-install-a", Transport.WIFI, query, permission, success, unique, buckets)

private fun fact(id: String, start: String, end: String, value: Long? = 5, kind: EvidenceKind = EvidenceKind.ESTIMATE,
                 revision: String = "exposure-v1", metric: String = "short_video_exposures") : SummaryFact {
    val range = TimeRange(at(start), at(end))
    return SummaryFact(id, metric, "$APP/$REELS", MetricUnit.COUNT, kind, revision, range,
        value, if (value == null) 0 else range.durationMs, range.durationMs)
}

private fun testTime() {
    test("half-open membership, intersections and rejected invalid intervals") {
        val range = TimeRange(10, 20)
        check(range.contains(10) && !range.contains(20))
        equal(null, range.intersect(TimeRange(20, 30)))
        equal(TimeRange(15, 20), range.intersect(TimeRange(15, 25)))
        rejects { TimeRange(5, 5) }; rejects { TimeRange(-1, 1) }; rejects { TimeRange(9, 8) }
    }
    test("union merges overlaps and adjacency without double-counting") {
        equal(listOf(TimeRange(1, 20), TimeRange(30, 40)), union(listOf(
            TimeRange(10, 20), TimeRange(1, 11), TimeRange(30, 40), TimeRange(1, 5))))
        equal(emptyList<TimeRange>(), union(emptyList()))
    }
    test("midnight splits conserve original duration and event day boundaries") {
        val range = TimeRange(at("2026-10-26T23:59:00Z"), at("2026-10-27T00:01:00Z"))
        val slices = splitByDay(range, UTC)
        equal(listOf(LocalDate.parse("2026-10-26"), LocalDate.parse("2026-10-27")), slices.map { it.date })
        equal(listOf(MINUTE, MINUTE), slices.map { it.range.durationMs })
        equal(range.durationMs, totalMs(slices.map { it.range }))
    }
    test("civil days honor spring and autumn DST rather than fixed 24 hours") {
        val zone = ZoneId.of("America/New_York")
        val spring = calendarBucket(at("2026-03-08T12:00:00Z"), zone, Granularity.DAY, DayOfWeek.MONDAY)
        val autumn = calendarBucket(at("2026-11-01T12:00:00Z"), zone, Granularity.DAY, DayOfWeek.MONDAY)
        equal(23 * HOUR, spring.durationMs); equal(25 * HOUR, autumn.durationMs)
        equal(1, splitByDay(spring, zone).size)
    }
    test("view-zone regrouping changes bins without mutating UTC evidence") {
        val instant = at("2026-10-26T20:00:00Z")
        val dhaka = calendarBucket(instant, ZoneId.of("Asia/Dhaka"), Granularity.DAY, DayOfWeek.MONDAY)
        equal(at("2026-10-26T18:00:00Z"), dhaka.startMs)
        equal(instant, at("2026-10-26T20:00:00Z"))
    }
    test("skipped civil date progresses without a phantom day") {
        val range = TimeRange(at("2011-12-29T10:00:00Z"), at("2011-12-31T10:00:00Z"))
        val slices = splitByDay(range, ZoneId.of("Pacific/Apia"))
        equal(listOf(LocalDate.parse("2011-12-29"), LocalDate.parse("2011-12-31")), slices.map { it.date })
        equal(range.durationMs, totalMs(slices.map { it.range }))
    }
}

private fun testSurfaces() {
    test("five-hour app diary keeps four-hour Reels as a subset") {
        val result = surfaceBreakdown(APP, TimeRange(0, 5 * HOUR),
            listOf(AppInterval(APP, TimeRange(0, 5 * HOUR))),
            listOf(SurfaceInterval(APP, REELS, TimeRange(0, 4 * HOUR)),
                SurfaceInterval(APP, "feed", TimeRange(4 * HOUR, 5 * HOUR))))
        equal(5 * HOUR, result.appMs); equal(4 * HOUR, result.bySurfaceMs[REELS])
        equal(HOUR, result.bySurfaceMs["feed"]); equal(0L, result.unclassifiedMs)
    }
    test("surface intervals clip to app union and exclude other apps") {
        val result = surfaceBreakdown(APP, TimeRange(0, 100),
            listOf(AppInterval(APP, TimeRange(20, 80)), AppInterval(APP, TimeRange(30, 90)),
                AppInterval(OTHER, TimeRange(0, 100))),
            listOf(SurfaceInterval(APP, REELS, TimeRange(0, 100)),
                SurfaceInterval(OTHER, "reading", TimeRange(0, 100))))
        equal(70L, result.appMs); equal(mapOf(REELS to 70L), result.bySurfaceMs)
    }
    test("overlapping different surfaces remain unclassified with conflict evidence") {
        val result = surfaceBreakdown(APP, TimeRange(0, 100), listOf(AppInterval(APP, TimeRange(0, 100))),
            listOf(SurfaceInterval(APP, REELS, TimeRange(0, 70)), SurfaceInterval(APP, "feed", TimeRange(50, 90))))
        equal(mapOf(REELS to 50L, "feed" to 20L), result.bySurfaceMs)
        equal(30L, result.unclassifiedMs); equal(20L, result.conflictedMs)
    }
    test("same-surface duplicate intervals count once and missing detectors preserve unclassified time") {
        val apps = listOf(AppInterval(APP, TimeRange(0, 100)))
        val surface = SurfaceInterval(APP, REELS, TimeRange(0, 50))
        val result = surfaceBreakdown(APP, TimeRange(0, 100), apps, List(50) { surface })
        equal(50L, result.bySurfaceMs[REELS]); equal(50L, result.unclassifiedMs)
        equal(100L, surfaceBreakdown(APP, TimeRange(0, 100), apps, emptyList()).unclassifiedMs)
    }
    test("randomized sweep matches an independent point-wise partition oracle") {
        val random = Random(20261027)
        repeat(200) {
            val apps = List(10) {
                val start = random.nextInt(0, 99).toLong()
                AppInterval(APP, TimeRange(start, random.nextLong(start + 1, 101)))
            }
            val surfaces = List(20) {
                val start = random.nextInt(0, 99).toLong()
                SurfaceInterval(APP, if (random.nextBoolean()) REELS else "feed",
                    TimeRange(start, random.nextLong(start + 1, 101)))
            }
            val expected = mutableMapOf<String, Long>()
            var appMs = 0L; var unknown = 0L; var conflict = 0L
            for (tick in 0L until 100L) if (apps.any { it.range.contains(tick) }) {
                appMs++
                val active = surfaces.filter { it.range.contains(tick) }.map { it.surfaceId }.toSet()
                if (active.size == 1) expected[active.single()] = (expected[active.single()] ?: 0L) + 1
                else { unknown++; if (active.size > 1) conflict++ }
            }
            val actual = surfaceBreakdown(APP, TimeRange(0, 100), apps, surfaces)
            equal(SurfaceBreakdown(appMs, expected, unknown, conflict), actual)
        }
    }
}

private fun testCounts() {
    val query = TimeRange(0, 1000)
    fun count(events: List<ExposureObservation>, qualified: Boolean = true, coverage: List<TimeRange> = listOf(query)) =
        exposureCount(APP, REELS, query, qualified, coverage, events)
    test("fifty duplicate callbacks add one settled exposure") {
        equal(1L, count(List(50) { exposure(0) }).value)
        equal(2L, count(List(50) { exposure(0) } + exposure(1)).value)
    }
    test("ads excluded; ambiguous eligibility marks a partial count") {
        val result = count(listOf(exposure(0), exposure(1, eligibility = Eligibility.EXCLUDED),
            exposure(2, eligibility = Eligibility.AMBIGUOUS)))
        equal(1L, result.value); check(result.partial)
        equal(setOf("AMBIGUOUS_ELIGIBILITY"), result.reasonCodes)
    }
    test("app and visit identity isolate equal ordinals; a real revisit may count again") {
        equal(2L, count(listOf(exposure(0), exposure(0, app = OTHER), exposure(0, episode = "visit-b"))).value)
    }
    test("unsupported and missing coverage are null; covered empty is zero") {
        equal(null, count(emptyList(), qualified = false).value)
        equal(null, count(emptyList(), coverage = emptyList()).value)
        equal(0L, count(emptyList()).value)
        val partial = count(listOf(exposure(0)), coverage = listOf(TimeRange(0, 500)))
        equal(1L, partial.value); check(partial.partial)
    }
    test("contradictory replay identity is withheld rather than counted twice") {
        val result = count(listOf(exposure(0, 10), exposure(0, 20)))
        equal(0L, result.value); check("CONFLICTING_TRANSITION" in result.reasonCodes)
    }
    test("event at midnight belongs only to next half-open day") {
        val event = exposure(0, 1000)
        equal(0L, count(listOf(event)).value)
        equal(1L, exposureCount(APP, REELS, TimeRange(1000, 2000), true,
            listOf(TimeRange(1000, 2000)), listOf(event)).value)
    }
    test("events outside coverage do not make gaps look known") {
        val result = count(listOf(exposure(0, 800)), coverage = listOf(TimeRange(0, 500)))
        equal(0L, result.value); check("EVENT_OUTSIDE_COVERAGE" in result.reasonCodes)
    }
    test("click intents and observed UI outcomes never merge into likes or posted comments") {
        val intent = InteractionObservation("intent-a", APP, InteractionAction.LIKE, 10, InteractionEvidence.INTENT_ONLY)
        val outcome = InteractionObservation("outcome-a", APP, InteractionAction.LIKE, 20, InteractionEvidence.OBSERVED_UI_OUTCOME)
        val comment = outcome.copy(eventId = "comment-a", action = InteractionAction.COMMENT_SUBMIT)
        val result = interactionCounts(APP, query, listOf(intent, intent, outcome, outcome, comment))
        equal(1L, result.intents[InteractionAction.LIKE]); equal(1L, result.observedOutcomes[InteractionAction.LIKE])
        equal(1L, result.observedOutcomes[InteractionAction.COMMENT_SUBMIT])
    }
    test("conflicting interaction identity, other app and boundary events withheld") {
        val event = InteractionObservation("id-a", APP, InteractionAction.LIKE, 10, InteractionEvidence.OBSERVED_UI_OUTCOME)
        val result = interactionCounts(APP, query, listOf(event, event.copy(action = InteractionAction.UNLIKE),
            event.copy(eventId = "id-b", appId = OTHER), event.copy(eventId = "id-c", occurredAtMs = 1000)))
        equal(1, result.conflictingIds); equal(emptyMap<InteractionAction, Long>(), result.observedOutcomes)
    }
}

private fun testNetwork() {
    test("network canonical bucket replay deduplicates received and sent bytes") {
        val result = network(listOf(bucket(), bucket(), bucket(HOUR, 2 * HOUR, 200, 40)))
        equal(300L, result.rxBytes); equal(60L, result.txBytes); equal(2, result.bucketCount)
        check(!result.partial)
    }
    test("missing network time stays partial with actual accounted bucket bounds") {
        val result = network(listOf(bucket()))
        check(result.partial && "REQUEST_NOT_FULLY_REPRESENTED" in result.reasons)
        equal(listOf(TimeRange(0, HOUR)), result.accountedBucketRanges)
        equal(100L, result.rxBytes)
    }
    test("partial network buckets excluded without invented time or surface allocation") {
        val result = network(listOf(bucket(), bucket(HOUR, 2 * HOUR, 200)), TimeRange(HOUR / 2, 2 * HOUR))
        equal(200L, result.rxBytes); check(result.partial); check("PARTIAL_BUCKET_EXCLUDED" in result.reasons)
    }
    test("permission, failed source, shared UID and no retained data remain unknown") {
        equal(null, network(listOf(bucket()), permission = false).rxBytes)
        equal(null, network(listOf(bucket()), success = false).rxBytes)
        equal(null, network(listOf(bucket()), unique = false).rxBytes)
        equal(null, network(emptyList()).rxBytes)
        equal(0L, network(listOf(bucket(rx = 0, tx = 0))).rxBytes)
    }
    test("network scope excludes transport and install-epoch mismatches") {
        val result = network(listOf(bucket(), bucket(uid = "reinstalled-uid"), bucket(transport = Transport.MOBILE)))
        equal(100L, result.rxBytes)
    }
    test("conflicting OS bucket snapshots cannot be added together") {
        val result = network(listOf(bucket(), bucket(rx = 150)))
        equal(null, result.rxBytes); check("CONFLICTING_BUCKET" in result.reasons)
    }
    test("overlapping network snapshots and aggregate-plus-foreground buckets rejected") {
        equal(null, network(listOf(bucket(), bucket(HOUR / 2, 2 * HOUR))).rxBytes)
        val all = bucket()
        val foreground = all.copy(key = all.key.copy(state = 2))
        check("OVERLAPPING_BUCKET_DIMENSIONS" in network(listOf(all, foreground)).reasons)
        val background = all.copy(key = all.key.copy(state = 1))
        equal(200L, network(listOf(background, foreground)).rxBytes)
    }
    test("network integer overflow fails closed rather than wrapping totals") {
        var failed = false
        try { network(listOf(bucket(rx = Long.MAX_VALUE), bucket(HOUR, 2 * HOUR, 1))) }
        catch (_: ArithmeticException) { failed = true }
        check(failed)
    }
}

private fun testRollups() {
    val first = fact("a", "2026-10-01T00:00:00Z", "2026-10-02T00:00:00Z", 1000)
    val second = fact("b", "2026-10-02T00:00:00Z", "2026-10-03T00:00:00Z", 500)
    test("monthly compaction deduplicates facts and preserves additive totals") {
        val result = rollupFacts(listOf(first, second, first), UTC, Granularity.MONTH).single()
        equal(1500L, result.coveredValue); equal(2L, result.contributors)
        equal(48 * HOUR, result.knownMs); equal(48 * HOUR, result.eligibleMs)
    }
    test("unknown facts remain unknown; partial rollup identifies lost coverage") {
        val unknown = second.copy(value = null, knownMs = 0)
        val result = rollupFacts(listOf(first, unknown), UTC, Granularity.MONTH).single()
        equal(1000L, result.coveredValue); equal(1L, result.unknownFactCount)
        equal(24 * HOUR, result.knownMs); equal(48 * HOUR, result.eligibleMs)
        equal(null, rollupFacts(listOf(unknown), UTC, Granularity.MONTH).single().coveredValue)
    }
    test("semantic revisions and evidence kinds cannot silently merge") {
        val result = rollupFacts(listOf(first, second.copy(semanticsRevision = "v2"),
            second.copy(id = "c", kind = EvidenceKind.OBSERVED)), UTC, Granularity.MONTH)
        equal(3, result.size)
    }
    test("fact conflicts, overlap and boundary crossing rejected before compaction") {
        rejects { rollupFacts(listOf(first, first.copy(value = 20)), UTC, Granularity.MONTH) }
        rejects { rollupFacts(listOf(first, first.copy(id = "duplicate-partition")), UTC, Granularity.MONTH) }
        rejects { rollupFacts(listOf(first.copy(range = TimeRange(at("2026-09-30T23:00:00Z"),
            at("2026-10-01T01:00:00Z")))), UTC, Granularity.MONTH) }
    }
    test("custom week-start and leap-year monthly/yearly boundaries") {
        val t = at("2026-10-27T12:00:00Z")
        equal(at("2026-10-26T00:00:00Z"), calendarBucket(t, UTC, Granularity.WEEK, DayOfWeek.MONDAY).startMs)
        equal(at("2026-10-25T00:00:00Z"), calendarBucket(t, UTC, Granularity.WEEK, DayOfWeek.SUNDAY).startMs)
        val leap = at("2024-02-15T00:00:00Z")
        equal(29 * 24 * HOUR, calendarBucket(leap, UTC, Granularity.MONTH, DayOfWeek.MONDAY).durationMs)
        equal(366 * 24 * HOUR, calendarBucket(leap, UTC, Granularity.YEAR, DayOfWeek.MONDAY).durationMs)
    }
    test("invalid values and duration coverage rejected") {
        rejects { first.copy(value = -1) }; rejects { first.copy(knownMs = first.range.durationMs + 1) }
        rejects { first.copy(eligibleMs = first.range.durationMs + 1) }
        rejects { first.copy(knownMs = 2, eligibleMs = 1) }
    }
    test("a year of 1000 daily exposures preserves totals without content history") {
        val start = LocalDate.parse("2025-01-01")
        val facts = (0L until 365).map { offset ->
            val date = start.plusDays(offset)
            fact("day-$offset", "${date}T00:00:00Z", "${date.plusDays(1)}T00:00:00Z", 1000)
        }
        equal(365_000L, rollupFacts(facts, UTC, Granularity.YEAR).single().coveredValue)
    }
    test("100000 exposures replayed twice retain exactly 100000 counts") {
        val observations = (0L until 100_000L).map { ordinal -> exposure(ordinal, ordinal) }
        equal(100_000L, exposureCount(APP, REELS, TimeRange(0, 100_000), true,
            listOf(TimeRange(0, 100_000)), observations + observations).value)
    }
    test("time equivalents use user-chosen arithmetic and preserve remainder") {
        equal(TimeEquivalent(8, 0, 30 * MINUTE), timeEquivalent(4 * HOUR, 30 * MINUTE))
        equal(TimeEquivalent(2, 5 * MINUTE, 30 * MINUTE), timeEquivalent(65 * MINUTE, 30 * MINUTE))
        rejects { timeEquivalent(10, 0) }; rejects { timeEquivalent(-1, 30) }
    }
}

private fun example() {
    println("\nSYNTHETIC MOBILE DIARY — not device data or implemented collection")
    val result = surfaceBreakdown(APP, TimeRange(0, 5 * HOUR), listOf(AppInterval(APP, TimeRange(0, 5 * HOUR))),
        listOf(SurfaceInterval(APP, REELS, TimeRange(0, 4 * HOUR)), SurfaceInterval(APP, "feed", TimeRange(4 * HOUR, 5 * HOUR))))
    val events = (0L until 1000).map { exposure(it, it * 10_000) }
    val count = exposureCount(APP, REELS, TimeRange(0, 5 * HOUR), true, listOf(TimeRange(0, 5 * HOUR)), events)
    println("App: ${result.appMs / HOUR} h; short-video surface: ${result.bySurfaceMs[REELS]!! / HOUR} h")
    println("Short-video exposures: ${count.value} (estimate; fixture-qualified only)")
    println("Likes / comment outcomes: unavailable — no qualified interaction adapter")
    println("Optional equivalent: ${timeEquivalent(4 * HOUR, 30 * MINUTE).completeSessions} chosen 30-minute sessions")
}

fun main(args: Array<String>) {
    testTime(); testSurfaces(); testCounts(); testNetwork(); testRollups()
    println("PASS: $passed calculation checks; no collector, UI, persistence or lifecycle verification")
    if ("--example" in args) example()
}
