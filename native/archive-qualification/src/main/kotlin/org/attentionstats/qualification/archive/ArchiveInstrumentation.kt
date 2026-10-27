package org.attentionstats.qualification.archive

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.Looper
import android.util.Base64
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.aead.AesGcmParameters
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingParameters
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.proto.EncryptedKeyset
import com.google.crypto.tink.proto.Keyset
import com.google.crypto.tink.proto.KeyData
import com.google.crypto.tink.proto.OutputPrefixType
import com.google.crypto.tink.shaded.protobuf.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/** Runs only synthetic primitive/framing checks. No activity, permissions or personal sources. */
class ArchiveInstrumentation : Instrumentation() {
    private var stage = "preflight"
    private var checks = 0
    private var rejections = 0
    private var roundTrips = 0
    private var acceptedCalls = 0
    private val result = Bundle()

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val vector = File(targetContext.noBackupFilesDir, "synthetic-vectors.json")
        try {
            check(!vector.exists())
            verify(vector)
            result.putInt("checks", checks)
            result.putInt("rejections", rejections)
            result.putInt("round_trips", roundTrips)
            result.putString("stream", "PASS: synthetic archive envelope qualification\n")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            vector.delete() // Only this fixed fixture-owned output; package is also removed by host.
            File(targetContext.noBackupFilesDir, "synthetic-large.json").delete()
            result.putString("stage", stage)
            result.putString("stream", "FAIL: ${failure.javaClass.simpleName}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun expect(condition: Boolean) { check(condition); checks++ }

    private fun reject(block: () -> Unit) {
        var rejected = false
        try { block() }
        catch (_: GeneralSecurityException) { rejected = true }
        catch (_: IllegalArgumentException) { rejected = true }
        catch (_: IOException) { rejected = true }
        expect(rejected)
        rejections++
    }

    private fun rejectArchive(bytes: ByteArray, credential: String) {
        val before = acceptedCalls
        reject { EnvelopeFixture.read(bytes, credential, accepted = { acceptedCalls++ }) }
        expect(acceptedCalls == before)
    }

    private fun hex(text: String): ByteArray = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun flip(bytes: ByteArray, offset: Int): ByteArray = bytes.copyOf().also {
        it[offset] = (it[offset].toInt() xor 1).toByte()
    }
    private fun payloadOffset(bytes: ByteArray): Int = 64 + ByteBuffer.wrap(bytes, 60, 4).int

    private fun rfcHkdf() {
        val repeated = ByteArray(22) { 0x0b }
        val vectors = listOf(
            listOf(repeated, hex("000102030405060708090a0b0c"), hex("f0f1f2f3f4f5f6f7f8f9"),
                hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865")),
            listOf(ByteArray(80) { it.toByte() }, ByteArray(80) { (it + 0x60).toByte() },
                ByteArray(80) { (it + 0xb0).toByte() },
                hex("b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71cc30c58179ec3e87c14c01d5c1f3434f1d87")),
            listOf(repeated, byteArrayOf(), byteArrayOf(),
                hex("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8")),
        )
        vectors.forEach { (ikm, salt, info, expected) ->
            expect(Hkdf.computeHkdf("HMACSHA256", ikm, salt, info, expected.size).contentEquals(expected))
        }
        result.putInt("hkdf_vectors", vectors.size)
    }

    private fun verify(vectorFile: File) {
        expect(Looper.myLooper() != Looper.getMainLooper())
        stage = "hkdf"
        rfcHkdf()
        stage = "credentials"
        val raw = EnvelopeFixture.randomBytes(32)
        val credential = EnvelopeFixture.encodeCredential(raw)
        try {
            expect(credential.length == 43)
            val decoded = EnvelopeFixture.decodeCredential(credential)
            expect(decoded.contentEquals(raw))
            decoded.fill(0)
            val canonicalZero = EnvelopeFixture.encodeCredential(ByteArray(32))
            for (bad in listOf("", credential + "=", credential + "\n", " " + credential,
                credential.dropLast(1), "!".repeat(43), canonicalZero.dropLast(1) + "B")) {
                reject { EnvelopeFixture.decodeCredential(bad) }
            }

            stage = "round_trips"
            val size = EnvelopeFixture.SEGMENT
            val lengths = listOf(0, 1, size - 57, size - 56, size - 55,
                2 * size - 72, 2 * size - 71, 3 * size + 7)
            val cases = JSONArray()
            var large = byteArrayOf()
            for (length in lengths) {
                val plaintext = ByteArray(length) { (it * 31 + it / 8192).toByte() }
                val archive = EnvelopeFixture.write(plaintext, credential)
                expect(EnvelopeFixture.read(archive, credential).contentEquals(plaintext))
                roundTrips++
                if (length == lengths.last()) large = archive
                if (length <= 1) cases.put(vectorCase("primitive-$length", archive, credential, plaintext))
            }
            // Fixture-only logical bytes: framing is inspectable; this is not the product schema/parser.
            val records = record(1, "{\"fixture\":\"synthetic-only\",\"entities\":\"1\"}") +
                record(2, "{\"id\":\"fixture-1\",\"value\":\"public artificial marker\"}") +
                record(3, "{\"entities\":\"1\"}")
            val small = EnvelopeFixture.write(records, credential)
            expect(EnvelopeFixture.read(small, credential).contentEquals(records))
            roundTrips++
            cases.put(vectorCase("synthetic-records", small, credential, records))

            stage = "envelope_mutations"
            rejectArchive(small, EnvelopeFixture.encodeCredential(ByteArray(32)))
            for (offset in 0 until payloadOffset(small)) rejectArchive(flip(small, offset), credential)
            for (length in 0..64) rejectArchive(small.copyOf(length), credential)
            for (length in listOf(0, 4097, -1)) {
                val invalid = small.copyOf()
                ByteBuffer.wrap(invalid, 60, 4).putInt(length)
                rejectArchive(invalid, credential)
            }
            val unavailable = small.copyOf()
            unavailable[12] = 2
            ByteBuffer.wrap(unavailable, 16, 12).putInt(65536).putInt(3).putInt(4)
            rejectArchive(unavailable, credential)
            rejectArchive(ByteArray(EnvelopeFixture.MAX_CIPHER + 1), credential)

            stage = "keyset_allowlist"
            val wrongParams = AesGcmHkdfStreamingParameters.builder().setKeySizeBytes(32)
                .setDerivedAesGcmKeySizeBytes(32)
                .setHkdfHashType(AesGcmHkdfStreamingParameters.HashType.SHA256)
                .setCiphertextSegmentSizeBytes(4096).build()
            val wrongHandle = KeysetHandle.generateNew(wrongParams, RegistryConfiguration.get())
            rejectArchive(EnvelopeFixture.write(records, credential, wrongHandle), credential)
            val multiple = KeysetHandle.newBuilder(EnvelopeFixture.newHandle()).addEntry(
                KeysetHandle.generateEntryFromParameters(EnvelopeFixture.parameters).withRandomId()
            ).build()
            rejectArchive(EnvelopeFixture.write(records, credential, multiple), credential)
            val otherType = KeysetHandle.generateNew(AesGcmParameters.builder().setKeySizeBytes(32)
                .setIvSizeBytes(12).setTagSizeBytes(16)
                .setVariant(AesGcmParameters.Variant.NO_PREFIX).build(), RegistryConfiguration.get())
            val prefix = small.copyOfRange(0, 60)
            val wrapped = TinkProtoKeysetFormat.serializeEncryptedKeyset(otherType,
                EnvelopeFixture.wrappingAead(credential, prefix),
                "AttentionStats/archive/v1/keyset\u0000".toByteArray(Charsets.US_ASCII) + prefix,
                RegistryConfiguration.get())
            val otherEnvelope = prefix + ByteBuffer.allocate(4).putInt(wrapped.size).array() + wrapped +
                small.copyOfRange(payloadOffset(small), small.size)
            rejectArchive(otherEnvelope, credential)
            val wireHandle = EnvelopeFixture.newHandle()
            val secret = TinkProtoKeysetFormat.serializeKeyset(wireHandle, InsecureSecretKeyAccess.get(),
                RegistryConfiguration.get())
            try {
                val originalSet = Keyset.parseFrom(secret)
                val entry = originalSet.getKey(0)
                for (badEntry in listOf(
                    entry.toBuilder().setOutputPrefixType(OutputPrefixType.TINK).build(),
                    entry.toBuilder().setKeyData(entry.keyData.toBuilder()
                        .setKeyMaterialType(KeyData.KeyMaterialType.ASYMMETRIC_PUBLIC)).build(),
                )) {
                    val altered = originalSet.toBuilder().setKey(0, badEntry).build().toByteArray()
                    try {
                        val cipher = EnvelopeFixture.wrappingAead(credential, prefix).encrypt(altered,
                            "AttentionStats/archive/v1/keyset\u0000".toByteArray(Charsets.US_ASCII) + prefix)
                        val wire = EncryptedKeyset.newBuilder().setEncryptedKeyset(ByteString.copyFrom(cipher))
                            .build().toByteArray()
                        val header = prefix + ByteBuffer.allocate(4).putInt(wire.size).array() + wire
                        // Valid payload under the same key and altered W/AAD: only wire admission rejects it.
                        rejectArchive(EnvelopeFixture.encryptPayload(header, wireHandle, records) {}, credential)
                    } finally { altered.fill(0) }
                }
            } finally { secret.fill(0) }

            stage = "stream_mutations"
            for (offset in payloadOffset(small) until small.size) rejectArchive(flip(small, offset), credential)
            for (length in 65 until small.size) rejectArchive(small.copyOf(length), credential)
            rejectArchive(small + byteArrayOf(0), credential)
            rejectArchive(small + ByteArray(16), credential)
            rejectArchive(small + small, credential)
            val start = payloadOffset(large)
            // First ciphertext segment includes its 40-byte stream header; remaining segments don't.
            val segments = (start until large.size step size).map { offset ->
                large.copyOfRange(offset, minOf(offset + size, large.size))
            }
            expect(segments.size == 4)
            val header = large.copyOfRange(0, start)
            fun reordered(order: List<Int>): ByteArray = header + order.fold(byteArrayOf()) { acc, i -> acc + segments[i] }
            rejectArchive(reordered(listOf(0, 2, 1, 3)), credential)
            rejectArchive(reordered(listOf(0, 1, 1, 2, 3)), credential)
            rejectArchive(reordered(listOf(0, 2, 3)), credential)
            rejectArchive(reordered(listOf(0, 1, 2)), credential)
            val otherLarge = EnvelopeFixture.secondStream(large, credential, ByteArray(3 * size + 7))
            val otherStart = payloadOffset(otherLarge)
            expect(otherLarge.copyOfRange(0, otherStart).contentEquals(header))
            rejectArchive(header + segments[0] + otherLarge.copyOfRange(otherStart + size, otherStart + 2 * size) +
                segments[2] + segments[3], credential)
            for (segment in 0..3) {
                val begin = start + segment * size
                val end = minOf(begin + size, large.size)
                for (offset in listOf(begin, (begin + end) / 2, end - 1)) rejectArchive(flip(large, offset), credential)
                for (length in listOf(begin, end - 1, end)) {
                    if (length < large.size) rejectArchive(large.copyOf(length), credential)
                }
            }
            expect(acceptedCalls == 0)
            val before = small.copyOf()
            EnvelopeFixture.read(small, credential, accepted = { acceptedCalls++ })
            expect(acceptedCalls == 1 && small.contentEquals(before))

            stage = "limits_cancellation"
            val mutable = small.copyOf()
            var mutationCalls = 0
            val immutableRead = EnvelopeFixture.read(mutable, credential, cancel = {
                if (++mutationCalls == 2) mutable.fill(0)
            })
            expect(immutableRead.contentEquals(records) && mutable.all { it == 0.toByte() })
            reject { EnvelopeFixture.read(large, credential, plaintextLimit = size) }
            reject { EnvelopeFixture.write(ByteArray(EnvelopeFixture.MAX_PLAIN + 1), credential) }
            var cancellations = 0
            for (at in listOf(1, 2, 132, 400)) {
                var calls = 0
                var cancelled = false
                try {
                    EnvelopeFixture.read(large, credential, cancel = {
                        if (++calls == at) throw CancellationException("Synthetic cancellation")
                    }, accepted = { error("Plaintext released after cancellation") })
                } catch (_: CancellationException) { cancelled = true }
                expect(cancelled)
                cancellations++
            }
            for (at in listOf(1, 2)) {
                var calls = 0
                var cancelled = false
                try {
                    EnvelopeFixture.write(records, credential, cancel = {
                        if (++calls == at) throw CancellationException("Synthetic cancellation")
                    })
                } catch (_: CancellationException) { cancelled = true }
                expect(cancelled)
                cancellations++
            }
            result.putInt("cancellations", cancellations)
            result.putInt("largest_plaintext", lengths.last())
            result.putInt("segments", segments.size)

            stage = "published_vectors"
            var goldenCases = 0
            check(targetContext.assets.list("")?.contains("generated-profile.json") == true)
            run {
                val golden = targetContext.assets.open("generated-profile.json").use {
                    JSONObject(String(it.readBytes(), Charsets.UTF_8))
                }.getJSONArray("cases")
                for (i in 0 until golden.length()) {
                    val case = golden.getJSONObject(i)
                    val bytes = Base64.decode(case.getString("envelopeBase64"), Base64.DEFAULT)
                    val read = EnvelopeFixture.read(bytes, case.getString("syntheticCredential"))
                    expect(digest(read) == case.getString("plaintextSha256"))
                    goldenCases++
                }
            }
            result.putInt("golden_cases", goldenCases)
            // Fixed output contains only public artificial vectors; host reads it before uninstall.
            val exported = JSONObject().put("scope", "PUBLIC SYNTHETIC TEST KEYS ONLY; NEVER USE FOR REAL DATA")
                .put("suite", "draft-v1-suite1-profile1").put("producer", "Tink Android 1.24.0")
                .put("cases", cases)
            val text = exported.toString(2)
            expect(text.toByteArray(Charsets.UTF_8).size < 16_384)
            vectorFile.writeText(text, Charsets.UTF_8)
            val largeCase = vectorCase("four-segments", large, credential, byteArrayOf())
                .removePlaintextForLarge(lengths.last(), digest(ByteArray(lengths.last()) {
                    (it * 31 + it / 8192).toByte()
                }))
            File(targetContext.noBackupFilesDir, "synthetic-large.json").writeText(
                JSONObject().put("scope", exported.getString("scope")).put("cases", JSONArray().put(largeCase))
                    .toString(), Charsets.UTF_8)
            result.putInt("vector_cases", cases.length())
        } finally { raw.fill(0) }
    }

    private fun record(kind: Int, json: String): ByteArray {
        val bytes = json.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(5).put(kind.toByte()).putInt(bytes.size).array() + bytes
    }

    private fun vectorCase(name: String, archive: ByteArray, credential: String, plain: ByteArray): JSONObject =
        JSONObject().put("name", name).put("syntheticCredential", credential)
            .put("envelopeBase64", Base64.encodeToString(archive, Base64.NO_WRAP))
            .put("plaintextBase64", Base64.encodeToString(plain, Base64.NO_WRAP))
            .put("plaintextSha256", digest(plain)).put("envelopeSha256", digest(archive))

    private fun JSONObject.removePlaintextForLarge(length: Int, sha256: String): JSONObject {
        remove("plaintextBase64")
        put("plaintextLength", length)
        put("plaintextSha256", sha256)
        return this
    }
}
