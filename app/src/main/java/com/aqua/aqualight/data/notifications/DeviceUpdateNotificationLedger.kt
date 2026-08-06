package com.aqua.aqualight.data.notifications

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aqua.aqualight.data.user.UserDataScope
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.first

private val Context.deviceUpdateNotificationLedgerDataStore by preferencesDataStore(
    name = "device_update_notification_ledger"
)

/** Persistent owner/device ledger that limits availability alerts to once per target version. */
class DeviceUpdateNotificationLedger private constructor(
    private val context: Context
) {

    suspend fun isAnnounced(
        ownerUid: String,
        deviceUid: String,
        targetVersion: String
    ): Boolean {
        val owner = requireOwnerUid(ownerUid)
        val device = requireDeviceUid(deviceUid)
        val target = requireTargetVersion(targetVersion)
        return records(owner)[device] == target
    }

    suspend fun markAnnounced(
        ownerUid: String,
        deviceUid: String,
        targetVersion: String
    ) {
        val owner = requireOwnerUid(ownerUid)
        val device = requireDeviceUid(deviceUid)
        val target = requireTargetVersion(targetVersion)
        val key = ownerKey(owner)

        context.deviceUpdateNotificationLedgerDataStore.edit { preferences ->
            val retained = preferences[key]
                .orEmpty()
                .mapNotNull(::parseRecord)
                .filterNot { record -> record.deviceUid == device }
                .map(::serializeRecord)
                .toMutableSet()
            retained += serializeRecord(Record(device, target))
            preferences[key] = retained
        }
    }

    suspend fun trackedDeviceUids(ownerUid: String): Set<String> {
        return records(requireOwnerUid(ownerUid)).keys
    }

    suspend fun clearDevice(ownerUid: String, deviceUid: String) {
        val owner = requireOwnerUid(ownerUid)
        val device = requireDeviceUid(deviceUid)
        val key = ownerKey(owner)

        context.deviceUpdateNotificationLedgerDataStore.edit { preferences ->
            val retained = preferences[key]
                .orEmpty()
                .mapNotNull(::parseRecord)
                .filterNot { record -> record.deviceUid == device }
                .map(::serializeRecord)
                .toSet()
            if (retained.isEmpty()) {
                preferences.remove(key)
            } else {
                preferences[key] = retained
            }
        }
    }

    suspend fun clearOwner(ownerUid: String) {
        val key = ownerKey(requireOwnerUid(ownerUid))
        context.deviceUpdateNotificationLedgerDataStore.edit { preferences ->
            preferences.remove(key)
        }
    }

    private suspend fun records(ownerUid: String): Map<String, String> {
        val key = ownerKey(ownerUid)
        return context.deviceUpdateNotificationLedgerDataStore.data.first()[key]
            .orEmpty()
            .mapNotNull(::parseRecord)
            .associate { record -> record.deviceUid to record.targetVersion }
    }

    private fun ownerKey(ownerUid: String) = stringSetPreferencesKey(
        OWNER_KEY_PREFIX + UUID.nameUUIDFromBytes(
            ownerUid.toByteArray(StandardCharsets.UTF_8)
        )
    )

    private fun serializeRecord(record: Record): String {
        val encodedDevice = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(record.deviceUid.toByteArray(StandardCharsets.UTF_8))
        return encodedDevice + RECORD_SEPARATOR + record.targetVersion
    }

    private fun parseRecord(value: String): Record? {
        val parts = value.split(RECORD_SEPARATOR, limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return null
        }
        return runCatching {
            val deviceUid = String(
                Base64.getUrlDecoder().decode(parts[0]),
                StandardCharsets.UTF_8
            ).trim()
            Record(
                deviceUid = requireDeviceUid(deviceUid),
                targetVersion = requireTargetVersion(parts[1])
            )
        }.getOrNull()
    }

    private fun requireOwnerUid(ownerUid: String): String {
        return UserDataScope.normalizeOwnerUid(ownerUid).also { normalized ->
            require(normalized.isNotBlank()) { "ownerUid must not be blank" }
        }
    }

    private fun requireDeviceUid(deviceUid: String): String {
        return deviceUid.trim().also { normalized ->
            require(normalized.isNotBlank()) { "deviceUid must not be blank" }
        }
    }

    private fun requireTargetVersion(targetVersion: String): String {
        return targetVersion.trim().also { normalized ->
            require(normalized.isNotBlank()) { "targetVersion must not be blank" }
        }
    }

    private data class Record(
        val deviceUid: String,
        val targetVersion: String
    )

    companion object {
        private const val OWNER_KEY_PREFIX = "owner_"
        private const val RECORD_SEPARATOR = "\u001F"

        fun create(context: Context): DeviceUpdateNotificationLedger {
            return DeviceUpdateNotificationLedger(context.applicationContext)
        }
    }
}
