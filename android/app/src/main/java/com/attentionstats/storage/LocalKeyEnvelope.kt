package com.attentionstats.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Installation-local AES-GCM envelope. Missing keys/wrappers never recreate existing history. */
internal class LocalKeyEnvelope(private val context: Context, private val database: File,
    private val alias: String = "attentionstats.history.wrap.v1") {
    data class Opened(val raw: ByteArray, val installationEpoch: String, val generation: String)
    private val wrapper = AtomicFile(File(context.noBackupFilesDir, "history-key-v1"))
    private val magic = "ATTNKEY1".toByteArray(Charsets.US_ASCII)
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun open(): Opened {
        val existing = wrapper.baseFile.exists() || File(wrapper.baseFile.path + ".bak").exists()
        if (existing) {
            check(store.containsAlias(alias)) { "KEY_UNAVAILABLE" }
            val bytes = wrapper.openRead().use { input ->
                val read = input.readNBytesCompat(257)
                check(read.size in 1..256) { "KEY_UNAVAILABLE" }
                read
            }
            return unwrap(bytes)
        }
        check(listOf(database, File(database.path + "-wal"), File(database.path + "-shm"))
            .none { it.exists() }) { "KEY_UNAVAILABLE" }
        if (!store.containsAlias(alias)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).build())
            }.generateKey()
        }
        val opened = Opened(ByteArray(32).also { SecureRandom().nextBytes(it) },
            UUID.randomUUID().toString(), UUID.randomUUID().toString())
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            check(cipher.iv.size == 12)
            cipher.updateAAD(aad(opened.installationEpoch, opened.generation))
            val ciphertext = cipher.doFinal(opened.raw)
            val output = ByteArrayOutputStream()
            DataOutputStream(output).use { stream ->
                stream.write(magic)
                stream.writeUTF(opened.installationEpoch)
                stream.writeUTF(opened.generation)
                stream.write(cipher.iv)
                stream.writeInt(ciphertext.size)
                stream.write(ciphertext)
            }
            val encoded = output.toByteArray()
            val verified = unwrap(encoded)
            try { check(verified.raw.contentEquals(opened.raw)) } finally { verified.raw.fill(0) }
            val stream = wrapper.startWrite()
            try { stream.write(encoded); wrapper.finishWrite(stream) }
            catch (failure: Exception) { wrapper.failWrite(stream); throw failure }
            return opened
        } catch (failure: Exception) {
            opened.raw.fill(0)
            throw failure
        }
    }

    private fun unwrap(bytes: ByteArray): Opened = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        check(ByteArray(8).also { input.readFully(it) }.contentEquals(magic)) { "KEY_UNAVAILABLE" }
        val installation = input.readUTF().also { requireUuid(it) }
        val generation = input.readUTF().also { requireUuid(it) }
        val iv = ByteArray(12).also { input.readFully(it) }
        check(input.readInt() == 48) { "KEY_UNAVAILABLE" }
        val encrypted = ByteArray(48).also { input.readFully(it) }
        check(input.read() == -1) { "KEY_UNAVAILABLE" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad(installation, generation))
        val raw = cipher.doFinal(encrypted)
        check(raw.size == 32)
        Opened(raw, installation, generation)
    }

    private fun key() = store.getKey(alias, null) as SecretKey
    private fun requireUuid(value: String) {
        check(value.length == 36 && UUID.fromString(value).toString() == value) { "KEY_UNAVAILABLE" }
    }
    private fun aad(installation: String, generation: String): ByteArray =
        ("AttentionStats/store/wrapper/v1\u0000" + context.packageName + "\u0000" +
            installation + "\u0000" + generation + "\u0000" + alias).toByteArray(Charsets.US_ASCII)

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(256)
        while (output.size() < limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (count == -1) break
            check(count > 0)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
