package com.attentionstats.widget

import org.json.JSONObject

/** A bounded, in-memory projection. No widget data is saved in plaintext preferences. */
internal data class WidgetData(
    val todayVideos: Long, val sessionVideos: Long, val watchingMs: Long?, val screenMs: Long?,
    val reelsAppsMs: Long?, val launches: Long?, val breaks: Long, val photos: Long, val ads: Long,
    val playbackMs: Long?, val focusRemainingMs: Long?, val observedApps: Long,
    val topVideoApps: List<Pair<String, Long>>, val topScreenApp: Pair<String, Long>?,
    val hours: List<Long>, val day: String, val updatedMs: Long,
) {
    companion object {
        /** Example projections are preview-only; never enter authoritative history or placed widgets. */
        fun example() = WidgetData(128, 12, 2_520_000, 14_400_000, 5_400_000, 36, 3, 24, 8, null, null, 9,
            listOf("Source One" to 64L, "Source Two" to 40L, "Source Three" to 24L), null,
            List(24) { if (it in 8..22) ((it * 7) % 23).toLong() else 0L },
            java.time.LocalDate.now().toString(), System.currentTimeMillis())

        fun fromSnapshot(snapshot: JSONObject, session: Long, focusRemaining: Long?, now: Long): WidgetData {
            val day = snapshot.getJSONArray("days").getJSONObject(0)
            val rows = day.getJSONArray("apps").let { values -> (0 until values.length()).map { values.getJSONObject(it) } }
            fun number(row: JSONObject, key: String) = row.optString(key).toLongOrNull()
            // Regional package rows share source-level facts: select the per-source maximum, never add duplicates.
            val social = rows.filter { !it.isNull("social") }.groupBy { it.getString("social") }
            fun sourceTotal(key: String): Long? {
                val values = social.values.mapNotNull { group -> group.mapNotNull { number(it, key) }.maxOrNull() }
                return values.takeIf { it.isNotEmpty() }?.sum()
            }
            val usageAvailable = snapshot.getBoolean("usageEnabled") && snapshot.getBoolean("usageGranted")
            fun appTotal(key: String, sourceOnly: Boolean = false): Long? {
                val values = rows.filter { !sourceOnly || !it.isNull("social") }.mapNotNull { number(it, key) }
                return if (usageAvailable && values.isNotEmpty()) values.sum() else null
            }
            val breaks = day.getJSONObject("interventions").let { values -> values.keys().asSequence()
                .filter { it.startsWith("break.") }.sumOf { values.getLong(it) } }
            val top = if (!usageAvailable) null else rows.mapNotNull { row -> number(row, "appMs")?.let { row.getString("name") to it } }.maxByOrNull { it.second }
            val names = mapOf("instagram" to "Instagram", "facebook" to "Facebook", "youtube" to "YouTube", "tiktok" to "TikTok", "snapchat" to "Snapchat")
            val sources = social.map { (key, group) -> (names[key] ?: "Source") to (group.mapNotNull { number(it, "videoCount") }.maxOrNull() ?: 0L) }.sortedByDescending { it.second }
            return WidgetData(sourceTotal("videoCount") ?: 0, session, sourceTotal("reelsMs"), appTotal("appMs"),
                appTotal("appMs", true), appTotal("appOpens"), breaks, sourceTotal("photoCount") ?: 0, sourceTotal("adCount") ?: 0,
                sourceTotal("playbackMs"), focusRemaining, rows.count { number(it, "appMs") != null || number(it, "appOpens") != null }.toLong(),
                sources, top, day.getJSONArray("hours").let { values -> (0 until 24).map { values.getString(it).toLong() } },
                day.getString("day"), now)
        }
    }
}
