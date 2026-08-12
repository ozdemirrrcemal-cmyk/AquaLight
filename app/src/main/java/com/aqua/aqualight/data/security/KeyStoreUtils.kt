package com.aqua.aqualight.data.security

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.IOException
import java.security.SecureRandom

/**
 * Creates or loads a persistent random AES key protected by MasterKey via EncryptedFile.
 * Returns raw 32 bytes (256-bit) key.
 *
 * If Android backup restores encrypted files without the matching Keystore key,
 * AndroidX Security may fail with AEADBadTagException / MAC verification failed.
 * In that case, this class safely resets the encrypted key storage and creates
 * a new data key instead of crashing the app.
 */
object KeyStoreUtils {

  private const val KEY_FILE_NAME = "data_key.bin"
  private const val KEY_SIZE = 32 // 256 bits

  private const val ANDROIDX_SECURITY_ENCRYPTED_FILE_PREFS =
    "__androidx_security_crypto_encrypted_file_pref__"

  @Throws(IOException::class)
  fun getOrCreateDataKey(
    context: Context,
    masterKey: MasterKey
  ): ByteArray {
    val appContext = context.applicationContext
    val keyFile = File(
      appContext.filesDir,
      KEY_FILE_NAME
    )

    if (keyFile.exists()) {
      val existingKey = readExistingKey(
        context = appContext,
        masterKey = masterKey,
        keyFile = keyFile
      )

      if (existingKey != null) {
        return existingKey
      }

      resetEncryptedKeyStorage(
        context = appContext,
        keyFile = keyFile
      )
    }

    val newKey = ByteArray(KEY_SIZE).also {
      SecureRandom().nextBytes(it)
    }

    return persistNewKeyWithRecovery(
      context = appContext,
      masterKey = masterKey,
      keyFile = keyFile,
      newKey = newKey
    )
  }

  private fun readExistingKey(
    context: Context,
    masterKey: MasterKey,
    keyFile: File
  ): ByteArray? {
    return runCatching {
      val encryptedFile = buildEncryptedFile(
        context = context,
        masterKey = masterKey,
        keyFile = keyFile
      )

      encryptedFile.openFileInput().use {
        input ->
        val bytes = input.readBytes()

        if (bytes.size == KEY_SIZE) {
          bytes
        } else {
          null
        }
      }
    }.getOrNull()
  }

  @Throws(IOException::class)
  private fun persistNewKeyWithRecovery(
    context: Context,
    masterKey: MasterKey,
    keyFile: File,
    newKey: ByteArray
  ): ByteArray {
    resetEncryptedKeyStorage(
      context = context,
      keyFile = keyFile
    )

    val firstAttempt = runCatching {
      writeKey(
        context = context,
        masterKey = masterKey,
        keyFile = keyFile,
        newKey = newKey
      )
    }

    if (firstAttempt.isSuccess) {
      return newKey
    }

    resetEncryptedKeyStorage(
      context = context,
      keyFile = keyFile
    )

    val secondAttempt = runCatching {
      writeKey(
        context = context,
        masterKey = masterKey,
        keyFile = keyFile,
        newKey = newKey
      )
    }

    if (secondAttempt.isSuccess) {
      return newKey
    }

    throw IOException(
      "Failed to persist data encryption key",
      secondAttempt.exceptionOrNull()
    )
  }

  private fun writeKey(
    context: Context,
    masterKey: MasterKey,
    keyFile: File,
    newKey: ByteArray
  ) {
    val encryptedFile = buildEncryptedFile(
      context = context,
      masterKey = masterKey,
      keyFile = keyFile
    )

    encryptedFile.openFileOutput().use {
      output ->
      output.write(newKey)
      output.flush()
    }
  }

  private fun buildEncryptedFile(
    context: Context,
    masterKey: MasterKey,
    keyFile: File
  ): EncryptedFile {
    return EncryptedFile.Builder(
      context,
      keyFile,
      masterKey,
      EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()
  }

  private fun resetEncryptedKeyStorage(
    context: Context,
    keyFile: File
  ) {
    runCatching {
      if (keyFile.exists()) {
        keyFile.delete()
      }
    }

    runCatching {
      context.getSharedPreferences(
        ANDROIDX_SECURITY_ENCRYPTED_FILE_PREFS,
        Context.MODE_PRIVATE
      ).edit()
        .clear()
        .commit()
    }

    runCatching {
      File(
        context.applicationInfo.dataDir,
        "shared_prefs/$ANDROIDX_SECURITY_ENCRYPTED_FILE_PREFS.xml"
      ).delete()
    }
  }
}
