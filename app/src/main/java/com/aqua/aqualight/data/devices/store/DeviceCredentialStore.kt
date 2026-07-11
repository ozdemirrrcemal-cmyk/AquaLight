package com.aqua.aqualight.data.devices.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Secure owner-scoped runtime credential store.
 *
 * Preference keys contain only SHA-256 digests. A token written for one owner
 * can never be addressed by another owner, even when the device UID is equal.
 */
class DeviceCredentialStore(
    context: Context,
    ownerUid: String
) : AqlWsTokenProvider {

    private val appContext = context.applicationContext
    private val ownerUid = ownerUid.trim().also { normalizedOwnerUid ->
        require(normalizedOwnerUid.isNotBlank()) {
            "ownerUid must not be blank"
        }
    }

    private val preferences: SharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            appContext,
            PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun getToken(
        deviceUid: DeviceUid
    ): String? {
        return withContext(Dispatchers.IO) {
            val token = preferences
                .getString(tokenPreferenceKey(deviceUid), null)
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: return@withContext null

            check(token.isRuntimeTokenHex()) {
                "Stored runtime token is malformed."
            }

            token
        }
    }

    override suspend fun saveToken(
        deviceUid: DeviceUid,
        token: String
    ) {
        val normalizedToken = token.trim()
        require(normalizedToken.isRuntimeTokenHex()) {
            "Runtime token must be a ${AqlBleProvisioningContract.RUNTIME_TOKEN_HEX_LENGTH}-character hexadecimal value."
        }

        withContext(Dispatchers.IO) {
            check(
                preferences.edit()
                    .putString(tokenPreferenceKey(deviceUid), normalizedToken)
                    .commit()
            ) {
                "Runtime token could not be persisted."
            }
        }
    }

    override suspend fun clearToken(
        deviceUid: DeviceUid
    ) {
        withContext(Dispatchers.IO) {
            check(
                preferences.edit()
                    .remove(tokenPreferenceKey(deviceUid))
                    .commit()
            ) {
                "Runtime token could not be removed."
            }
        }
    }

    suspend fun hasToken(
        deviceUid: DeviceUid
    ): Boolean {
        return getToken(deviceUid).isNullOrBlank().not()
    }

    suspend fun clearOwner() {
        withContext(Dispatchers.IO) {
            val ownerPrefix = DeviceCredentialKeyFactory.ownerPrefix(ownerUid)
            val keys = preferences.all.keys.filter { key ->
                key.startsWith(ownerPrefix)
            }

            if (keys.isEmpty()) {
                return@withContext
            }

            val editor = preferences.edit()
            keys.forEach(editor::remove)

            check(editor.commit()) {
                "Owner runtime credentials could not be removed."
            }
        }
    }

    private fun tokenPreferenceKey(
        deviceUid: DeviceUid
    ): String {
        return DeviceCredentialKeyFactory.tokenKey(
            ownerUid = ownerUid,
            deviceUid = deviceUid
        )
    }

    private fun String.isRuntimeTokenHex(): Boolean {
        return length == AqlBleProvisioningContract.RUNTIME_TOKEN_HEX_LENGTH &&
            matches(Regex("(?i)^[0-9a-f]+$"))
    }

    private companion object {
        const val PREFERENCES_NAME = "device_credentials"
    }
}
