package com.attentionstats.reels


/** Standalone comparisons against the shared table; no Android or personal history. */
fun main() {
    var checks = 0
    fun test(name: String, action: () -> Unit) {
        try { action() } catch (failure: Throwable) { throw AssertionError(name, failure) }
        checks++
    }
    val quantities = listOf(
        .25 to "a quarter of", .299999 to "a quarter of", .3 to "a third of", .399999 to "a third of",
        .4 to "half", .599999 to "half", .6 to "three quarters of", .849999 to "three quarters of",
        .85 to "1", 1.0 to "1", 1.149999 to "1", 1.15 to "1.2", 2.5 to "2.5", 3.0 to "3",
        9.99 to "10", 10.0 to "10", 10.49 to "10", 10.5 to "11", 1000.0 to "1,000",
        42195.0 to "42,195",
    )
    quantities.forEach { (q, expected) -> test("quantity $q") { check(Perspective.quantity(q) == expected) } }
    // Identical inputs and expected strings are asserted in perspective.test.ts.
    data class Sample(val minutes: Long, val seed: Long, val expected: List<String>)
    val samples = listOf(
        Sample(5, 0, listOf("read 5 pages", "cycled 1.3 km")),
        Sample(30, 2, listOf("read 27 pages", "run a full 5 km", "done 1.5 20-minute workouts", "taken 2 language lessons")),
        Sample(68, 0, listOf("read all of The Little Prince", "walked 5.7 km (about 7,400 steps)", "cycled 18 km", "run 2.3 × 5 km")),
        Sample(160, 1, listOf("read all of The Great Gatsby", "cycled 43 km", "run 5.3 × 5 km", "done 8 20-minute workouts")),
        Sample(480, 0, listOf("read all of Pride and Prejudice", "walked 40 km (about 52,000 steps)", "cycled 128 km", "run 16 × 5 km")),
        Sample(488000, 7, listOf("read 1,000 books the length of Pride and Prejudice", "practised an instrument 16,267 times", "walked 40,667 km (about 52,866,700 steps)", "cycled 130,133 km")),
    )
    samples.forEach { sample -> test("shared output ${sample.minutes} seed ${sample.seed}") {
        check(Perspective.achievements(sample.minutes * 60_000, 4, sample.seed).map { it.text } == sample.expected)
    } }
    fun all(minutes: Double) = Perspective.achievements((minutes * 60_000).toLong(), 100, 0)
    fun text(minutes: Double, key: String) = all(minutes).find { it.key == key }?.text
    test("book qualification, longest selection and repetition boundaries") {
        check(text(57.799, "pages") == "read 53 pages")
        check(text(57.8, "book") == "read all of The Little Prince")
        check(text(68.0, "book") == "read all of The Little Prince")
        check(text(78.2, "book") == "read The Little Prince 1.2 times")
        check(text(159.799, "book") == "read all of The Alchemist")
        check(text(159.8, "book") == "read all of The Great Gatsby")
        check(text(160.0, "book") == "read all of The Great Gatsby")
        check(text(1000.0, "book") == "read Pride and Prejudice 2 times")
        check(text(1464.0, "book") == "read 3 books the length of Pride and Prejudice")
        check(text(488000.0, "book") == "read 1,000 books the length of Pride and Prejudice")
    }
    test("eligibility and singular branches") {
        check(text(5.0, "walk") == null)
        check(text(6.0, "walk") == "walked 0.5 km (about 700 steps)")
        check(text(7.0, "walk") == "walked 0.6 km (about 800 steps)")
        check(text(25.499, "run") == null)
        check(text(25.5, "run") == "run a full 5 km")
        check(text(34.5, "run") == "run 1.2 × 5 km")
        check(text(16.999, "workout") == null)
        check(text(17.0, "workout") == "done a 20-minute workout")
        check(text(23.0, "workout") == "done 1.2 20-minute workouts")
        check(text(12.75, "language") == "taken a language lesson")
        check(text(51.0, "course") == "finished an hour of an online course")
        check(text(8.5, "meditate") == "meditated for 10 minutes")
        check(text(25.5, "instrument") == "practised an instrument for half an hour")
    }
    test("empty inputs and count") {
        for (ms in listOf(-1L, 0L, 299999L)) check(Perspective.achievements(ms, 4, 0).isEmpty())
        check(Perspective.achievements(300000, 0, 0).isEmpty())
        check(Perspective.achievements(300000, -1, 0).isEmpty())
    }
    test("first stays fixed, candidates rotate, no duplicate pages or book pairing") {
        for (minutes in listOf(5.0, 30.0, 60.0, 160.0, 480.0)) {
            val ms = (minutes * 60_000).toLong()
            val base = all(minutes)
            val candidates = base.drop(1)
            for (seed in -10L..19L) {
                val start = ((seed % candidates.size + candidates.size) % candidates.size).toInt()
                val result = Perspective.achievements(ms, 100, seed)
                check(result == listOf(base.first()) + candidates.drop(start) + candidates.take(start))
                check(result == Perspective.achievements(ms, 100, seed))
                check(Perspective.achievements(ms, 2, seed) == result.take(2))
                check(result.map { it.key }.distinct().size == result.size)
                check(!(result.any { it.key == "book" } && result.any { it.key == "pages" }))
            }
        }
    }
    val landmarks = listOf("bus" to 12, "whale" to 30, "liberty" to 93, "pitch" to 105,
        "eiffel" to 330, "empire" to 443, "burj" to 828, "marathon" to 42195, "everest" to 8849)
    test("distance minimum and all landmark thresholds choose the largest by metres") {
        check(Perspective.distance(-1) == null)
        check(Perspective.distance(0) == null)
        check(Perspective.distance(54) == null)
        check(Perspective.distance(55)?.key == "bus")
        for ((_, metres) in landmarks) for (ratio in listOf(.499, .5, .501, .849, .85, .851, 1.149, 1.15, 1.151, 2.0)) {
            val videos = (metres * ratio / .11).toLong()
            val distance = videos * .11
            val expected = landmarks.filter { distance >= it.second * .5 }.maxByOrNull { it.second }?.first
            check(Perspective.distance(videos)?.key == expected)
        }
    }
    test("each distance phrasing band and special landmark wording") {
        check(Perspective.distance(55)!!.text.contains("over half the length of a city bus"))
        check(Perspective.distance(100)!!.text.contains("as long as a city bus"))
        check(Perspective.distance(130)!!.text.contains("1.2 times the length of a city bus"))
        check(Perspective.distance(140)!!.text.contains("over half the length of a blue whale"))
        check(Perspective.distance(81_000)!!.text.contains("as tall as Mount Everest"))
        check(Perspective.distance(100_000)!!.text.contains("times the height of Mount Everest"))
        check(Perspective.distance(200_000)!!.text.contains("over half the length of a marathon"))
        check(Perspective.distance(383_600)!!.text.contains("a whole marathon"))
        check(Perspective.distance(500_000)!!.text.contains("times the length of a marathon"))
        check(Perspective.distance(Long.MAX_VALUE) != null)
    }
    test("output has no shaming or obligation words") {
        val forbidden = Regex("\\b(wasted|addicted|should|guilt)\\b", RegexOption.IGNORE_CASE)
        for (minutes in listOf(1, 5, 15, 60, 120, 360, 1000, 10000)) for (seed in 0L..5L) {
            check(Perspective.achievements(minutes * 60_000L, 6, seed).none { forbidden.containsMatchIn(it.text) })
        }
        check((0L..500_000L step 997).mapNotNull { Perspective.distance(it) }.none { forbidden.containsMatchIn(it.text) })
    }
    println("PASS: $checks synthetic perspective checks (host JVM; no Android/device verification)")
}
