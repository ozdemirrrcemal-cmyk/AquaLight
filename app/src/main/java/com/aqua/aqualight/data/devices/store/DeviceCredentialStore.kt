package com.aqua.aqualight.data.devices.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTokenProvider
import com.aqua.aqualight.data.user.UserDataScope
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Secure owner-scoped local credential store.
 *
 * Both the Firebase owner UID and device UID are included in the SHA-256 key
 * material. Neither raw identifier is exposed in encrypted preference keys, and
 * accounts sharing one installation cannot reuse each other's runtime token.
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
                .getString(tokenPreferenceKey(ownerUid, deviceUid), null)
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
                    tokenPreferenceKey(ownerUid, deviceUid),
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
                .remove(tokenPreferenceKey(ownerUid, deviceUid))
                .commit()
        }
    }

    suspend fun hasToken(deviceUid: DeviceUid): Boolean {
        return getToken(deviceUid).isNullOrBlank().not()
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

    private fun tokenPreferenceKey(
        ownerUid: String,
        deviceUid: DeviceUid
    ): String {
        val keyMaterial = buildString {
            append(UserDataScope.normalizeOwnerUid(ownerUid))
            append(KEY_MATERIAL_SEPARATOR)
            append(deviceUid.value.trim().uppercase(Locale.US))
        }

        return "$KEY_PREFIX${sha256(keyMaterial)}"
    }

    private fun String.isRuntimeTokenHex(): Boolean {
        return length == AqlBleProvisioningContract.RUNTIME_TOKEN_HEX_LENGTH &&
            matches(Regex("(?i)^[0-9a-f]+$"))
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))

        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "aql_device_credentials_v1"
        const val KEY_PREFIX = "ws_token_"
        const val KEY_MATERIAL_SEPARATOR = "\u001F"
    }
}
