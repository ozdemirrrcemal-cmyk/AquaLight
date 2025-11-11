package com.aqua.aqualight.data

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.SecureRandom

/**
 * Creates or loads a persistent random AES key protected by MasterKey via EncryptedFile.
 * Returns raw 32 bytes (256-bit) key.
 */
object KeyStoreUtils {

    private const val KEY_FILE_NAME = "data_key.bin"
    private const val KEY_SIZE = 32 // 256 bits

    fun getOrCreateDataKey(context: Context, masterKey: MasterKey): ByteArray {
        val keyFile = File(context.filesDir, KEY_FILE_NAME)

        if (keyFile.exists()) {
            try {
                val encryptedFile = EncryptedFile.Builder(
                    context,
                    keyFile,
                    masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()
                encryptedFile.openFileInput().use { fis ->
                    val bytes = fis.readBytes()
                    if (bytes.size == KEY_SIZE) return bytes
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallthrough to recreate
            }
        }

        val newKey = ByteArray(KEY_SIZE).also { SecureRandom().nextBytes(it) }
        try {
            val encryptedFile = EncryptedFile.Builder(
                context,
                keyFile,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            encryptedFile.openFileOutput().use { fos ->
                fos.write(newKey)
                fos.flush()
            }
            return newKey
        } catch (e: Exception) {
            e.printStackTrace()
            // If persistence fails, return generated key (not persisted) — unlikely
            return newKey
        }
    }
}