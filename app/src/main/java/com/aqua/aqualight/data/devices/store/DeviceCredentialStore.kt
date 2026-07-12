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
                .getString(pendingTokenPreferenceKey(deviceUid), null)
                .orEmpty()
                .ifBlank {
                    preferences
                        .getString(tokenPreferenceKey(deviceUid), null)
                        .orEmpty()
                }
                .trim()
                .takeIf(String::isNotBlank)
                ?: return@withContext null

            check(token.isRuntimeTokenHex()) {
                "Stored runtime token is malformed."
            }

            token
        }
    }

    suspend fun getCommittedToken(
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
                    .remove(pendingTokenPreferenceKey(deviceUid))
                    .commit()
            ) {
                "Runtime token could not be removed."
            }
        }
    }

    /**
     * Makes a verified provisioning token available to the runtime without
     * replacing the last committed credential.
     */
    suspend fun stageToken(
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
                    .putString(
                        pendingTokenPreferenceKey(deviceUid),
                        normalizedToken
                    )
                    .commit()
            ) {
                "Provisioning runtime token could not be staged."
            }
        }
    }

    /** Promotes a staged token with one encrypted preference transaction. */
    suspend fun commitStagedToken(
        deviceUid: DeviceUid
    ) {
        withContext(Dispatchers.IO) {
            val pendingKey = pendingTokenPreferenceKey(deviceUid)
            val stagedToken = preferences
                .getString(pendingKey, null)
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: error("No staged runtime token exists for this device.")

            check(stagedToken.isRuntimeTokenHex()) {
                "Staged runtime token is malformed."
            }

            check(
                preferences.edit()
                    .putString(tokenPreferenceKey(deviceUid), stagedToken)
                    .remove(pendingKey)
                    .commit()
            ) {
                "Provisioning runtime token could not be committed."
            }
        }
    }

    suspend fun rollbackStagedToken(
        deviceUid: DeviceUid
    ) {
        withContext(Dispatchers.IO) {
            check(
                preferences.edit()
                    .remove(pendingTokenPreferenceKey(deviceUid))
                    .commit()
            ) {
                "Provisioning runtime token could not be rolled back."
            }
        }
    }

    /**
     * Discards transactions that could not survive a process restart. The last
     * committed credential remains untouched.
     */
    suspend fun discardStagedTokens(): Int {
        return withContext(Dispatchers.IO) {
            val pendingPrefix = DeviceCredentialKeyFactory
                .pendingTokenPrefix(ownerUid)
            val pendingKeys = preferences.all.keys.filter { key ->
                key.startsWith(pendingPrefix)
            }

            if (pendingKeys.isEmpty()) {
                return@withContext 0
            }

            val editor = preferences.edit()
            pendingKeys.forEach(editor::remove)

            check(editor.commit()) {
                "Staged runtime credentials could not be discarded."
            }

            pendingKeys.size
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

    /**
     * Removes credentials that no longer have a durable known-device record.
     *
     * This is run only while opening an owner session. It recovers safely from
     * process death during new-device provisioning and from a known-device store
     * corruption reset without exposing a token-backed ghost registration.
     */
    suspend fun retainTokensFor(
        deviceUids: Iterable<DeviceUid>
    ): Int {
        return withContext(Dispatchers.IO) {
            val ownerPrefix = DeviceCredentialKeyFactory.ownerPrefix(ownerUid)
            val allowedKeys = deviceUids
                .flatMap { deviceUid ->
                    listOf(
                        tokenPreferenceKey(deviceUid),
                        pendingTokenPreferenceKey(deviceUid)
                    )
                }
                .toSet()
            val orphanedKeys = preferences.all.keys.filter { key ->
                key.startsWith(ownerPrefix) && key !in allowedKeys
            }

            if (orphanedKeys.isEmpty()) {
                return@withContext 0
            }

            val editor = preferences.edit()
            orphanedKeys.forEach(editor::remove)

            check(editor.commit()) {
                "Orphaned owner runtime credentials could not be removed."
            }

            orphanedKeys.size
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

    private fun pendingTokenPreferenceKey(
        deviceUid: DeviceUid
    ): String {
        return DeviceCredentialKeyFactory.pendingTokenKey(
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
