package com.attentionstats.protection

import android.content.Context
import android.os.Build
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

/** Per-use strong biometric cryptographic authorization; never accepts the screen-lock PIN. */
internal class ProtectionBiometric(private val context: Context,
    private val alias: String = "attentionstats.protection.biometric.v1") {
    fun available() = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
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
    fun prompt(activity: FragmentActivity, complete: (Boolean) -> Unit): BiometricPrompt {
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null) as? SecretKey
            ?: throw ProtectionCredentials.Denied("BIOMETRIC_REENABLE_REQUIRED")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        var delivered = false
        fun finish(success: Boolean) { if (!delivered) { delivered = true; complete(success) } }
        val prompt = BiometricPrompt(activity, Executor { activity.runOnUiThread(it) }, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { finish(false) }
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val success = try {
                    check(result.cryptoObject?.cipher === cipher)
                    cipher.doFinal(challenge).isNotEmpty()
                } catch (_: Exception) { false } finally { challenge.fill(0) }
                finish(success)
            }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Authorize protection change")
            .setSubtitle("Use an enrolled parent biometric")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Use parental PIN").build(), BiometricPrompt.CryptoObject(cipher))
        return prompt
    }
}
