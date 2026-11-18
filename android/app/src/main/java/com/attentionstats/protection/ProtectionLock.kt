package com.attentionstats.protection

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Native-only action continuations. Nothing reusable is returned to JavaScript. */
internal object ProtectionLock {
    val worker = Executors.newSingleThreadExecutor()
    val changes = Mutex()
    private val main = Handler(Looper.getMainLooper())
    private data class Request(val credentials: ProtectionCredentials, val label: String, val revision: String, val expires: Long,
        val success: (String, Long) -> Unit, val failure: (String) -> Unit)
    private val pending = mutableMapOf<String, Request>()
    fun request(context: Context, label: String, failure: (String) -> Unit, credentials: ProtectionCredentials = ProtectionCredentials(context), success: (String, Long) -> Unit) {
        worker.execute {
            try {
                val state = credentials.state()
                if (!state.enabled) { success(state.revision, SystemClock.elapsedRealtime() + 120_000); return@execute }
                val token = UUID.randomUUID().toString()
                main.post {
                    if (pending.isNotEmpty()) { failure("AUTH_BUSY"); return@post }
                    pending[token] = Request(credentials, label, state.revision, SystemClock.elapsedRealtime() + 120_000, success, failure)
                    try { context.startActivity(Intent(context, ProtectionLockActivity::class.java)
                        .putExtra("request", token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (_: Exception) { finish(token, null) }
                    main.postDelayed({ if (pending.containsKey(token)) finish(token, null) }, 120_000)
                }
            } catch (_: Exception) { failure("PROTECTION_LOCK_UNAVAILABLE") }
        }
    }
    fun credentials(token: String): ProtectionCredentials? = pending[token]?.takeIf { SystemClock.elapsedRealtime() < it.expires }?.credentials
    fun label(token: String): String? = pending[token]?.takeIf { SystemClock.elapsedRealtime() < it.expires }?.label
    fun finish(token: String, verifiedRevision: String?) {
        check(Looper.myLooper() == Looper.getMainLooper())
        val request = pending.remove(token) ?: return
        if (verifiedRevision == request.revision && SystemClock.elapsedRealtime() < request.expires) request.success(verifiedRevision, request.expires)
        else request.failure("AUTH_CANCELLED")
    }
    suspend fun execute(context: Context, revision: String, expires: Long, operation: suspend () -> Unit) = execute(ProtectionCredentials(context), revision, expires, operation)
    suspend fun execute(credentials: ProtectionCredentials, revision: String, expires: Long, operation: suspend () -> Unit) = changes.withLock {
        if (SystemClock.elapsedRealtime() >= expires) throw ProtectionCredentials.Denied("AUTH_EXPIRED")
        val current = try { credentials.state() }
            catch (_: Exception) { throw ProtectionCredentials.Denied("PROTECTION_LOCK_UNAVAILABLE") }
        if (current.revision != revision) throw ProtectionCredentials.Denied("AUTH_STALE")
        operation()
    }
}
