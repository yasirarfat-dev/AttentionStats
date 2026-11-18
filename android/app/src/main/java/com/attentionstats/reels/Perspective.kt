package com.attentionstats.reels

import java.util.Locale
import kotlin.math.floor

/** Shared, deterministic comparisons. These describe possibilities, never obligations. */
internal object Perspective {
    data class Line(val key: String, val text: String)
    const val PERSPECTIVE_ASSUMPTIONS = "These are estimates of possibilities, not measured activities: reading at 250 words per minute, 275 words per book page (1.1 min per page). Approximate book word counts: The Little Prince 17,000; The Old Man and the Sea 27,000; Animal Farm 30,000; The Alchemist 39,000; The Great Gatsby 47,000; Harry Potter and the Philosopher's Stone 77,000; Nineteen Eighty-Four 89,000; The Hobbit 95,000; To Kill a Mockingbird 100,000; Pride and Prejudice 122,000. Reading minutes = words / 250. Walking at 5 km/h (12 min per km), 1,300 steps per km; cycling at 16 km/h (3.75 min per km); 30 min per 5 km run; 20 min per workout; 15 min per language lesson; 60 min per hour of online course; 10 min per meditation; 30 min per instrument practice. Thumb travel assumes one 11 cm swipe per counted short video; actual swipes vary. Landmark estimates: city bus 12 m, blue whale 30 m, Statue of Liberty 93 m, football pitch 105 m, Eiffel Tower 330 m, Empire State Building 443 m, Burj Khalifa 828 m, marathon 42,195 m and Mount Everest 8,849 m. Recorded short-video time is on-screen time, not confirmed watching."

    private val books = listOf(
        "The Little Prince" to 17000,
        "The Old Man and the Sea" to 27000,
        "Animal Farm" to 30000,
        "The Alchemist" to 39000,
        "The Great Gatsby" to 47000,
        "Harry Potter and the Philosopher's Stone" to 77000,
        "Nineteen Eighty-Four" to 89000,
        "The Hobbit" to 95000,
        "To Kill a Mockingbird" to 100000,
        "Pride and Prejudice" to 122000,
    )
    private data class Landmark(val key: String, val metres: Int, val phrase: String)
    private val landmarks = listOf(
        Landmark("bus", 12, "a city bus"), Landmark("whale", 30, "a blue whale"),
        Landmark("liberty", 93, "the Statue of Liberty"), Landmark("pitch", 105, "a football pitch"),
        Landmark("eiffel", 330, "the Eiffel Tower"), Landmark("empire", 443, "the Empire State Building"),
        Landmark("burj", 828, "the Burj Khalifa"), Landmark("marathon", 42195, "a marathon"),
        Landmark("everest", 8849, "Mount Everest"),
    )

    fun quantity(q: Double): String = when {
        q < .3 -> "a quarter of"
        q < .4 -> "a third of"
        q < .6 -> "half"
        q < .85 -> "three quarters of"
        q < 1.15 -> "1"
        q < 10 -> String.format(Locale.US, "%.1f", q).removeSuffix(".0")
        else -> String.format(Locale.US, "%,.0f", floor(q + .5))
    }

    private fun integer(n: Double): String = String.format(Locale.US, "%,.0f", n)

    private fun achievementQuantity(q: Double): String = if (q < 10) {
        String.format(Locale.US, "%.1f", floor(q * 10 + .5) / 10).removeSuffix(".0")
    } else integer(floor(q + .5))

    fun achievements(ms: Long, count: Int, seed: Long): List<Line> {
        if (ms < 300_000 || count <= 0) return emptyList()
        val minutes = ms / 60_000.0
        val book = if (minutes >= 30) books.lastOrNull { minutes / (it.second / 250.0) >= .85 } else null
        val first = if (book != null) {
            val (title, words) = book
            val ratio = minutes / (words / 250.0)
            Line("book", when {
                ratio < 1.15 -> "read all of $title"
                ratio < 3 -> "read $title ${achievementQuantity(ratio)} times"
                else -> "read ${integer(floor(ratio))} books the length of $title"
            })
        } else Line("pages", "read ${integer(floor(minutes / 1.1 + .5))} pages")
        val candidates = mutableListOf<Line>()
        val km = minutes / 12
        if (km >= .5) candidates += Line("walk", "walked ${achievementQuantity(km)} km (about ${integer(floor(km * 1300 / 100 + .5) * 100)} steps)")
        val cycleKm = minutes / 3.75
        if (cycleKm >= 1) candidates += Line("cycle", "cycled ${achievementQuantity(cycleKm)} km")
        fun add(key: String, unitMinutes: Int, singular: String, plural: (String) -> String) {
            val n = minutes / unitMinutes
            if (n >= .85) candidates += Line(key, if (n < 1.15) singular else plural(achievementQuantity(n)))
        }
        add("run", 30, "run a full 5 km") { "run $it × 5 km" }
        add("workout", 20, "done a 20-minute workout") { "done $it 20-minute workouts" }
        add("language", 15, "taken a language lesson") { "taken $it language lessons" }
        add("course", 60, "finished an hour of an online course") { "finished $it hours of an online course" }
        add("meditate", 10, "meditated for 10 minutes") { "meditated $it times for 10 minutes" }
        add("instrument", 30, "practised an instrument for half an hour") { "practised an instrument $it times" }
        // Pages are already first when no book qualifies; never repeat or pair them with a book.
        val size = candidates.size
        val start = ((seed % size + size) % size).toInt()
        return (listOf(first) + candidates.drop(start) + candidates.take(start)).take(count)
    }

    /** Landmarks compared by length rather than height (kept in sync with perspective.ts). */
    private val lengths = setOf("bus", "whale", "pitch", "marathon")

    fun distance(videos: Long): Line? {
        val metres = videos * .11
        if (metres < 6) return null
        val landmark = landmarks.filter { metres >= .5 * it.metres }.maxByOrNull { it.metres } ?: return null
        val ratio = metres / landmark.metres
        val long = landmark.key in lengths
        val dimension = if (long) "length" else "height"
        val comparison = when {
            ratio >= 1.15 -> "${quantity(ratio)} times the $dimension of ${landmark.phrase}"
            ratio >= .85 -> if (landmark.key == "marathon") "a whole marathon"
                else "as ${if (long) "long" else "tall"} as ${landmark.phrase}"
            else -> "over half the $dimension of ${landmark.phrase}"
        }
        val travelled = if (metres >= 1_000) "${quantity(metres / 1_000)} km" else "${quantity(metres)} m"
        return Line(landmark.key, "$travelled of thumb scrolling · $comparison")
    }
}
