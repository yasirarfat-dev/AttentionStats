package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.ContextWrapper
import android.os.SystemClock
import com.attentionstats.protection.ProtectionCredentials
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.runBlocking

/** Real Android Keystore/AtomicFile/PBKDF2; synthetic secrets and UUID-owned files only. */
internal object ProtectionCredentialsVerification {
    fun run(instrumentation: Instrumentation): String = runBlocking {
        val target = instrumentation.targetContext
        val root = File(target.noBackupFilesDir, "protection-credentials-test-${UUID.randomUUID()}")
        check(root.mkdir())
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = root }
        val alias = "attentionstats.protection.test.${UUID.randomUUID()}"
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val owner = ProtectionCredentials(context, alias)
        val file = File(root, "protection-credentials-v1")
        var checks = 0
        fun expect(value: Boolean) { check(value) { "protection credentials check ${checks + 1}" }; checks++ }
        fun chars(value: String) = value.toCharArray()
        val empty = CharArray(0)
        var pin = "583194"
        var phrases = listOf("synthetic orchard silver", "synthetic river candle", "synthetic mountain copper")
        fun phraseValues() = phrases.map(::chars)
        fun waitRetry() { SystemClock.sleep(1100) }
        fun reason(operation: () -> Unit) = (runCatching(operation).exceptionOrNull() as? ProtectionCredentials.Denied)?.reason
        try {
            expect(!owner.state().enabled && owner.state().revision == "unset")
            expect(runCatching { owner.setup(chars("123"), chars("123"), phraseValues(), phraseValues()) }.isFailure)
            expect(!file.exists() && !keys.containsAlias(alias))
            val duplicates = List(3) { chars("synthetic repeated phrase") }
            expect(runCatching { owner.setup(chars(pin), chars(pin), duplicates, duplicates) }.isFailure)
            expect(!file.exists() && !keys.containsAlias(alias))
            val began = SystemClock.elapsedRealtime()
            owner.setup(chars(pin), chars(pin), phraseValues(), phraseValues())
            val setupMs = SystemClock.elapsedRealtime() - began
            val first = owner.state()
            expect(first.enabled && !first.biometric && keys.containsAlias(alias))
            expect(owner.verify(chars(pin)) == first.revision)
            val encoded = file.readBytes().toString(Charsets.ISO_8859_1)
            expect(!encoded.contains(pin) && phrases.none(encoded::contains))
            expect(runCatching { owner.setup(chars(pin), chars(pin), phraseValues(), phraseValues()) }.isFailure)
            expect(owner.state() == first)
            expect(reason { owner.verify(chars("111111")) } == "INCORRECT_SECRET")
            waitRetry()
            expect(ProtectionCredentials(context, alias).verify(chars(pin)) == first.revision)
            owner.change("pin", chars(pin), empty, chars("729461"), chars("729461"))
            pin = "729461"
            expect(owner.state().revision != first.revision && owner.state().enabled)
            expect(reason { owner.verify(chars("583194")) } == "INCORRECT_SECRET")
            waitRetry(); expect(owner.verify(chars(pin)) == owner.state().revision)
            for ((index, phrase) in phrases.withIndex()) {
                val nextPin = "74185${index}"
                owner.change("reset", empty, chars(phrase), chars(nextPin), chars(nextPin))
                pin = nextPin
                expect(owner.verify(chars(pin)) == owner.state().revision && !owner.state().biometric)
            }
            val beforeRotation = owner.state()
            val replacement = listOf("synthetic atlas garden", "synthetic ocean amber", "synthetic maple lantern")
            expect(reason { owner.change("phrases", chars(pin), chars("synthetic wrong phrase"), empty, empty,
                replacement.map(::chars), replacement.map(::chars)) } == "INCORRECT_SECRET")
            expect(owner.state() == beforeRotation)
            waitRetry()
            owner.change("phrases", chars(pin), chars(phrases[1]), empty, empty,
                replacement.map(::chars), replacement.map(::chars))
            phrases = replacement
            expect(owner.state().revision != beforeRotation.revision)
            expect(reason { owner.verify(chars("synthetic orchard silver"), true) } == "INCORRECT_SECRET")
            waitRetry(); expect(owner.verify(chars(phrases[2]), true) == owner.state().revision)
            val beforeCancel = owner.state()
            expect(runCatching { owner.change("pin", chars(pin), empty, chars("794215"), chars("794215"),
                beforeCommit = { error("synthetic cancellation before durable write") }) }.isFailure)
            expect(owner.state() == beforeCancel && owner.verify(chars(pin)) == beforeCancel.revision)
            val encodedBefore = file.readBytes()
            file.writeBytes(encodedBefore.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
            expect(runCatching { owner.state() }.isFailure)
            file.writeBytes(encodedBefore)
            expect(owner.state() == beforeCancel)
            repeat(5) { attempt ->
                expect(reason { owner.verify(chars("111111")) } == "INCORRECT_SECRET")
                if (attempt < 4) waitRetry()
            }
            expect(reason { ProtectionCredentials(context, alias).verify(chars(pin)) } == "TRY_LATER")
            val retained = file.readBytes()
            keys.deleteEntry(alias)
            expect(runCatching { owner.state() }.isFailure && file.readBytes().contentEquals(retained))
            "PASS: $checks isolated protection credential checks; setupMs=$setupMs; no live lock or history changes"
        } finally {
            if (keys.containsAlias(alias)) keys.deleteEntry(alias)
            check(!keys.containsAlias(alias))
            check(root.parentFile?.canonicalFile == target.noBackupFilesDir.canonicalFile)
            check(root.deleteRecursively() && !root.exists())
        }
    }
}
