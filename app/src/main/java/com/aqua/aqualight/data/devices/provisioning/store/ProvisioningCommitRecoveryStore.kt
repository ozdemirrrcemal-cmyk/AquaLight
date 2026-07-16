package com.aqua.aqualight.data.devices.provisioning.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.store.DeviceCredentialStore
import com.aqua.aqualight.data.devices.store.DeviceKnownStore
import com.aqua.aqualight.data.devices.store.KnownDeviceProtoMapper
import com.aqua.aqualight.data.devices.store.StoredKnownDevice
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Durable, encrypted commit journal for the final provisioning transaction.
 *
 * A record is written only after firmware completion has been accepted and
 * immediately before the app commits the verified snapshot and runtime token.
 * If Android kills the process between those two durable writes, the next owner
 * session completes the same commit idempotently instead of leaving a token and
 * snapshot from different generations.
 */
class ProvisioningCommitRecoveryStore(
    context: Context
) {

    private val appContext = context.applicationContext
    private val preferences: SharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    suspend fun record(
        ownerUid: String,
        snapshot: DeviceSnapshot,
        runtimeToken: String
    ) {
        val owner = normalizedOwner(ownerUid)
        val token = normalizedRuntimeToken(runtimeToken)
        val storedSnapshot = KnownDeviceProtoMapper.toStored(owner, snapshot)
        val payload = JSONObject()
            .put(FIELD_OWNER_UID, owner)
            .put(FIELD_DEVICE_UID, snapshot.deviceUid.value)
            .put(
                FIELD_SNAPSHOT,
                Base64.getEncoder().encodeToString(storedSnapshot.toByteArray())
            )
            .put(FIELD_RUNTIME_TOKEN, token)
            .toString()

        withContext(Dispatchers.IO) {
            synchronized(LOCK) {
                preferences.edit()
                    .putString(recordKey(owner, snapshot.deviceUid), payload)
                    .commitOrThrow("Provisioning commit journal could not be persisted.")
            }
        }
    }

    suspend fun clear(
        ownerUid: String,
        deviceUid: DeviceUid
    ) {
        val owner = normalizedOwner(ownerUid)
        withContext(Dispatchers.IO) {
            synchronized(LOCK) {
                preferences.edit()
                    .remove(recordKey(owner, deviceUid))
                    .commitOrThrow("Provisioning commit journal could not be cleared.")
            }
        }
    }

    suspend fun clearOwner(ownerUid: String) {
        val owner = normalizedOwner(ownerUid)
        val prefix = ownerPrefix(owner)
        withContext(Dispatchers.IO) {
            synchronized(LOCK) {
                val matchingKeys = preferences.all.keys.filter { key ->
                    key.startsWith(prefix)
                }
                if (matchingKeys.isEmpty()) return@withContext
                val editor = preferences.edit()
                matchingKeys.forEach(editor::remove)
                editor.commitOrThrow("Owner provisioning commit journals could not be cleared.")
            }
        }
    }

    /** Completes every commit whose durable decision record belongs to [ownerUid]. */
    suspend fun recoverOwner(ownerUid: String): Int {
        val owner = normalizedOwner(ownerUid)
        val records = readOwnerRecords(owner)
        if (records.isEmpty()) return 0

        val knownStore = DeviceKnownStore(appContext, owner)
        val credentialStore = DeviceCredentialStore(appContext, owner)
        var recoveredCount = 0

        records.forEach { record ->
            check(record.ownerUid == owner) {
                "Provisioning commit journal owner does not match the active owner."
            }
            check(record.snapshot.deviceUid == record.deviceUid) {
                "Provisioning commit journal device identity changed."
            }

            knownStore.saveSnapshot(record.snapshot)
            credentialStore.saveToken(record.deviceUid, record.runtimeToken)
            clear(owner, record.deviceUid)
            recoveredCount += 1
        }

        return recoveredCount
    }

    private suspend fun readOwnerRecords(ownerUid: String): List<CommitRecord> =
        withContext(Dispatchers.IO) {
            val prefix = ownerPrefix(ownerUid)
            synchronized(LOCK) {
                preferences.all
                    .asSequence()
                    .filter { (key, _) -> key.startsWith(prefix) }
                    .sortedBy { (key, _) -> key }
                    .map { (_, value) ->
                        val payload = value as? String
                            ?: error("Provisioning commit journal value is malformed.")
                        decode(payload)
                    }
                    .toList()
            }
        }

    private fun decode(payload: String): CommitRecord {
        val json = JSONObject(payload)
        val ownerUid = normalizedOwner(json.getString(FIELD_OWNER_UID))
        val deviceUid = DeviceUid(json.getString(FIELD_DEVICE_UID))
        val storedSnapshot = StoredKnownDevice.parseFrom(
            Base64.getDecoder().decode(json.getString(FIELD_SNAPSHOT))
        )
        val snapshot = KnownDeviceProtoMapper.toSnapshot(storedSnapshot)
        val runtimeToken = normalizedRuntimeToken(json.getString(FIELD_RUNTIME_TOKEN))
        return CommitRecord(
            ownerUid = ownerUid,
            deviceUid = deviceUid,
            snapshot = snapshot,
            runtimeToken = runtimeToken
        )
    }

    private fun normalizedOwner(ownerUid: String): String = ownerUid.trim().also { owner ->
        require(owner.isNotBlank()) { "ownerUid must not be blank" }
    }

    private fun normalizedRuntimeToken(runtimeToken: String): String =
        runtimeToken.trim().also { token ->
            require(
                token.length == AqlBleProvisioningContract.RUNTIME_TOKEN_HEX_LENGTH &&
                    token.all { character ->
                        character.isDigit() || character.lowercaseChar() in 'a'..'f'
                    }
            ) {
                "Provisioning runtime token is malformed."
            }
        }

    private fun recordKey(ownerUid: String, deviceUid: DeviceUid): String =
        "${ownerPrefix(ownerUid)}${sha256(deviceUid.value)}"

    private fun ownerPrefix(ownerUid: String): String =
        "$KEY_PREFIX${sha256(ownerUid)}."

    private fun sha256(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun SharedPreferences.Editor.commitOrThrow(message: String) {
        check(commit()) { message }
    }

    private data class CommitRecord(
        val ownerUid: String,
        val deviceUid: DeviceUid,
        val snapshot: DeviceSnapshot,
        val runtimeToken: String
    )

    private companion object {
        val LOCK = Any()
        const val FILE_NAME = "aql_provisioning_commit_recovery"
        const val KEY_PREFIX = "commit."
        const val FIELD_OWNER_UID = "owner_uid"
        const val FIELD_DEVICE_UID = "device_uid"
        const val FIELD_SNAPSHOT = "snapshot"
        const val FIELD_RUNTIME_TOKEN = "runtime_token"
    }
}
