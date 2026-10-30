package com.attentionstats.reels

import org.attentionstats.reels.PageCategory
import org.attentionstats.reels.ReelApp

/** Serialized, memory-only unlock-session projection. Routes and app switches never reset it. */
internal class UnlockSessionCounter {
    private val apps = mutableMapOf<ReelApp, MutableMap<PageCategory, Long>>()
    fun clear() = apps.clear()
    fun clear(app: ReelApp) { apps.remove(app) }
    fun record(app: ReelApp, category: PageCategory) {
        val counts = apps.getOrPut(app) { mutableMapOf() }
        counts[category] = Math.addExact(counts[category] ?: 0, 1)
    }
    fun counts(app: ReelApp? = null): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        for (values in if (app == null) apps.values else listOfNotNull(apps[app]))
            for ((category, count) in values) result[category.key] = Math.addExact(result[category.key] ?: 0, count)
        return result
    }
    fun total(app: ReelApp): Long? = apps[app]?.values?.sum()
}
