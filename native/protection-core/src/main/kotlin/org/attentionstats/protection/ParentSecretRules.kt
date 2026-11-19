package org.attentionstats.protection

import java.security.MessageDigest
import java.text.Normalizer

/** Ways a person can prove parent authority. Only the first three are knowledge secrets. */
enum class ParentAuthority {
    PIN, PATTERN, PHRASE, STRONG_BIOMETRIC, WEAK_BIOMETRIC, DEVICE_CREDENTIAL;
    val knowledge get() = this == PIN || this == PATTERN || this == PHRASE
}

/** What the platform says satisfied a non-cryptographic prompt. */
enum class PromptResultType { BIOMETRIC, DEVICE_CREDENTIAL, UNKNOWN }

/**
 * Parent-chosen convenience unlock methods. [strong] is key-bound class-3 biometrics; [weak] admits
 * class-2 biometrics (face) and therefore every enrolled biometric; [device] admits the phone's own
 * screen-lock credential.
 */
data class UnlockMethods(val strong: Boolean = true, val weak: Boolean = false, val device: Boolean = false) {
    /** A weak prompt also accepts every strong biometric, so weak never survives strong being off. */
    fun normalized() = if (strong) this else copy(weak = false)
    fun widens(to: UnlockMethods): Boolean {
        val next = to.normalized(); val current = normalized()
        return (next.strong && !current.strong) || (next.weak && !current.weak) || (next.device && !current.device)
    }
    companion object { val NONE = UnlockMethods(strong = false, weak = false, device = false) }
}

/** Pure parent-secret rules shared by native storage, native UI validation and host tests. No Android types. */
object ParentSecretRules {
    const val PIN_MIN = 4
    const val PIN_MAX = 12
    const val PHRASE_MIN = 3
    const val PHRASE_MAX = 128
    /** Most backup phrases kept; only the first is required (ADR-120). */
    const val PHRASE_COUNT = 3
    const val PHRASE_MIN_COUNT = 1
    const val PATTERN_MIN = 4
    const val PATTERN_CELLS = 9
    /** DEVICE_CREDENTIAL alone needs API 30; on older releases it only combines with other authenticators. */
    const val DEVICE_CREDENTIAL_MIN_SDK = 30
    private const val PATTERN_PREFIX = "AttentionStats/pattern/v1:"

    fun validPin(value: CharArray) = value.size in PIN_MIN..PIN_MAX && value.all { it in '0'..'9' }

    /**
     * Case-sensitive NFC with trimmed and collapsed Java-regex whitespace (format 1 normalization; existing
     * 12+ character verifiers keep matching). Returns null outside [PHRASE_MIN]..[PHRASE_MAX]. Caller clears.
     */
    fun normalizePhrase(value: CharArray): CharArray? {
        if (value.size !in PHRASE_MIN..PHRASE_MAX) return null
        val normalized = Normalizer.normalize(String(value), Normalizer.Form.NFC).trim().replace(Regex("\\s+"), " ")
        return if (normalized.length in PHRASE_MIN..PHRASE_MAX) normalized.toCharArray() else null
    }

    enum class PhraseProblem { LENGTH, MISMATCH, DUPLICATE }

    /** Per-index first problem for the three setup phrases; null entries are acceptable. */
    fun phraseProblems(phrases: List<CharArray>, confirmations: List<CharArray>): List<PhraseProblem?> {
        require(phrases.size == confirmations.size)
        val normalized = phrases.map(::normalizePhrase)
        val repeated = confirmations.map(::normalizePhrase)
        try {
            return phrases.indices.map { i ->
                val value = normalized[i]
                when {
                    value == null -> PhraseProblem.LENGTH
                    (0 until i).any { j -> normalized[j]?.contentEquals(value) == true } -> PhraseProblem.DUPLICATE
                    repeated[i]?.contentEquals(value) != true -> PhraseProblem.MISMATCH
                    else -> null
                }
            }
        } finally { (normalized + repeated).forEach { it?.fill('\u0000') } }
    }

    /** The cell passed over between two cells of the 3x3 grid (row-major 0..8), if any. */
    fun middle(from: Int, to: Int): Int? {
        require(from in 0 until PATTERN_CELLS && to in 0 until PATTERN_CELLS)
        val rows = from / 3 + to / 3; val columns = from % 3 + to % 3
        if (rows % 2 != 0 || columns % 2 != 0) return null
        return (rows / 2 * 3 + columns / 2).takeIf { it != from && it != to }
    }

    /** Touch-drag step: a visited cell is ignored; an unvisited skipped middle cell is added first. */
    fun extend(path: IntArray, cell: Int): IntArray {
        require(cell in 0 until PATTERN_CELLS)
        if (cell in path) return path
        val between = path.lastOrNull()?.let { middle(it, cell) }?.takeIf { it !in path }
        return if (between == null) path + cell else path + between + cell
    }

    /** At least four distinct cells and no unvisited cell jumped over. */
    fun validPattern(path: IntArray): Boolean {
        if (path.size !in PATTERN_MIN..PATTERN_CELLS || path.any { it !in 0 until PATTERN_CELLS }) return false
        if (path.distinct().size != path.size) return false
        return (1 until path.size).all { i ->
            val skipped = middle(path[i - 1], path[i])
            skipped == null || (0 until i).any { path[it] == skipped }
        }
    }

    /** Domain-separated secret text used as the PBKDF2 password for the pattern verifier. Caller clears. */
    fun encodePattern(path: IntArray): CharArray {
        require(validPattern(path))
        return CharArray(PATTERN_PREFIX.length + path.size) { i ->
            if (i < PATTERN_PREFIX.length) PATTERN_PREFIX[i] else '1' + path[i - PATTERN_PREFIX.length]
        }
    }

    fun decodePattern(encoded: CharArray): IntArray? {
        if (encoded.size <= PATTERN_PREFIX.length) return null
        if (PATTERN_PREFIX.indices.any { encoded[it] != PATTERN_PREFIX[it] }) return null
        val digits = encoded.copyOfRange(PATTERN_PREFIX.length, encoded.size)
        try {
            if (digits.any { it !in '1'..'9' }) return null
            return IntArray(digits.size) { digits[it] - '1' }.takeIf(::validPattern)
        } finally { digits.fill('\u0000') }
    }

    /** Whether a set of 1..[PHRASE_COUNT] backup phrases has an acceptable size. */
    fun validPhraseCount(count: Int) = count in PHRASE_MIN_COUNT..PHRASE_COUNT

    /**
     * Stored phrases for an enabled lock. An app PIN or pattern always has at least one backup phrase; phone
     * fingerprint mode may have none (ADR-121), because a one-touch setup was wanted and a parent can still reset
     * it with the phone's own screen lock, which anyone adding a fingerprint already had to know.
     */
    fun validStoredPhraseCount(count: Int, keyless: Boolean) = count in (if (keyless) 0 else PHRASE_MIN_COUNT)..PHRASE_COUNT

    /**
     * Which proofs may authorize each security change. With a parent key, biometrics and the phone credential
     * never can, so they cannot widen unlock methods or replace parent secrets (sabotage protection).
     *
     * [keyless] is fingerprint-only mode (ADR-120): the parent chose the phone's fingerprint instead of an app
     * key, so a key-bound strong fingerprint (invalidated whenever anyone enrolls a new fingerprint) is the
     * parent's proof. Face and the phone credential still never authorize changes; a phrase still resets.
     */
    fun acceptsForChange(action: String, authority: ParentAuthority, keyless: Boolean = false, phrases: Int = 1): Boolean =
        if (keyless) when (action) {
            "reset" -> authority == ParentAuthority.PHRASE
            // Re-arm fingerprint approval after an enrollment change: a backup phrase, or with none saved the
            // phone's screen lock (ADR-121).
            "fingerprint-reset" -> authority == ParentAuthority.PHRASE || (phrases == 0 && authority == ParentAuthority.DEVICE_CREDENTIAL)
            "methods" -> authority == ParentAuthority.STRONG_BIOMETRIC || authority == ParentAuthority.PHRASE
            "pin", "pattern", "disable", "phrases" -> authority == ParentAuthority.STRONG_BIOMETRIC
            else -> false
        } else when (action) {
            "reset" -> authority == ParentAuthority.PHRASE
            "methods", "biometric-on", "biometric-off" -> authority.knowledge
            "pin", "pattern", "remove-pin", "remove-pattern", "disable", "phrases", "fingerprint-only" ->
                authority == ParentAuthority.PIN || authority == ParentAuthority.PATTERN
            else -> false
        }

    /** Whether replacing a secret should reset convenience methods: recovery, or replacing an existing kind. */
    fun clearsMethods(action: String, replacedExisting: Boolean) = action == "reset" || (action in setOf("pin", "pattern") && replacedExisting)

    /**
     * Ordinary single-action authorization. The weak route needs a still-valid enrollment-bound strong key
     * as its enrollment-change tripwire, because class-2 results cannot be bound to a Keystore key.
     */
    fun authorizesAction(authority: ParentAuthority, methods: UnlockMethods, strongKeyValid: Boolean, sdk: Int): Boolean {
        val allowed = methods.normalized()
        return when (authority) {
            ParentAuthority.PIN, ParentAuthority.PATTERN -> true
            ParentAuthority.PHRASE -> false
            ParentAuthority.STRONG_BIOMETRIC -> allowed.strong && strongKeyValid
            ParentAuthority.WEAK_BIOMETRIC -> allowed.weak && strongKeyValid
            ParentAuthority.DEVICE_CREDENTIAL -> allowed.device && sdk >= DEVICE_CREDENTIAL_MIN_SDK
        }
    }

    /** Biometric button route: the weak prompt when allowed (it also covers fingerprints), else key-bound strong. */
    fun biometricRoute(methods: UnlockMethods, strongKeyValid: Boolean, weakHardware: Boolean): ParentAuthority? {
        val allowed = methods.normalized()
        return when {
            allowed.weak && weakHardware && strongKeyValid -> ParentAuthority.WEAK_BIOMETRIC
            allowed.strong -> ParentAuthority.STRONG_BIOMETRIC
            else -> null
        }
    }

    /** A non-crypto success must report the type the prompt was limited to; UNKNOWN is tolerated on old releases. */
    fun resultMatches(authority: ParentAuthority, type: PromptResultType): Boolean = when (authority) {
        ParentAuthority.WEAK_BIOMETRIC -> type == PromptResultType.BIOMETRIC || type == PromptResultType.UNKNOWN
        ParentAuthority.DEVICE_CREDENTIAL -> type == PromptResultType.DEVICE_CREDENTIAL || type == PromptResultType.UNKNOWN
        else -> false
    }
}

/**
 * At most one outstanding non-crypto authorization challenge. Issuing replaces (revokes) any earlier one,
 * a challenge redeems at most once, and only for the purpose and authority it was created for.
 */
class OneUseChallenges(private val random: (ByteArray) -> Unit, private val lifetimeMs: Long = 120_000) {
    class Challenge internal constructor(val purpose: String, val authority: ParentAuthority, internal val nonce: ByteArray, internal val expires: Long)
    private var outstanding: Challenge? = null

    @Synchronized fun issue(purpose: String, authority: ParentAuthority, now: Long): Challenge {
        require(authority == ParentAuthority.WEAK_BIOMETRIC || authority == ParentAuthority.DEVICE_CREDENTIAL)
        revoke()
        val nonce = ByteArray(32).also(random)
        check(nonce.any { it != 0.toByte() })
        return Challenge(purpose, authority, nonce, now + lifetimeMs).also { outstanding = it }
    }

    @Synchronized fun redeem(challenge: Challenge, purpose: String, authority: ParentAuthority, now: Long): Boolean {
        val current = outstanding ?: return false
        if (!MessageDigest.isEqual(current.nonce, challenge.nonce)) return false
        outstanding = null
        val valid = current.purpose == purpose && current.authority == authority && now < current.expires
        current.nonce.fill(0)
        return valid
    }

    /** Revokes [only] when it is still outstanding, or everything when null. */
    @Synchronized fun revoke(only: Challenge? = null) {
        val current = outstanding ?: return
        if (only != null && only !== current) return
        current.nonce.fill(0); outstanding = null
    }

    @Synchronized fun pending() = outstanding != null
}
