package com.attentionstats.diary

import com.attentionstats.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/** Internal debug instrumentation seam. Display-only; never changes collectors or saved facts. */
internal object DiaryPresentationFixture {
    private data class Values(val appMs: String, val reelsMs: String, val count: String,
        val appName: String, val secondAppName: String?)
    @Volatile private var values: Values? = null
    @Volatile private var todayCase: String? = null
    fun today(case: String) {
        check(BuildConfig.DEBUG && case in setOf("zero", "typical", "large", "extreme"))
        todayCase = case
    }
    fun set(appMs: String, reelsMs: String, count: String,
        appName: String = "Layout fixture", secondAppName: String? = null) {
        check(BuildConfig.DEBUG)
        check(listOf(appMs, reelsMs, count).all { it.toLongOrNull()?.let { number -> number >= 0 } == true })
        values = Values(appMs, reelsMs, count, appName, secondAppName)
    }
    fun clear() { values = null; todayCase = null }
    fun applyReels(snapshot: String): String {
        if (!BuildConfig.DEBUG) return snapshot
        val case = todayCase ?: return snapshot
        val count = when (case) { "zero" -> "0"; "typical" -> "123"; "large" -> "999999"; else -> "9999999999999" }
        return JSONObject(snapshot).also { it.getJSONObject("session").put("count", count) }.toString()
    }
    fun apply(snapshot: String): String {
        if (!BuildConfig.DEBUG) return snapshot
        todayCase?.let { case ->
            val count = when (case) { "zero" -> 0L; "typical" -> 965L; "large" -> 999_999L; else -> 9_999_999_999_999L }
            val value = JSONObject(snapshot)
            value.put("usageEnabled", true).put("usageGranted", true)
            val days = value.getJSONArray("days")
            for (index in 0 until days.length()) {
                val apps = JSONArray()
                for ((position, social) in listOf("facebook", "instagram", "youtube", "tiktok", "snapchat").withIndex()) {
                    val n = if (position == 0) count else count / (position + 2)
                    apps.put(JSONObject().put("packageName", "synthetic.layout.$social").put("name", "Layout app ${position + 1}")
                        .put("social", social).put("appMs", if (n == 0L) "0" else if (case == "typical") "4260000" else "86340000")
                        .put("reelsMs", if (n == 0L) "0" else if (case == "typical") "3180000" else "86340000").put("appOpens", n.toString())
                        .put("reelsCount", n.toString()).put("videoCount", n.toString())
                        .put("adCount", if (n == 0L) "0" else if (case == "typical") "39" else n.toString())
                        .put("photoCount", if (n == 0L) "0" else if (case == "typical") "123" else n.toString())
                        .put("revisitCount", "0"))
                }
                days.getJSONObject(index).put("apps", apps).put("interventions", JSONObject().put("break.synthetic", count))
            }
            return value.toString()
        }
        val current = values ?: return snapshot
        val value = JSONObject(snapshot)
        val days = value.getJSONArray("days")
        for (index in 0 until days.length()) {
            val apps = JSONArray().put(JSONObject()
            .put("packageName", "synthetic.layout.fixture").put("name", current.appName)
            .put("social", "instagram").put("appMs", current.appMs).put("reelsMs", current.reelsMs)
            .put("appOpens", current.count)
            .put("reelsCount", current.count).put("adCount", "0").put("photoCount", "0").put("revisitCount", "0"))
            current.secondAppName?.let { name -> apps.put(JSONObject()
                .put("packageName", "synthetic.layout.second").put("name", name)
                .put("social", "facebook").put("appMs", "0").put("reelsMs", "0")
                .put("appOpens", "0")
                .put("reelsCount", "0").put("adCount", "0").put("photoCount", "0").put("revisitCount", "0")) }
            days.getJSONObject(index).put("apps", apps)
        }
        return value.toString()
    }
}
