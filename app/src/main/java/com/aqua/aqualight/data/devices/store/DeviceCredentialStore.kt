package com.aqua.aqualight.data.devices.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTokenProvider
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Secure owner-scoped runtime credential store.
 *
 * Preference keys contain SHA-256 digests of both Firebase owner UID and device UID. Raw identifiers
 * are not exposed, and a token saved for one account cannot be resolved or removed by another.
 */
class DeviceCredentialStore(
    context: Context
) : AqlWsTokenProvider {

    private val appContext = context.applicationContext

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

    override suspend fun getToken(deviceUid: DeviceUid): String? {
        val ownerUid = currentOwnerUidOrNull()
            ?: return null

        return withContext(Dispatchers.IO) {
            preferences
                .getString(
                    DeviceCredentialKeyFactory.key(
                        ownerUid = ownerUid,
                        deviceUid = deviceUid.value
                    ),
                    null
                )
                ?.trim()
                ?.takeIf { token -> token.isNotBlank() }
        }
    }

    override suspend fun saveToken(deviceUid: DeviceUid, token: String) {
        val ownerUid = UserDataScope.requireCurrentUid()
        val normalizedToken = token.trim()

        if (!normalizedToken.isRuntimeTokenHex()) {
            return
        }

        withContext(Dispatchers.IO) {
            preferences.edit()
                .putString(
                    DeviceCredentialKeyFactory.key(
                        ownerUid = ownerUid,
                        deviceUid = deviceUid.value
                    ),
                    normalizedToken
                )
                .commit()
        }
    }

    override suspend fun clearToken(deviceUid: DeviceUid) {
        val ownerUid = currentOwnerUidOrNull()
            ?: return

        withContext(Dispatchers.IO) {
            preferences.edit()
                .remove(
                    DeviceCredentialKeyFactory.key(
                        ownerUid = ownerUid,
                        deviceUid = deviceUid.value
                    )
                )
                .commit()
        }
    }

    suspend fun hasToken(deviceUid: DeviceUid): Boolean {
        return getToken(deviceUid).isNullOrBlank().not()
    }

    suspend fun clearOwner(
        ownerUid: String
    ) {
        val normalizedOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)

        if (normalizedOwnerUid.isBlank()) {
            return
        }

        val ownerPrefix = DeviceCredentialKeyFactory.ownerPrefix(normalizedOwnerUid)

        withContext(Dispatchers.IO) {
            val editor = preferences.edit()
            preferences.all.keys
                .filter { key -> key.startsWith(ownerPrefix) }
                .forEach(editor::remove)
            editor.commit()
        }
    }

    fun clearAll() {
        preferences.edit()
            .clear()
            .apply()
    }

    private fun currentOwnerUidOrNull(): String? {
        return UserDataScope.normalizeOwnerUid(
            UserDataScope.currentUid()
        ).takeIf { ownerUid ->
            ownerUid.isNotBlank()
        }
    }

    private fun String.isRuntimeTokenHex(): Boolean {
        return length == AqlBleProvisioningContract.RUNTIME_TOKEN_HEX_LENGTH &&
            matches(Regex("(?i)^[0-9a-f]+$"))
    }

    private companion object {
        const val PREFERENCES_NAME = "aql_device_credentials_v2"
    }
}
