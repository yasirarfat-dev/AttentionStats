package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import com.attentionstats.protection.ProtectionBiometric
import com.attentionstats.protection.ProtectionLockActivity
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual system prompt on the physical phone; requires the person's own biometric touch. */
internal object ProtectionBiometricVerification {
    fun run(instrumentation: Instrumentation): String {
        val context = instrumentation.targetContext
        fun main(action: () -> Unit) {
            var failure: Throwable? = null
            instrumentation.runOnMainSync { try { action() } catch (problem: Throwable) { failure = problem } }
            failure?.let { throw it }
        }
        val alias = "attentionstats.protection.test.biometric.${UUID.randomUUID()}"
        val biometric = ProtectionBiometric(context, alias)
        check(biometric.available()) { "Strong enrolled biometric required; no enrollment changed" }
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val activity = instrumentation.startActivitySync(Intent(context, ProtectionLockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ProtectionLockActivity
        var prompt: androidx.biometric.BiometricPrompt? = null
        try {
            biometric.provision()
            check(keys.containsAlias(alias))
            val cancelled = CountDownLatch(1)
            var cancellationAccepted = true
            main {
                prompt = biometric.prompt(activity) { success -> cancellationAccepted = success; cancelled.countDown() }
            }
            Thread.sleep(1000)
            main { prompt?.cancelAuthentication() }
            check(cancelled.await(10, TimeUnit.SECONDS) && !cancellationAccepted) { "Cancellation must not authorize" }
            Thread.sleep(750)
            val touched = CountDownLatch(1)
            var accepted = false
            main {
                prompt = biometric.prompt(activity) { success -> accepted = success; touched.countDown() }
            }
            instrumentation.sendStatus(1, Bundle().apply { putString("stream", "READY: touch the phone fingerprint sensor; synthetic key only, no lock/history changes") })
            check(touched.await(90, TimeUnit.SECONDS) && accepted) { "Actual biometric touch did not authorize the synthetic operation" }
            return "PASS: 3 actual strong biometric checks; owned key, cancellation rejected, real biometric cryptographic operation; no live protection changes"
        } finally {
            main { prompt?.cancelAuthentication(); activity.finish() }
            if (keys.containsAlias(alias)) keys.deleteEntry(alias)
            check(!keys.containsAlias(alias))
        }
    }
}
