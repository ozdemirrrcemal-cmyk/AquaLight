package com.aqua.aqualight.data.notifications

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import kotlinx.coroutines.flow.first

private val Context.deviceUpdateNotificationLedgerDataStore by preferencesDataStore(
    name = "device_update_notification_ledger",
    corruptionHandler = ReplaceFileCorruptionHandler {
        LocalDataRecoveryTracker.markRecovered(
            LocalDataRecoveryTracker.Area.NOTIFICATION_PREFERENCES
        )
        emptyPreferences()
    }
)

/** Persistent owner/device/target ledger for background firmware availability alerts. */
class DeviceUpdateNotificationLedger private constructor(
    private val context: Context
) {

    suspend fun isAnnounced(
        ownerUid: String,
        deviceUid: String,
        targetVersion: String
    ): Boolean {
        val record = normalizedRecord(deviceUid, targetVersion)
        return record in records(ownerUid)
    }

    suspend fun markAnnounced(
        ownerUid: String,
        deviceUid: String,
        targetVersion: String
    ) {
        addRecord(ownerUid, normalizedRecord(deviceUid, targetVersion))
    }

    suspend fun trackDevice(ownerUid: String, deviceUid: String) {
        addRecord(
            ownerUid = ownerUid,
            record = normalizedRecord(deviceUid, ACTIVE_NOTIFICATION_MARKER)
        )
    }

    suspend fun trackedDeviceUids(ownerUid: String): Set<String> {
        return records(ownerUid).mapTo(mutableSetOf()) { record -> record.deviceUid }
    }

    suspend fun clearDevice(ownerUid: String, deviceUid: String) {
        val key = DeviceUpdateNotificationLedgerCodec.ownerKey(ownerUid)
        val device = DeviceUpdateNotificationLedgerCodec.normalizeDeviceUid(deviceUid)
        context.deviceUpdateNotificationLedgerDataStore.edit { preferences ->
            val retained = decodeRecords(preferences[key])
                .filterNotTo(mutableSetOf()) { record -> record.deviceUid == device }
            if (retained.isEmpty()) {
                preferences.remove(key)
            } else {
                preferences[key] = retained.mapTo(mutableSetOf()) {
                    DeviceUpdateNotificationLedgerCodec.encode(it)
                }
            }
        }
    }

    suspend fun clearOwner(ownerUid: String) {
        val key = DeviceUpdateNotificationLedgerCodec.ownerKey(ownerUid)
        context.deviceUpdateNotificationLedgerDataStore.edit { preferences ->
            preferences.remove(key)
        }
    }

    private suspend fun addRecord(
        ownerUid: String,
        record: DeviceUpdateNotificationRecord
    ) {
        val key = DeviceUpdateNotificationLedgerCodec.ownerKey(ownerUid)
        context.deviceUpdateNotificationLedgerDataStore.edit { preferences ->
            val retained = decodeRecords(preferences[key]).toMutableSet()
            retained += record
            preferences[key] = retained.mapTo(mutableSetOf()) {
                DeviceUpdateNotificationLedgerCodec.encode(it)
            }
        }
    }

    private suspend fun records(ownerUid: String): Set<DeviceUpdateNotificationRecord> {
        val key = DeviceUpdateNotificationLedgerCodec.ownerKey(ownerUid)
        val preferences = context.deviceUpdateNotificationLedgerDataStore.data.first()
        return decodeRecords(preferences[key])
    }

    private fun normalizedRecord(
        deviceUid: String,
        targetVersion: String
    ): DeviceUpdateNotificationRecord {
        return DeviceUpdateNotificationRecord(
            deviceUid = DeviceUpdateNotificationLedgerCodec.normalizeDeviceUid(deviceUid),
            targetVersion = DeviceUpdateNotificationLedgerCodec.normalizeTargetVersion(
                targetVersion
            )
        )
    }

    private fun decodeRecords(values: Set<String>?): Set<DeviceUpdateNotificationRecord> {
        return values.orEmpty().mapNotNullTo(mutableSetOf()) {
            DeviceUpdateNotificationLedgerCodec.decode(it)
        }
    }

    companion object {
        private const val ACTIVE_NOTIFICATION_MARKER = "__active_notification__"

        fun create(context: Context): DeviceUpdateNotificationLedger {
            return DeviceUpdateNotificationLedger(context.applicationContext)
        }
    }
}
