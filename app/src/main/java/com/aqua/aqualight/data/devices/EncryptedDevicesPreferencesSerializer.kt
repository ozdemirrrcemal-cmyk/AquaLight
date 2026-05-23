package com.aqua.aqualight.data.devices

import android.content.Context
import android.util.Log
import androidx.datastore.core.Serializer
import androidx.security.crypto.MasterKey
import com.aqua.aqualight.data.security.KeyStoreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

class EncryptedDevicesPreferencesSerializer(
    private val context: Context,
    private val delegate: Serializer<DevicesPreferences>
) : Serializer<DevicesPreferences> {

    companion object {
        private const val TAG = "DevicesPrefsSerializer"
        private const val ALGO = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val GCM_TAG_LENGTH = 128
    }

    override val defaultValue: DevicesPreferences
        get() = DevicesPreferences.getDefaultInstance()

    private fun getSecretKey(): SecretKey {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val rawKey = KeyStoreUtils.getOrCreateDataKey(
            context = context,
            masterKey = masterKey
        )

        return SecretKeySpec(
            rawKey,
            "AES"
        )
    }

    override suspend fun readFrom(
        input: InputStream
    ): DevicesPreferences = withContext(Dispatchers.IO) {
        val buffered = BufferedInputStream(input)

        val iv = ByteArray(IV_SIZE)
        val read = buffered.read(iv)

        if (read == -1) {
            return@withContext defaultValue
        }

        if (read != IV_SIZE) {
            Log.e(
                TAG,
                "Corrupted devices prefs file: IV incomplete. read=$read"
            )
            return@withContext defaultValue
        }

        return@withContext try {
            val cipher = Cipher.getInstance(ALGO)
            val key = getSecretKey()
            val spec = GCMParameterSpec(
                GCM_TAG_LENGTH,
                iv
            )

            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                spec
            )

            CipherInputStream(
                buffered,
                cipher
            ).use { cipherInputStream ->
                delegate.readFrom(cipherInputStream)
            }
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Failed to decrypt devices prefs. Returning default.",
                exception
            )
            defaultValue
        }
    }

    override suspend fun writeTo(
        t: DevicesPreferences,
        output: OutputStream
    ) = withContext(Dispatchers.IO) {
        val iv = ByteArray(IV_SIZE).also { bytes ->
            SecureRandom().nextBytes(bytes)
        }

        val cipher = Cipher.getInstance(ALGO)
        val key = getSecretKey()
        val spec = GCMParameterSpec(
            GCM_TAG_LENGTH,
            iv
        )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            key,
            spec
        )

        val buffered = BufferedOutputStream(output)

        buffered.write(iv)
        buffered.flush()

        CipherOutputStream(
            buffered,
            cipher
        ).use { cipherOutputStream ->
            delegate.writeTo(
                t,
                cipherOutputStream
            )
            cipherOutputStream.flush()
        }

        buffered.flush()
    }
}