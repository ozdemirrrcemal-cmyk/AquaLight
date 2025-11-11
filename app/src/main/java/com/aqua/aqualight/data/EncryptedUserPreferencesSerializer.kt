package com.aqua.aqualight.data

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM wrapper serializer.
 * On-disk format: [12 bytes IV][ciphertext]
 */
class EncryptedUserPreferencesSerializer(
    private val context: Context,
    private val delegate: Serializer<UserPreferences>
) : Serializer<UserPreferences> {

    companion object {
        private const val ALGO = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12 // 96 bits
        private const val GCM_TAG_LENGTH = 128
    }

    override val defaultValue: UserPreferences
        get() = UserPreferences.getDefaultInstance()

    private fun getSecretKey(): SecretKey {
        // MasterKey used to encrypt/decrypt a persistent raw key stored with EncryptedFile
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val rawKey = KeyStoreUtils.getOrCreateDataKey(context, masterKey)
        return SecretKeySpec(rawKey, "AES")
    }

    override suspend fun readFrom(input: InputStream): UserPreferences = withContext(Dispatchers.IO) {
        try {
            val buffered = BufferedInputStream(input)
            // Try read IV first
            val iv = ByteArray(IV_SIZE)
            val actuallyRead = buffered.read(iv)
            if (actuallyRead != IV_SIZE) {
                // not encrypted / empty -> attempt delegate parsing (legacy)
                // Rewind by constructing empty stream: delegate expects full protobuf; if file empty -> default
                // Here we return defaultValue to be safe
                return@withContext defaultValue
            }
            val cipher = Cipher.getInstance(ALGO)
            val key = getSecretKey()
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            CipherInputStream(buffered, cipher).use { cis ->
                return@withContext delegate.readFrom(cis)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext defaultValue
        }
    }

    override suspend fun writeTo(t: UserPreferences, output: OutputStream) = withContext(Dispatchers.IO) {
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(ALGO)
        val key = getSecretKey()
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val buffered = BufferedOutputStream(output)
        buffered.write(iv)
        buffered.flush()
        CipherOutputStream(buffered, cipher).use { cos ->
            delegate.writeTo(t, cos)
            cos.flush()
        }
        buffered.flush()
    }
}