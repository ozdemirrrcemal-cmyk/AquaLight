package com.aqua.aqualight.data.user

import android.content.Context
import androidx.security.crypto.MasterKey
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AES-GCM wrapper serializer.
 * On-disk format: [12 bytes IV][ciphertext].
 *
 * Decryption, authentication, schema, or invariant failures are reported through
 * LocalDataRecoveryTracker and recover to the first commercial default store.
 * No raw exception or preference content is written to logs.
 */
class EncryptedUserPreferencesSerializer(
    private val context: Context,
    private val delegate: androidx.datastore.core.Serializer<UserPreferences>
) : androidx.datastore.core.Serializer<UserPreferences> {

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }

    override val defaultValue: UserPreferences
        get() = delegate.defaultValue

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
            val iv = ByteArray(IV_SIZE)
            val readCount = buffered.read(iv)

            if (readCount == -1) {
                return@withContext defaultValue
            }

            if (readCount != IV_SIZE) {
                return@withContext recoverCorruptedPreferences()
            }

            try {
                val cipher = Cipher.getInstance(ALGORITHM)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    getSecretKey(),
                    GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
                )

                CipherInputStream(buffered, cipher).use { cipherInput ->
                    delegate.readFrom(cipherInput)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                recoverCorruptedPreferences()
            }
        }

    override suspend fun writeTo(
        t: UserPreferences,
        output: OutputStream
    ) = withContext(Dispatchers.IO) {
        val iv = ByteArray(IV_SIZE).also { bytes ->
            SecureRandom().nextBytes(bytes)
        }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            getSecretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )

        val buffered = BufferedOutputStream(output)
        buffered.write(iv)
        buffered.flush()

        CipherOutputStream(buffered, cipher).use { cipherOutput ->
            delegate.writeTo(t, cipherOutput)
            cipherOutput.flush()
        }
        buffered.flush()
    }

    private fun recoverCorruptedPreferences(): UserPreferences {
        LocalDataRecoveryTracker.markRecovered(
            LocalDataRecoveryTracker.Area.USER_PREFERENCES
        )
        return defaultValue
    }
}
