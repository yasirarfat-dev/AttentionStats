package com.attentionstats.protection

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import org.attentionstats.protection.ParentAuthority
import org.attentionstats.protection.ParentSecretRules
import org.attentionstats.protection.UnlockMethods
import org.json.JSONArray
import org.json.JSONObject

/**
 * Installation-local verifiers only. Never part of the logical archive or JS bridge.
 *
 * Format 1 record (all fields additive; older records stay readable): `enabled`, `biometric` (strong
 * key-bound biometrics allowed), optional `weak`/`device` method choices, optional `pin` and `pattern`
 * PBKDF2 verifiers (at least one while enabled), three `phrases` verifiers while enabled, `revision` and the
 * shared retry state. A disabled record keeps only revision/retry state; verifiers are deleted on disable.
 */
internal class ProtectionCredentials(private val context: Context,
    private val alias: String = "attentionstats.protection.credentials.v1") {
    val biometricAlias get() = "$alias.biometric"
    class Denied(val reason: String) : RuntimeException(reason)
    data class State(val enabled: Boolean, val biometric: Boolean, val revision: String,
        val pin: Boolean = false, val pattern: Boolean = false, val weak: Boolean = false, val device: Boolean = false) {
        val methods get() = UnlockMethods(biometric, weak, device).normalized()
    }
    /** One security change. Arrays are owned and cleared by the caller. */
    class Change(val action: String, val proof: CharArray, val proofKind: ParentAuthority,
        val oldPhrase: CharArray = CharArray(0), val replacement: CharArray = CharArray(0), val confirm: CharArray = CharArray(0),
        val replacementKind: ParentAuthority = ParentAuthority.PIN,
        val phrases: List<CharArray> = emptyList(), val confirmations: List<CharArray> = emptyList(),
        val methods: UnlockMethods? = null)
    private val file = AtomicFile(File(context.noBackupFilesDir, "protection-credentials-v1"))
    private val keys get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun bytes(value: String, size: Int) = Base64.decode(value, Base64.NO_WRAP).also { require(it.size == size) }
    private fun key() = keys.getKey(alias, null) as? SecretKey ?: throw Denied("KEY_UNAVAILABLE")
    private fun aad() = ("AttentionStats/ProtectionCredentials/v1/" + context.packageName + "/" + alias).toByteArray()
    private fun exists() = listOf("", ".bak", ".new").any { File(file.baseFile.path + it).exists() }
    private val verifierFields = listOf("pin", "pattern", "phrases")

    private fun read(): JSONObject? {
        if (!exists()) {
            if (keys.containsAlias(alias)) throw Denied("KEY_UNAVAILABLE")
            return null
        }
        val encrypted = file.openRead().use { input ->
            val buffer = ByteArray(8193)
            var size = 0
            while (size < buffer.size) { val count = input.read(buffer, size, buffer.size - size); if (count < 0) break; size += count }
            require(size in 29..8192)
            buffer.copyOf(size)
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encrypted.copyOfRange(0, 12)))
        cipher.updateAAD(aad())
        val plain = cipher.doFinal(encrypted, 12, encrypted.size - 12)
        return try {
            JSONObject(plain.toString(Charsets.UTF_8)).also {
                require(it.getInt("version") == 1)
                require(UUID.fromString(it.getString("revision")).toString() == it.getString("revision"))
                require(it.getInt("attempts") in 0..30)
                if (it.getBoolean("enabled")) {
                    require(it.getJSONArray("phrases").length() == ParentSecretRules.PHRASE_COUNT)
                    require(it.has("pin") || it.has("pattern"))
                }
            }
        } finally { plain.fill(0) }
    }

    private fun write(value: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key()); check(cipher.iv.size == 12); cipher.updateAAD(aad())
        val plain = value.toString().toByteArray()
        val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        val stream = file.startWrite()
        try { stream.write(cipher.iv); stream.write(encrypted); file.finishWrite(stream) }
        catch (failure: Exception) { file.failWrite(stream); throw failure }
    }
    private fun status(value: JSONObject?): State {
        if (value == null) return State(enabled = false, biometric = false, revision = "unset")
        val enabled = value.getBoolean("enabled")
        val biometric = value.getBoolean("biometric")
        return State(enabled, biometric, value.getString("revision"),
            pin = enabled && value.has("pin"), pattern = enabled && value.has("pattern"),
            weak = biometric && value.optBoolean("weak"), device = value.optBoolean("device"))
    }
    fun state(): State = synchronized(lock) {
        val value = read()
        // Records disabled before verifier deletion existed are purged on first read; authority is unchanged.
        if (value != null && !value.getBoolean("enabled") && verifierFields.any(value::has))
            runCatching { write(disabled(value, value.getString("revision"))); deleteBiometricKey() }
        status(value)
    }

    private fun derive(secret: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret, salt, 600_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
    }
    private fun verifier(secret: CharArray): JSONObject {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(secret, salt)
        return try { JSONObject().put("salt", b64(salt)).put("hash", b64(hash)) } finally { hash.fill(0) }
    }
    private fun matches(secret: CharArray, verifier: JSONObject): Boolean {
        val expected = bytes(verifier.getString("hash"), 32)
        val actual = derive(secret, bytes(verifier.getString("salt"), 16))
        return try { MessageDigest.isEqual(actual, expected) } finally { actual.fill(0); expected.fill(0) }
    }
    private fun phrase(value: CharArray): CharArray = requireNotNull(ParentSecretRules.normalizePhrase(value))
    private fun validatePin(value: CharArray) { require(ParentSecretRules.validPin(value)) }
    private fun validatePattern(value: CharArray) { require(ParentSecretRules.decodePattern(value) != null) }
    private fun validateNew(kind: ParentAuthority, value: CharArray, confirm: CharArray) {
        when (kind) {
            ParentAuthority.PIN -> validatePin(value)
            ParentAuthority.PATTERN -> validatePattern(value)
            else -> throw IllegalArgumentException()
        }
        require(value.contentEquals(confirm))
    }
    private fun field(kind: ParentAuthority) = if (kind == ParentAuthority.PIN) "pin" else "pattern"
    /** Validates three distinct confirmed phrases and returns their verifiers. */
    private fun phraseVerifiers(phrases: List<CharArray>, confirmations: List<CharArray>): JSONArray {
        require(phrases.size == ParentSecretRules.PHRASE_COUNT && confirmations.size == ParentSecretRules.PHRASE_COUNT)
        require(ParentSecretRules.phraseProblems(phrases, confirmations).all { it == null })
        val normalized = phrases.map(::phrase)
        try { return JSONArray(normalized.map(::verifier)) } finally { normalized.forEach { it.fill('\u0000') } }
    }

    /** Legacy PIN-only setup with no convenience methods; kept for existing callers and qualification. */
    fun setup(pin: CharArray, confirm: CharArray, phrases: List<CharArray>, confirmations: List<CharArray>, beforeCommit: () -> Unit = {}) =
        create(pin, confirm, null, null, phrases, confirmations, UnlockMethods.NONE, beforeCommit)

    /**
     * New lock with exactly one app secret, a PIN or an app pattern (encoded by [ParentSecretRules.encodePattern]),
     * three phrases and the chosen methods (ADR-117: one secret is easier to remember). The caller provisions the strong biometric key in [beforeCommit] when methods.strong.
     */
    fun create(pin: CharArray?, pinConfirm: CharArray?, pattern: CharArray?, patternConfirm: CharArray?,
        phrases: List<CharArray>, confirmations: List<CharArray>, methods: UnlockMethods, beforeCommit: () -> Unit = {}) = synchronized(lock) {
        require(android.os.Build.VERSION.SDK_INT >= 26)
        require(read()?.getBoolean("enabled") != true)
        require((pin == null) != (pattern == null))
        if (pin != null) validateNew(ParentAuthority.PIN, pin, requireNotNull(pinConfirm))
        if (pattern != null) validateNew(ParentAuthority.PATTERN, pattern, requireNotNull(patternConfirm))
        val chosen = methods.normalized()
        require(!chosen.device || android.os.Build.VERSION.SDK_INT >= ParentSecretRules.DEVICE_CREDENTIAL_MIN_SDK)
        val next = JSONObject().put("version", 1).put("enabled", true).put("biometric", chosen.strong)
            .put("weak", chosen.weak).put("device", chosen.device)
            .put("revision", UUID.randomUUID().toString())
            .put("phrases", phraseVerifiers(phrases, confirmations)).put("attempts", 0)
            .put("deadline", 0).put("boot", boot())
        if (pin != null) next.put("pin", verifier(pin))
        if (pattern != null) next.put("pattern", verifier(pattern))
        val generated = !keys.containsAlias(alias)
        if (generated) KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
        try { beforeCommit(); write(next) } catch (failure: Exception) {
            if (generated && !exists()) keys.deleteEntry(alias)
            throw failure
        }
    }
    private fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun delay(attempts: Int): Long = if (attempts < 5) 1000L else (30_000L shl (attempts - 5).coerceAtMost(5)).coerceAtMost(900_000L)
    private fun charge(value: JSONObject) {
        val now = SystemClock.elapsedRealtime()
        if (value.getInt("attempts") > 0 && (value.getInt("boot") != boot() ||
                now < value.getLong("deadline") - delay(value.getInt("attempts")))) {
            value.put("boot", boot()).put("deadline", now + delay(value.getInt("attempts")))
            write(value)
        }
        if (now < value.getLong("deadline")) throw Denied("TRY_LATER")
        val attempts = (value.getInt("attempts") + 1).coerceAtMost(30)
        value.put("attempts", attempts).put("deadline", now + delay(attempts)).put("boot", boot())
        write(value) // Charge before expensive verification, including a killed attempt.
    }
    /** PIN, pattern and phrase attempts share one persistent charge-before-verify throttle. */
    private fun authenticate(value: JSONObject, secret: CharArray, kind: ParentAuthority, clearAttempts: Boolean = true) {
        if (!value.getBoolean("enabled")) throw Denied("NOT_CONFIGURED")
        charge(value)
        val correct = when (kind) {
            ParentAuthority.PHRASE -> anyPhrase(value, secret)
            ParentAuthority.PIN -> value.has("pin") && ParentSecretRules.validPin(secret) && matches(secret, value.getJSONObject("pin"))
            ParentAuthority.PATTERN -> value.has("pattern") && ParentSecretRules.decodePattern(secret) != null &&
                matches(secret, value.getJSONObject("pattern"))
            else -> false
        }
        if (!correct) throw Denied("INCORRECT_SECRET")
        if (clearAttempts) { value.put("attempts", 0).put("deadline", 0); write(value) }
    }
    /** Checks all three verifiers without short-circuiting. Malformed input simply does not match. */
    private fun anyPhrase(value: JSONObject, secret: CharArray): Boolean {
        val normalized = ParentSecretRules.normalizePhrase(secret) ?: return false
        try {
            var result = false
            repeat(ParentSecretRules.PHRASE_COUNT) { result = matches(normalized, value.getJSONArray("phrases").getJSONObject(it)) or result }
            return result
        } finally { normalized.fill('\u0000') }
    }
    fun verify(secret: CharArray, recovery: Boolean = false): String =
        verify(secret, if (recovery) ParentAuthority.PHRASE else ParentAuthority.PIN)
    fun verify(secret: CharArray, kind: ParentAuthority): String = synchronized(lock) {
        require(kind.knowledge)
        val value = read() ?: throw Denied("NOT_CONFIGURED")
        authenticate(value, secret, kind); value.getString("revision")
    }
    private fun disabled(value: JSONObject, revision: String) = JSONObject().put("version", 1).put("enabled", false)
        .put("biometric", false).put("weak", false).put("device", false).put("revision", revision)
        .put("attempts", value.getInt("attempts")).put("deadline", value.getLong("deadline")).put("boot", value.getInt("boot"))
    private fun deleteBiometricKey() { val store = keys; if (store.containsAlias(biometricAlias)) store.deleteEntry(biometricAlias) }

    /** Legacy positional API: PIN proof, or a phrase proof for "reset". */
    fun change(action: String, currentPin: CharArray, oldPhrase: CharArray,
        replacement: CharArray, confirm: CharArray, phrases: List<CharArray> = emptyList(), confirmations: List<CharArray> = emptyList(), beforeCommit: () -> Unit = {}) =
        commit(if (action == "reset") Change(action, oldPhrase, ParentAuthority.PHRASE, replacement = replacement, confirm = confirm)
            else Change(action, currentPin, ParentAuthority.PIN, oldPhrase, replacement, confirm, phrases = phrases, confirmations = confirmations), beforeCommit)

    /** Each security change verifies its own knowledge proof; no reusable unlocked state. */
    fun commit(change: Change, beforeCommit: () -> Unit = {}) = synchronized(lock) {
        val action = change.action
        require(ParentSecretRules.acceptsForChange(action, change.proofKind))
        // Shape checks on new values happen before charging an attempt; they reveal nothing about stored secrets.
        when (action) {
            "reset", "pin", "pattern" -> validateNew(if (action == "reset") change.replacementKind else
                if (action == "pin") ParentAuthority.PIN else ParentAuthority.PATTERN, change.replacement, change.confirm)
            "phrases" -> require(ParentSecretRules.phraseProblems(change.phrases, change.confirmations).all { it == null })
            "methods" -> requireNotNull(change.methods)
        }
        val value = read() ?: throw Denied("NOT_CONFIGURED")
        authenticate(value, change.proof, change.proofKind, clearAttempts = action != "phrases")
        var next = value
        when (action) {
            "reset", "pin", "pattern" -> {
                val kind = if (action == "reset") change.replacementKind else if (action == "pin") ParentAuthority.PIN else ParentAuthority.PATTERN
                // One app secret (ADR-117): saving a PIN replaces a pattern and vice versa. Records from
                // earlier builds that hold both collapse to the new one.
                val replaced = value.has("pin") || value.has("pattern")
                value.put(field(kind), verifier(change.replacement))
                value.remove(field(if (kind == ParentAuthority.PIN) ParentAuthority.PATTERN else ParentAuthority.PIN))
                if (ParentSecretRules.clearsMethods(action, replaced)) value.put("biometric", false).put("weak", false).put("device", false)
            }
            "remove-pin" -> { require(value.has("pattern")); value.remove("pin") }
            "remove-pattern" -> { require(value.has("pin")); value.remove("pattern") }
            "disable" -> next = disabled(value, value.getString("revision")) // Verifiers are deleted, not retained.
            "biometric-on" -> {
                // Only after a verified strong prompt on a freshly provisioned key (see ProtectionLockActivity).
                val chosen = (change.methods ?: UnlockMethods(true, value.optBoolean("weak"), value.optBoolean("device"))).copy(strong = true)
                require(!chosen.device || android.os.Build.VERSION.SDK_INT >= ParentSecretRules.DEVICE_CREDENTIAL_MIN_SDK)
                value.put("biometric", true).put("weak", chosen.weak).put("device", chosen.device)
            }
            "biometric-off" -> value.put("biometric", false).put("weak", false)
            "methods" -> {
                val chosen = requireNotNull(change.methods).normalized()
                // Turning strong biometrics on needs a new key and a verified touch: use biometric-on.
                require(!chosen.strong || value.getBoolean("biometric"))
                require(!chosen.device || android.os.Build.VERSION.SDK_INT >= ParentSecretRules.DEVICE_CREDENTIAL_MIN_SDK)
                value.put("biometric", chosen.strong).put("weak", chosen.weak).put("device", chosen.device)
            }
            "phrases" -> {
                // PIN or pattern plus one existing phrase, checked in the same bounded operation.
                if (!anyPhrase(value, change.oldPhrase)) throw Denied("INCORRECT_SECRET")
                value.put("phrases", phraseVerifiers(change.phrases, change.confirmations))
            }
            else -> throw IllegalArgumentException()
        }
        beforeCommit(); next.put("revision", UUID.randomUUID().toString()).put("attempts", 0).put("deadline", 0); write(next)
        if (action == "disable") runCatching { deleteBiometricKey() }
    }
    companion object { private val lock = Any() }
}
