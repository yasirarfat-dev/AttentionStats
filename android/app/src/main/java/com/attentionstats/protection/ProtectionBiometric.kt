package com.attentionstats.protection

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.Executor
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.attentionstats.protection.OneUseChallenges
import org.attentionstats.protection.ParentAuthority
import org.attentionstats.protection.ParentSecretRules
import org.attentionstats.protection.PromptResultType

/**
 * Per-use strong biometric cryptographic authorization, plus parent-enabled non-cryptographic routes (weak
 * biometrics, phone screen lock) bound to a one-use in-memory challenge for one authorization request.
 */
internal class ProtectionBiometric(private val context: Context,
    private val alias: String = "attentionstats.protection.biometric.v1") {
    private val manager get() = BiometricManager.from(context)
    fun available() = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    fun weakAvailable() = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    /** DEVICE_CREDENTIAL alone is only supported by BiometricPrompt on API 30+; older releases degrade to unavailable. */
    fun deviceAvailable() = Build.VERSION.SDK_INT >= ParentSecretRules.DEVICE_CREDENTIAL_MIN_SDK &&
        manager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    private fun key() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null) as? SecretKey
    /** False when the enrollment-bound key is missing or invalidated by an enrollment change. */
    fun keyValid(): Boolean = try {
        val key = key()
        if (key == null) false else { Cipher.getInstance("AES/GCM/NoPadding").init(Cipher.ENCRYPT_MODE, key); true }
    } catch (_: Exception) { false }
    fun delete() {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keys.containsAlias(alias)) keys.deleteEntry(alias)
    }
    fun provision() {
        check(available())
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keys.containsAlias(alias)) keys.deleteEntry(alias)
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true).setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= 30) spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        else {
            @Suppress("DEPRECATION")
            spec.setUserAuthenticationValidityDurationSeconds(-1)
        }
        KeyGenerator.getInstance("AES", "AndroidKeyStore").apply { init(spec.build()) }.generateKey()
    }
    /**
     * [negative] labels the prompt's own dismiss button; [onNegative] runs instead of a failure when it is tapped,
     * and [onDismiss] when the person simply closes the prompt (Back or tapping outside), which is not a failure.
     */
    fun prompt(activity: FragmentActivity, title: String = "Authorize protection change", negative: String = "Use PIN or pattern",
        onNegative: (() -> Unit)? = null, onDismiss: (() -> Unit)? = null, complete: (Boolean) -> Unit): BiometricPrompt {
        val key = key() ?: throw ProtectionCredentials.Denied("BIOMETRIC_REENABLE_REQUIRED")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        var delivered = false
        fun finish(success: Boolean) { if (!delivered) { delivered = true; complete(success) } }
        val prompt = BiometricPrompt(activity, Executor { activity.runOnUiThread(it) }, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON && onNegative != null && !delivered) { delivered = true; onNegative() }
                else if ((errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) &&
                    onDismiss != null && !delivered) { delivered = true; onDismiss() }
                else finish(false)
            }
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val success = try {
                    check(result.cryptoObject?.cipher === cipher)
                    cipher.doFinal(challenge).isNotEmpty()
                } catch (_: Exception) { false } finally { challenge.fill(0) }
                finish(success)
            }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title)
            .setSubtitle("Touch the fingerprint sensor")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(negative).build(), BiometricPrompt.CryptoObject(cipher))
        return prompt
    }

    /**
     * Non-cryptographic prompt limited to exactly one parent-allowed authenticator. Success counts only when the
     * reported type matches and the challenge issued for [purpose] is still outstanding; it is consumed once, so a
     * stale, replayed or late callback cannot authorize anything.
     */
    fun promptBound(activity: FragmentActivity, authority: ParentAuthority, challenges: OneUseChallenges, purpose: String,
        negative: String = "Use PIN or pattern", complete: (Boolean) -> Unit): BiometricPrompt {
        val allowed = when (authority) {
            ParentAuthority.WEAK_BIOMETRIC -> { check(weakAvailable()); BiometricManager.Authenticators.BIOMETRIC_WEAK }
            ParentAuthority.DEVICE_CREDENTIAL -> { check(deviceAvailable()); BiometricManager.Authenticators.DEVICE_CREDENTIAL }
            else -> throw IllegalArgumentException()
        }
        val challenge = challenges.issue(purpose, authority, SystemClock.elapsedRealtime())
        var delivered = false
        fun finish(success: Boolean) { if (!delivered) { delivered = true; complete(success) } }
        val prompt = BiometricPrompt(activity, Executor { activity.runOnUiThread(it) }, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { challenges.revoke(challenge); finish(false) }
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val type = when (result.authenticationType) {
                    BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC -> PromptResultType.BIOMETRIC
                    BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL -> PromptResultType.DEVICE_CREDENTIAL
                    else -> PromptResultType.UNKNOWN
                }
                val matches = result.cryptoObject == null && ParentSecretRules.resultMatches(authority, type)
                // Always present the challenge so it is consumed even when the type is wrong.
                val redeemed = challenges.redeem(challenge, purpose, authority, SystemClock.elapsedRealtime())
                finish(matches && redeemed)
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle("Parent approval").setAllowedAuthenticators(allowed)
        if (authority == ParentAuthority.WEAK_BIOMETRIC) info.setSubtitle("Use a parent's face or fingerprint").setNegativeButtonText(negative)
        else info.setSubtitle("Use the phone screen lock")
        try { prompt.authenticate(info.build()) } catch (failure: Exception) { challenges.revoke(challenge); throw failure }
        return prompt
    }
}
