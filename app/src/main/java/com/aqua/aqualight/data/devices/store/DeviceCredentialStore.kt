package com.aqua.aqualight.data.devices.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTokenProvider
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Secure local credential store for AquaLight Devices V2.
 *
 * Runtime WebSocket pairing tokens are stored per deviceUid. The preference key does not contain
 * the raw deviceUid; it uses a SHA-256 digest to avoid exposing device identity in preference keys.
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
        return withContext(Dispatchers.IO) {
            preferences
                .getString(tokenPreferenceKey(deviceUid), null)
                ?.trim()
                ?.takeIf { token -> token.isNotBlank() }
        }
    }

    override suspend fun saveToken(deviceUid: DeviceUid, token: String) {
        val normalizedToken = token.trim()
        if (!normalizedToken.isRuntimeTokenHex()) {
            return
        }

        withContext(Dispatchers.IO) {
            preferences.edit()
                .putString(tokenPreferenceKey(deviceUid), normalizedToken)
                .commit()
        }
    }

    override suspend fun clearToken(deviceUid: DeviceUid) {
        withContext(Dispatchers.IO) {
            preferences.edit()
                .remove(tokenPreferenceKey(deviceUid))
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

    private fun tokenPreferenceKey(deviceUid: DeviceUid): String {
        return "$KEY_PREFIX${sha256(deviceUid.value)}"
    }

    private fun String.isRuntimeTokenHex(): Boolean {
        return length == AqlBleProvisioningContract.RUNTIME_TOKEN_HEX_LENGTH &&
            matches(Regex("(?i)^[0-9a-f]+$"))
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(value.trim().uppercase(Locale.US).toByteArray(Charsets.UTF_8))

        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "aql_device_credentials_v2"
        const val KEY_PREFIX = "ws_token_"
    }
}
