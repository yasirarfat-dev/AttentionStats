package com.attentionstats.diary

import com.attentionstats.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/** Internal debug instrumentation seam. Display-only; never changes collectors or saved facts. */
internal object DiaryPresentationFixture {
    private data class Values(val appMs: String, val reelsMs: String, val count: String,
        val appName: String, val secondAppName: String?)
    @Volatile private var values: Values? = null
    fun set(appMs: String, reelsMs: String, count: String,
        appName: String = "Layout fixture", secondAppName: String? = null) {
        check(BuildConfig.DEBUG)
        check(listOf(appMs, reelsMs, count).all { it.toLongOrNull()?.let { number -> number >= 0 } == true })
        values = Values(appMs, reelsMs, count, appName, secondAppName)
    }
    fun clear() { values = null }
    fun apply(snapshot: String): String {
        if (!BuildConfig.DEBUG) return snapshot
        val current = values ?: return snapshot
        val value = JSONObject(snapshot)
        val days = value.getJSONArray("days")
        for (index in 0 until days.length()) {
            val apps = JSONArray().put(JSONObject()
            .put("packageName", "synthetic.layout.fixture").put("name", current.appName)
            .put("social", "instagram").put("appMs", current.appMs).put("reelsMs", current.reelsMs)
            .put("appOpens", current.count)
            .put("reelsCount", current.count).put("adCount", "0"))
            current.secondAppName?.let { name -> apps.put(JSONObject()
                .put("packageName", "synthetic.layout.second").put("name", name)
                .put("social", "facebook").put("appMs", "0").put("reelsMs", "0")
                .put("appOpens", "0")
                .put("reelsCount", "0").put("adCount", "0")) }
            days.getJSONObject(index).put("apps", apps)
        }
        return value.toString()
    }
}
