package com.aqua.aqualight.data.user

import android.content.Context
import android.util.Log
import androidx.datastore.core.Serializer
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.aqua.aqualight.data.security.KeyStoreUtils
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
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
        private const val TAG = "UserPrefsSerializer"
        private const val ALGO = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12 // 96 bits
        private const val GCM_TAG_LENGTH = 128
    }

    override val defaultValue: UserPreferences
        get() = UserPreferences.getDefaultInstance()

    private fun getSecretKey(): SecretKey {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val rawKey = KeyStoreUtils.getOrCreateDataKey(context, masterKey)
        return SecretKeySpec(rawKey, "AES")
    }

    override suspend fun readFrom(input: InputStream): UserPreferences =
        withContext(Dispatchers.IO) {
            val buffered = BufferedInputStream(input)

            // Dosya boşsa (hiç byte yok) -> default
            buffered.mark(IV_SIZE)
            val iv = ByteArray(IV_SIZE)
            val read = buffered.read(iv)

            if (read == -1) {
                // tamamen boş dosya
                return@withContext defaultValue
            }

            if (read != IV_SIZE) {
                // Yarım yazılmış / bozuk dosya: logla, default dön
                Log.e(TAG, "Corrupted prefs file: IV incomplete (read=$read)")
                return@withContext defaultValue
            }

            return@withContext try {
                val cipher = Cipher.getInstance(ALGO)
                val key = getSecretKey()
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, key, spec)

                CipherInputStream(buffered, cipher).use { cis ->
                    delegate.readFrom(cis)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt prefs, returning default.", e)
                defaultValue
            }
        }

    override suspend fun writeTo(t: UserPreferences, output: OutputStream) =
        withContext(Dispatchers.IO) {
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