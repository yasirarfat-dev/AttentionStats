package org.attentionstats.qualification.archive

import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeyStatus
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKey
import com.google.crypto.tink.aead.AesGcmParameters
import com.google.crypto.tink.proto.AesGcmHkdfStreamingKey as StreamingKeyProto
import com.google.crypto.tink.proto.HashType
import com.google.crypto.tink.proto.KeyData
import com.google.crypto.tink.proto.Keyset
import com.google.crypto.tink.proto.KeyStatusType
import com.google.crypto.tink.proto.OutputPrefixType
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingKey
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingParameters
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.util.SecretBytes
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom

/** In-memory synthetic envelope ONLY. No history parser, provider, Keystore or production API. */
internal object EnvelopeFixture {
    const val SEGMENT = 1_048_576
    const val MAX_PLAIN = 4 * SEGMENT
    const val MAX_CIPHER = MAX_PLAIN + 16_384
    private val magic = byteArrayOf(0x41, 0x54, 0x54, 0x4e, 0x41, 0x52, 0x43, 0)
    private val keysetDomain = "AttentionStats/archive/v1/keyset\u0000".toByteArray(Charsets.US_ASCII)
    private val payloadDomain = "AttentionStats/archive/v1/payload\u0000".toByteArray(Charsets.US_ASCII)
    private val wrapInfo = "AttentionStats/archive/v1/wrap".toByteArray(Charsets.US_ASCII)
    private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    val parameters: AesGcmHkdfStreamingParameters = AesGcmHkdfStreamingParameters.builder()
        .setKeySizeBytes(32).setDerivedAesGcmKeySizeBytes(32)
        .setHkdfHashType(AesGcmHkdfStreamingParameters.HashType.SHA256)
        .setCiphertextSegmentSizeBytes(SEGMENT).build()

    init {
        AeadConfig.register()
        StreamingAeadConfig.register()
    }

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }
    fun encodeCredential(raw: ByteArray): String {
        require(raw.size == 32)
        return Base64.encodeToString(raw, BASE64_FLAGS)
    }

    fun decodeCredential(text: String): ByteArray {
        require(Regex("[A-Za-z0-9_-]{43}").matches(text))
        val raw = Base64.decode(text, BASE64_FLAGS)
        if (raw.size != 32 || encodeCredential(raw) != text) {
            raw.fill(0)
            throw GeneralSecurityException("Invalid fixture credential")
        }
        return raw
    }

    fun wrappingAead(credential: String, prefix: ByteArray): Aead {
        val raw = decodeCredential(credential)
        val derived = try {
            Hkdf.computeHkdf("HMACSHA256", raw, prefix.copyOfRange(28, 44), wrapInfo, 32)
        } finally { raw.fill(0) }
        try {
            val key = AesGcmKey.builder().setParameters(AesGcmParameters.builder()
                .setKeySizeBytes(32).setIvSizeBytes(12).setTagSizeBytes(16)
                .setVariant(AesGcmParameters.Variant.NO_PREFIX).build())
                .setKeyBytes(SecretBytes.copyFrom(derived, InsecureSecretKeyAccess.get())).build()
            return KeysetHandle.newBuilder().addEntry(
                KeysetHandle.importKey(key).withRandomId().makePrimary()
            ).build().getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        } finally { derived.fill(0) }
    }

    fun newHandle(): KeysetHandle = KeysetHandle.generateNew(parameters, RegistryConfiguration.get())

    private fun keysetReaderAead(credential: String, prefix: ByteArray): Aead {
        val delegate = wrappingAead(credential, prefix)
        return object : Aead by delegate {
            override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
                val authenticated = delegate.decrypt(ciphertext, associatedData)
                try {
                    // Validate the authenticated wire representation BEFORE Tink normalizes it.
                    require(authenticated.size <= 4096)
                    val set = Keyset.parseFrom(authenticated)
                    require(set.keyCount == 1)
                    val entry = set.getKey(0)
                    require(entry.keyId == set.primaryKeyId && entry.status == KeyStatusType.ENABLED)
                    require(entry.outputPrefixType == OutputPrefixType.RAW)
                    require(entry.keyData.keyMaterialType == KeyData.KeyMaterialType.SYMMETRIC)
                    require(entry.keyData.typeUrl ==
                        "type.googleapis.com/google.crypto.tink.AesGcmHkdfStreamingKey")
                    val key = StreamingKeyProto.parseFrom(entry.keyData.value)
                    require(key.version == 0 && key.keyValue.size() == 32)
                    require(key.params.ciphertextSegmentSize == SEGMENT && key.params.derivedKeySize == 32 &&
                        key.params.hkdfHashType == HashType.SHA256)
                    return authenticated
                } catch (failure: Exception) {
                    authenticated.fill(0)
                    throw failure
                }
            }
        }
    }

    fun prefix(): ByteArray = ByteBuffer.allocate(60).put(magic).putShort(1).putShort(1)
        .put(1).put(0).putShort(0).putInt(0).putInt(0).putInt(0)
        .put(randomBytes(16)).put(randomBytes(16)).array()

    fun write(
        plaintext: ByteArray, credential: String, handle: KeysetHandle = newHandle(),
        cancel: () -> Unit = {},
    ): ByteArray {
        require(plaintext.size <= MAX_PLAIN)
        cancel()
        val prefix = prefix()
        val wrapped = TinkProtoKeysetFormat.serializeEncryptedKeyset(
            handle, wrappingAead(credential, prefix), keysetDomain + prefix, RegistryConfiguration.get())
        require(wrapped.size in 1..4096)
        val header = prefix + ByteBuffer.allocate(4).putInt(wrapped.size).array() + wrapped
        return encryptPayload(header, handle, plaintext, cancel)
    }

    /** Test-only adversarial source: same envelope/key/AAD, independently randomized stream. */
    fun secondStream(archive: ByteArray, credential: String, plaintext: ByteArray): ByteArray {
        require(plaintext.size <= MAX_PLAIN)
        val offset = checkedHeader(archive)
        val prefix = archive.copyOfRange(0, 60)
        val handle = TinkProtoKeysetFormat.parseEncryptedKeyset(
            archive.copyOfRange(64, offset), keysetReaderAead(credential, prefix), keysetDomain + prefix,
            RegistryConfiguration.get())
        validate(handle)
        return encryptPayload(archive.copyOfRange(0, offset), handle, plaintext) {}
    }

    fun encryptPayload(
        header: ByteArray, handle: KeysetHandle, plaintext: ByteArray, cancel: () -> Unit,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(header)
        handle.getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
            .newEncryptingStream(output, payloadDomain + header).use { stream ->
                var offset = 0
                while (offset < plaintext.size) {
                    cancel()
                    val count = minOf(8192, plaintext.size - offset)
                    stream.write(plaintext, offset, count)
                    offset += count
                }
            }
        return output.toByteArray().also { require(it.size <= MAX_CIPHER) }
    }

    private fun validate(handle: KeysetHandle) {
        require(handle.size() == 1)
        val entry = handle.getAt(0)
        require(entry.status == KeyStatus.ENABLED && entry.isPrimary)
        val key = entry.key
        require(key is AesGcmHkdfStreamingKey && key.parameters == parameters)
    }

    private fun checkedHeader(bytes: ByteArray): Int {
        require(bytes.size in 65..MAX_CIPHER)
        val buffer = ByteBuffer.wrap(bytes, 0, 60)
        val readMagic = ByteArray(8).also { buffer.get(it) }
        require(readMagic.contentEquals(magic))
        require(buffer.short.toInt() == 1 && buffer.short.toInt() == 1)
        // Profile 2 is unavailable, even with the design's exact Argon tuple.
        require(buffer.get().toInt() == 1 && buffer.get().toInt() == 0)
        require(buffer.short.toInt() == 0)
        repeat(3) { require(buffer.int == 0) }
        val length = ByteBuffer.wrap(bytes, 60, 4).int.toLong() and 0xffff_ffffL
        require(length in 1..4096 && length <= bytes.size.toLong() - 64)
        return 64 + length.toInt()
    }

    /** The caller's array is copied once; both passes read the same exclusively owned bytes. */
    fun read(
        archive: ByteArray, credential: String, plaintextLimit: Int = MAX_PLAIN,
        cancel: () -> Unit = {}, accepted: (ByteArray) -> Unit = {},
    ): ByteArray {
        require(archive.size <= MAX_CIPHER && plaintextLimit in 0..MAX_PLAIN)
        val owned = archive.copyOf()
        cancel()
        val offset = checkedHeader(owned) // Admission before credential/KDF/keyset work.
        val prefix = owned.copyOfRange(0, 60)
        val handle = TinkProtoKeysetFormat.parseEncryptedKeyset(
            owned.copyOfRange(64, offset), keysetReaderAead(credential, prefix), keysetDomain + prefix,
            RegistryConfiguration.get())
        validate(handle) // No primitive is obtained for an authenticated but unsupported keyset.
        val primitive = handle.getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
        val aad = payloadDomain + owned.copyOfRange(0, offset)
        val buffer = ByteArray(8192)
        fun pass(output: ByteArrayOutputStream?) {
            var total = 0L
            primitive.newDecryptingStream(ByteArrayInputStream(owned, offset, owned.size - offset), aad)
                .use { stream ->
                    while (true) {
                        cancel()
                        val count = stream.read(buffer)
                        if (count == -1) break // Tink checks the final tag and physical EOF.
                        require(count > 0)
                        total += count
                        require(total <= plaintextLimit.toLong())
                        output?.write(buffer, 0, count)
                    }
                }
        }
        try {
            pass(null) // Full authentication to discard; no plaintext consumer is invoked.
            val output = ByteArrayOutputStream()
            pass(output)
            cancel()
            return output.toByteArray().also(accepted)
        } finally { buffer.fill(0) }
    }
}
