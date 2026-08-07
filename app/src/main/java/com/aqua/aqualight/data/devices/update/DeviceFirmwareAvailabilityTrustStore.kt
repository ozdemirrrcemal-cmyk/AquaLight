package com.aqua.aqualight.data.devices.update

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import kotlinx.coroutines.flow.first

private val Context.deviceFirmwareAvailabilityTrustDataStore by preferencesDataStore(
    name = "device_firmware_availability_trust",
    corruptionHandler = ReplaceFileCorruptionHandler {
        LocalDataRecoveryTracker.markRecovered(
            LocalDataRecoveryTracker.Area.NOTIFICATION_PREFERENCES
        )
        emptyPreferences()
    }
)

internal interface DeviceFirmwareAvailabilityTrust {
    suspend fun recordValidated(ownerUid: String, snapshot: DeviceSnapshot): Boolean
    suspend fun isFresh(ownerUid: String, snapshot: DeviceSnapshot): Boolean
    suspend fun trackedDeviceUids(ownerUid: String): Set<String>
    suspend fun clearDevice(ownerUid: String, deviceUid: String)
    suspend fun clearOwner(ownerUid: String)
}

internal class DeviceFirmwareAvailabilityTrustStore private constructor(
    private val context: Context,
    private val policy: DeviceFirmwareAvailabilityTrustPolicy
) : DeviceFirmwareAvailabilityTrust {

    override suspend fun recordValidated(
        ownerUid: String,
        snapshot: DeviceSnapshot
    ): Boolean {
        val record = policy.recordFor(snapshot) ?: return false
        replaceRecord(ownerUid, record)
        return true
    }

    override suspend fun isFresh(
        ownerUid: String,
        snapshot: DeviceSnapshot
    ): Boolean {
        val record = records(ownerUid)
            .firstOrNull { candidate ->
                candidate.deviceUid == snapshot.deviceUid.value
            }
        val fresh = record != null && policy.matches(snapshot, record)
        if (!fresh) {
            clearDevice(ownerUid, snapshot.deviceUid.value)
        }
        return fresh
    }

    override suspend fun trackedDeviceUids(ownerUid: String): Set<String> {
        return records(ownerUid).mapTo(mutableSetOf()) { record ->
            record.deviceUid
        }
    }

    override suspend fun clearDevice(ownerUid: String, deviceUid: String) {
        val key = DeviceFirmwareAvailabilityTrustCodec.ownerKey(ownerUid)
        val normalizedDeviceUid =
            DeviceFirmwareAvailabilityTrustCodec.normalizeDeviceUid(deviceUid)
        context.deviceFirmwareAvailabilityTrustDataStore.edit { preferences ->
            val retained = decodeRecords(preferences[key])
                .filterNotTo(mutableSetOf()) { record ->
                    record.deviceUid == normalizedDeviceUid
                }
            writeRecords(preferences, key, retained)
        }
    }

    override suspend fun clearOwner(ownerUid: String) {
        val key = DeviceFirmwareAvailabilityTrustCodec.ownerKey(ownerUid)
        context.deviceFirmwareAvailabilityTrustDataStore.edit { preferences ->
            preferences.remove(key)
        }
    }

    private suspend fun replaceRecord(
        ownerUid: String,
        record: DeviceFirmwareAvailabilityTrustRecord
    ) {
        val key = DeviceFirmwareAvailabilityTrustCodec.ownerKey(ownerUid)
        context.deviceFirmwareAvailabilityTrustDataStore.edit { preferences ->
            val retained = decodeRecords(preferences[key])
                .filterNotTo(mutableSetOf()) { candidate ->
                    candidate.deviceUid == record.deviceUid
                }
            retained += record
            writeRecords(preferences, key, retained)
        }
    }

    private suspend fun records(
        ownerUid: String
    ): Set<DeviceFirmwareAvailabilityTrustRecord> {
        val key = DeviceFirmwareAvailabilityTrustCodec.ownerKey(ownerUid)
        val preferences = context.deviceFirmwareAvailabilityTrustDataStore.data.first()
        return decodeRecords(preferences[key])
    }

    private fun decodeRecords(
        values: Set<String>?
    ): Set<DeviceFirmwareAvailabilityTrustRecord> {
        return values.orEmpty().mapNotNullTo(mutableSetOf()) {
            DeviceFirmwareAvailabilityTrustCodec.decode(it)
        }
    }

    private fun writeRecords(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        key: androidx.datastore.preferences.core.Preferences.Key<Set<String>>,
        records: Set<DeviceFirmwareAvailabilityTrustRecord>
    ) {
        if (records.isEmpty()) {
            preferences.remove(key)
        } else {
            preferences[key] = records.mapTo(mutableSetOf()) {
                DeviceFirmwareAvailabilityTrustCodec.encode(it)
            }
        }
    }

    companion object {
        fun create(context: Context): DeviceFirmwareAvailabilityTrustStore {
            return DeviceFirmwareAvailabilityTrustStore(
                context = context.applicationContext,
                policy = DeviceFirmwareAvailabilityTrustPolicy()
            )
        }

        internal fun createForTests(
            context: Context,
            policy: DeviceFirmwareAvailabilityTrustPolicy
        ): DeviceFirmwareAvailabilityTrustStore {
            return DeviceFirmwareAvailabilityTrustStore(
                context = context.applicationContext,
                policy = policy
            )
        }
    }
}
