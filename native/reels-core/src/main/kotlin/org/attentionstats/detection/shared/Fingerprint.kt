package org.attentionstats.detection.shared

import org.attentionstats.detection.ScreenNode
import java.security.SecureRandom

/**
 * Turns the visible metadata of one item (author, caption, media description) into an
 * in-memory fingerprint. Strings are normalized so counters ("12K likes") and toggles
 * ("Follow"/"Following") do not change it. Raw strings are never stored; the salt is a
 * device-local secret, so fingerprints cannot be compared across devices.
 */
object Fingerprint {
    @Volatile private var salt: Long = SecureRandom().nextLong()

    /** The app seeds a device-local secret so the same item keeps its fingerprint across restarts. */
    fun seed(value: Long) { salt = value }
    private val digits = Regex("[0-9]+")
    private val punctuation = Regex("[\\p{P}\\p{S}&&[^@#_]]+")
    private val spaces = Regex("\\s+")
    private val toggles = listOf("follow", "subscribe", "like", "mute", "sound", "play", "pause", "save",
        "share", "comment", "repost", "remix", "more", "translation", "join", "add friend", "tap to")

    fun normalize(label: String): String? {
        val value = label.lowercase().replace(digits, " ").replace(punctuation, " ").replace(spaces, " ").trim()
        if (value.length < 3) return null
        if (value.length <= 24 && toggles.any { value.contains(it) }) return null
        return value
    }

    fun hash(value: String): Long {
        var h = -0x340d631b7bdddcdbL xor salt // FNV-1a 64 offset basis, salted per process.
        for (char in value) { h = (h xor char.code.toLong()) * 0x100000001b3L }
        return h
    }

    data class Print(val identity: Long, val keys: Set<Long>)

    /** Fingerprint from labelled nodes; null when nothing distinctive is visible. */
    fun of(nodes: Iterable<ScreenNode>): Print? {
        val keys = nodes.mapNotNull { node -> node.label?.let(::normalize) }.map(::hash).toSortedSet()
        if (keys.isEmpty()) return null
        var identity = 17L
        for (key in keys) identity = identity * 1_000_003 xor key
        return Print(identity, keys)
    }
}

/** Classification of sponsored items: view-ID hints first, then exact interface labels. */
object Sponsored {
    private val exact = setOf("sponsored", "ad", "ads", "promoted", "paid partnership", "publicidad", "patrocinado",
        "sponsorisé", "gesponsert", "sponsorizzato", "реклама", "प्रायोजित")
    private val idHints = listOf("sponsored", "_ad_", "ad_cta", "ads_", "_ads", "cta_container", "promoted")

    fun label(label: String?): Boolean {
        if (label == null || label.length > 40) return false
        val value = label.trim().lowercase()
        return value in exact || value.startsWith("sponsored") || value.startsWith("ad ·") ||
            value.startsWith("ad•") || value.startsWith("ad •") || value == "hide ad"
    }

    fun node(node: ScreenNode): Boolean = label(node.label) || node.id?.let { id -> idHints.any { id.contains(it) } } == true
}
