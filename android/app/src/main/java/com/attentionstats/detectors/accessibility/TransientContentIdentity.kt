package com.attentionstats.detectors.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.attentionstats.reels.ReelApp

/** Notice-4 TikTok matching only. No raw value, token or key enters diagnostics/history. */
internal class TransientContentIdentity {
    private data class Scope(val app: ReelApp, val window: Int, val epoch: Long)
    private var scope: Scope? = null
    private var generation = 0L
    private var key = ByteArray(0)
    private var previous: ByteArray? = null
    private var ordinal = -1L

    @Synchronized fun reset() {
        key.fill(0); key = ByteArray(0)
        previous?.fill(0); previous = null
        scope = null; ordinal = -1; generation++
    }

    @Synchronized fun begin(app: ReelApp, window: Int, epoch: Long): Collector {
        check(app == ReelApp.TIKTOK)
        val next = Scope(app, window, epoch)
        if (scope != next) {
            reset(); scope = next
            key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        }
        return Collector(this, generation, SecretKeySpec(key, "HmacSHA256"))
    }

    @Synchronized private fun commit(ticket: Long, digest: ByteArray): Long? {
        if (ticket != generation || scope == null) return null
        if (previous?.let { MessageDigest.isEqual(it, digest) } != true) {
            previous?.fill(0); previous = digest.copyOf()
            ordinal = Math.addExact(ordinal, 1)
        }
        return ordinal
    }

    class Collector internal constructor(private val owner: TransientContentIdentity,
        private val ticket: Long, private val secret: SecretKeySpec) {
        private val fields = mutableMapOf<String, ByteArray>()
        private var invalid = false
        private var finished = false
        private fun mac(): Mac = Mac.getInstance("HmacSHA256").apply { init(secret) }

        fun add(id: String?, node: AccessibilityNodeInfo) {
            if (finished || id !in setOf("title", "desc") ||
                node.packageName?.toString() != ReelApp.TIKTOK.packageName ||
                !node.isVisibleToUser || node.isEditable || node.isPassword) return
            val field = checkNotNull(id)
            if (field in fields) { invalid = true; return }
            // Inspect only these two named, visible fields; never convert or retain their value.
            val value = node.text?.takeIf { it.isNotEmpty() } ?: node.contentDescription ?: return
            if (value.length !in 1..4096 || value.none { !it.isWhitespace() }) { invalid = true; return }
            val digest = mac()
            digest.update(if (field == "title") 1.toByte() else 2.toByte())
            digest.update((value.length ushr 8).toByte()); digest.update(value.length.toByte())
            for (index in 0 until value.length) {
                val code = value[index].code
                digest.update((code ushr 8).toByte()); digest.update(code.toByte())
            }
            fields[field] = digest.doFinal()
        }

        fun finish(eligible: Boolean): Long? {
            check(!finished); finished = true
            try {
                // Author-only or missing-caption layouts cannot identify video transitions.
                if (!eligible || invalid || "desc" !in fields) return null
                val combined = mac()
                for (field in listOf("title", "desc")) {
                    combined.update(if (field in fields) 1.toByte() else 0.toByte())
                    fields[field]?.let { combined.update(it) }
                }
                val digest = combined.doFinal()
                return try { owner.commit(ticket, digest) } finally { digest.fill(0) }
            } finally { fields.values.forEach { it.fill(0) }; fields.clear() }
        }
    }
}
