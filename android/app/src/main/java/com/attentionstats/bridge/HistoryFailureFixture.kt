package com.attentionstats.bridge

import com.attentionstats.BuildConfig
import com.facebook.react.bridge.Promise

/** Instrumentation-only, process-memory failures. No store, preferences, intent or JS setter. */
internal object HistoryFailureFixture {
    enum class Case { BUSY, INTERRUPTED, STORAGE_UNAVAILABLE, UNKNOWN, PAYLOAD, JSON, VALIDATION }
    @Volatile private var active: Case? = null
    fun set(case: Case) { check(BuildConfig.DEBUG); active = case }
    fun clear() { active = null }
    fun respond(promise: Promise): Boolean {
        if (!BuildConfig.DEBUG) return false
        val case = active ?: return false
        when (case) {
            Case.BUSY, Case.INTERRUPTED, Case.STORAGE_UNAVAILABLE ->
                promise.reject(case.name, "synthetic-private-history")
            Case.UNKNOWN -> promise.reject("synthetic-private-code", "synthetic-private-history")
            Case.PAYLOAD -> promise.resolve(" ".repeat(2_000_001))
            Case.JSON -> promise.resolve("{\"synthetic-private-history\":")
            Case.VALIDATION -> promise.resolve("{\"synthetic-private-history\":true}")
        }
        return true
    }
}
