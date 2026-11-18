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
import java.text.Normalizer
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import org.json.JSONArray
import org.json.JSONObject

/** Installation-local verifiers only. Never part of the logical archive or JS bridge. */
internal class ProtectionCredentials(private val context: Context,
    private val alias: String = "attentionstats.protection.credentials.v1") {
    val biometricAlias get() = "$alias.biometric"
    class Denied(val reason: String) : RuntimeException(reason)
    data class State(val enabled: Boolean, val biometric: Boolean, val revision: String)
    private val file = AtomicFile(File(context.noBackupFilesDir, "protection-credentials-v1"))
    private val keys get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun bytes(value: String, size: Int) = Base64.decode(value, Base64.NO_WRAP).also { require(it.size == size) }
    private fun key() = keys.getKey(alias, null) as? SecretKey ?: throw Denied("KEY_UNAVAILABLE")
    private fun aad() = ("AttentionStats/ProtectionCredentials/v1/" + context.packageName + "/" + alias).toByteArray()
    private fun exists() = listOf("", ".bak", ".new").any { File(file.baseFile.path + it).exists() }

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
                require(it.getInt("version") == 1 && it.getJSONArray("phrases").length() == 3)
                require(UUID.fromString(it.getString("revision")).toString() == it.getString("revision"))
                require(it.getInt("attempts") in 0..30)
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
    private fun status(value: JSONObject?) = State(value?.getBoolean("enabled") ?: false,
        value?.getBoolean("biometric") ?: false, value?.getString("revision") ?: "unset")
    fun state(): State = synchronized(lock) { status(read()) }

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
    private fun phrase(value: CharArray): CharArray {
        require(value.size in 12..128)
        return Normalizer.normalize(String(value), Normalizer.Form.NFC).trim().replace(Regex("\\s+"), " ")
            .also { require(it.length in 12..128) }.toCharArray()
    }
    private fun validatePin(value: CharArray) { require(value.size in 6..12 && value.all { it in '0'..'9' }) }

    fun setup(pin: CharArray, confirm: CharArray, phrases: List<CharArray>, confirmations: List<CharArray>, beforeCommit: () -> Unit = {}) = synchronized(lock) {
        require(android.os.Build.VERSION.SDK_INT >= 26)
        require(read()?.getBoolean("enabled") != true)
        validatePin(pin); require(pin.contentEquals(confirm) && phrases.size == 3 && confirmations.size == 3)
        val normalized = phrases.map(::phrase)
        val repeated = confirmations.map(::phrase)
        try {
            require(normalized.indices.all { normalized[it].contentEquals(repeated[it]) })
            require(normalized.indices.all { i -> (0 until i).none { normalized[i].contentEquals(normalized[it]) } })
            val next = JSONObject().put("version", 1).put("enabled", true).put("biometric", false)
                .put("revision", UUID.randomUUID().toString()).put("pin", verifier(pin))
                .put("phrases", JSONArray(normalized.map(::verifier))).put("attempts", 0)
                .put("deadline", 0).put("boot", boot())
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
        } finally { normalized.forEach { it.fill('\u0000') }; repeated.forEach { it.fill('\u0000') } }
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
    private fun authenticate(value: JSONObject, secret: CharArray, recovery: Boolean, clearAttempts: Boolean = true) {
        charge(value)
        val correct = if (recovery) {
            val normalized = phrase(secret)
            try {
                var result = false
                repeat(3) { result = matches(normalized, value.getJSONArray("phrases").getJSONObject(it)) or result }
                result
            } finally { normalized.fill('\u0000') }
        } else { validatePin(secret); matches(secret, value.getJSONObject("pin")) }
        if (!correct) throw Denied("INCORRECT_SECRET")
        if (clearAttempts) { value.put("attempts", 0).put("deadline", 0); write(value) }
    }
    fun verify(secret: CharArray, recovery: Boolean = false): String = synchronized(lock) {
        val value = read() ?: throw Denied("NOT_CONFIGURED")
        authenticate(value, secret, recovery); value.getString("revision")
    }
    /** Each security change verifies its own credentials; no reusable unlocked state. */
    fun change(action: String, currentPin: CharArray, oldPhrase: CharArray,
        replacement: CharArray, confirm: CharArray, phrases: List<CharArray> = emptyList(), confirmations: List<CharArray> = emptyList(), beforeCommit: () -> Unit = {}) = synchronized(lock) {
        val value = read() ?: throw Denied("NOT_CONFIGURED")
        if (action == "reset") authenticate(value, oldPhrase, true) else authenticate(value, currentPin, false, clearAttempts = action != "phrases")
        when (action) {
            "reset", "pin" -> { validatePin(replacement); require(replacement.contentEquals(confirm)); value.put("pin", verifier(replacement)); value.put("biometric", false) }
            "disable" -> value.put("enabled", false).put("biometric", false)
            "biometric-on" -> { require(value.getBoolean("enabled")); value.put("biometric", true) }
            "biometric-off" -> value.put("biometric", false)
            "phrases" -> {
                // PIN plus one existing phrase, checked in the same bounded operation.
                val normalized = phrase(oldPhrase)
                val next = phrases.map(::phrase); val repeated = confirmations.map(::phrase)
                try {
                    var correct = false
                    repeat(3) { correct = matches(normalized, value.getJSONArray("phrases").getJSONObject(it)) or correct }
                    if (!correct) throw Denied("INCORRECT_SECRET")
                    require(next.size == 3 && repeated.size == 3 && next.indices.all { next[it].contentEquals(repeated[it]) })
                    require(next.indices.all { i -> (0 until i).none { next[i].contentEquals(next[it]) } })
                    value.put("phrases", JSONArray(next.map(::verifier)))
                } finally { normalized.fill('\u0000'); next.forEach { it.fill('\u0000') }; repeated.forEach { it.fill('\u0000') } }
            }
            else -> throw IllegalArgumentException()
        }
        beforeCommit(); value.put("revision", UUID.randomUUID().toString()).put("attempts", 0).put("deadline", 0); write(value)
    }
    companion object { private val lock = Any() }
}
