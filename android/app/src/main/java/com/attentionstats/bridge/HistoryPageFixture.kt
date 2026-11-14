package com.attentionstats.bridge

import com.attentionstats.BuildConfig
import com.facebook.react.bridge.Promise

/** Internal instrumentation seam. No JS/intent setter, persistence or release response. */
internal object HistoryPageFixture {
    data class Request(val start: String, val through: String, val end: String, val size: Int, val window: Int)
    @Volatile private var responder: ((Request, Promise) -> Unit)? = null
    fun install(value: (Request, Promise) -> Unit) { check(BuildConfig.DEBUG); responder = value }
    fun clear() { responder = null }
    fun respond(request: Request, promise: Promise): Boolean {
        if (!BuildConfig.DEBUG) return false
        val active = responder ?: return false
        active(request, promise)
        return true
    }
}
