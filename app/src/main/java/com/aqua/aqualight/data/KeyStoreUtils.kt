package com.aqua.aqualight.data

import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.IOException
import java.security.SecureRandom

/**
 * Creates or loads a persistent random AES key protected by MasterKey via EncryptedFile.
 * Returns raw 32 bytes (256-bit) key.
 */
object KeyStoreUtils {

    private const val KEY_FILE_NAME = "data_key.bin"
    private const val KEY_SIZE = 32 // 256 bits

    @Throws(IOException::class)
    fun getOrCreateDataKey(context: Context, masterKey: MasterKey): ByteArray {
        val keyFile = File(context.filesDir, KEY_FILE_NAME)

        // Mevcut key'i okumayı dene
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
                    if (bytes.size == KEY_SIZE) {
                        return bytes
                    } else {
                        // yanlış boyut -> bozuk say, dosyayı sil ve yeniden oluştur
                        keyFile.delete()
                    }
                }
            } catch (e: Exception) {
                // okuma veya decrypt hatası: dosyayı sil ve aşağıda yeni key oluştur
                keyFile.delete()
            }
        }

        // Yeni key üret
        val newKey = ByteArray(KEY_SIZE).also { SecureRandom().nextBytes(it) }

        // Yeni key'i güvenli şekilde persist et
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
        } catch (e: Exception) {
            // Eğer persist edemiyorsak, app için bu ciddi bir durum:
            // Key'i RAM'de kullanıp sonra kaybetmek yerine, hata fırlatıyoruz.
            throw IOException("Failed to persist data encryption key", e)
        }

        return newKey
    }
}