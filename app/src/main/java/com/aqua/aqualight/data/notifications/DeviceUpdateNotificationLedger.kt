package com.aqua.aqualight.data.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.flow.first

private val Context.deviceUpdateNotificationStateDataStore:
    DataStore<DeviceUpdateNotificationStateStore> by dataStore(
        fileName = "device_update_notification_state.pb",
        serializer = DeviceUpdateNotificationStateSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler {
            LocalDataRecoveryTracker.markRecovered(
                LocalDataRecoveryTracker.Area.NOTIFICATION_PREFERENCES
            )
            DeviceUpdateNotificationStateRules.defaultStore()
        }
    )

internal data class DeviceUpdateNotificationLedgerRecord(
    val deviceUid: String,
    val targetVersion: String,
    val deliveryKey: String,
    val deliveredAtEpochMillis: Long,
    val resolved: Boolean
)

/** Durable owner/device delivery ledger used only by firmware-update notifications. */
internal interface DeviceUpdateNotificationLedger {
    suspend fun record(ownerUid: String, deviceUid: String): DeviceUpdateNotificationLedgerRecord?
    suspend fun recordedDeviceUids(ownerUid: String): Set<String>
    suspend fun shouldDeliverAvailability(
        ownerUid: String,
        deviceUid: String,
        targetVersion: String
    ): Boolean

    suspend fun markDelivered(
        ownerUid: String,
        deviceUid: String,
        targetVersion: String,
        deliveryKey: String,
        resolved: Boolean = false
    )

    suspend fun markResolved(
        ownerUid: String,
        deviceUid: String,
        resolvedVersion: String
    )

    suspend fun clearDevice(ownerUid: String, deviceUid: String)
    suspend fun clearOwner(ownerUid: String)

    companion object {
        fun create(context: Context): DeviceUpdateNotificationLedger {
            return DataStoreDeviceUpdateNotificationLedger(
                context = context.applicationContext
            )
        }

        fun noOp(): DeviceUpdateNotificationLedger = NoOpDeviceUpdateNotificationLedger
    }
}

@Suppress("TooManyFunctions")
private class DataStoreDeviceUpdateNotificationLedger(
    private val context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : DeviceUpdateNotificationLedger {

    override suspend fun record(
        ownerUid: String,
        deviceUid: String
    ): DeviceUpdateNotificationLedgerRecord? {
        val owner = requireOwnerUid(ownerUid)
        val device = requireDeviceUid(deviceUid)
        return context.deviceUpdateNotificationStateDataStore.data.first()
            .let(DeviceUpdateNotificationStateRules::validateStore)
            .ownerStatesList
            .firstOrNull { state -> state.ownerUid == owner }
            ?.recordsList
            ?.firstOrNull { record -> record.deviceUid == device }
            ?.toSnapshot()
    }

    override suspend fun recordedDeviceUids(ownerUid: String): Set<String> {
        val owner = requireOwnerUid(ownerUid)
        return context.deviceUpdateNotificationStateDataStore.data.first()
            .let(DeviceUpdateNotificationStateRules::validateStore)
            .ownerStatesList
            .firstOrNull { state -> state.ownerUid == owner }
            ?.recordsList
            ?.mapTo(linkedSetOf()) { record -> record.deviceUid }
            .orEmpty()
    }

    override suspend fun shouldDeliverAvailability(
        ownerUid: String,
        deviceUid: String,
        targetVersion: String
    ): Boolean {
        val target = targetVersion.trim().also { normalized ->
            require(normalized.isNotBlank()) { "targetVersion must not be blank" }
        }
        val existing = record(ownerUid, deviceUid)
        return existing == null || existing.targetVersion != target
    }

    override suspend fun markDelivered(
        ownerUid: String,
        deviceUid: String,
        targetVersion: String,
        deliveryKey: String,
        resolved: Boolean
    ) {
        val owner = requireOwnerUid(ownerUid)
        val device = requireDeviceUid(deviceUid)
        val target = targetVersion.trim()
        val key = deliveryKey.trim().also { normalized ->
            require(normalized.isNotBlank()) { "deliveryKey must not be blank" }
        }
        val deliveredAt = nowMillis().coerceAtLeast(1L)

        updateOwner(owner) { records ->
            records[device] = DeviceUpdateNotificationRecord.newBuilder()
                .setDeviceUid(device)
                .setTargetVersion(target)
                .setDeliveryKey(key)
                .setDeliveredAtEpochMillis(deliveredAt)
                .setResolved(resolved)
                .build()
        }
    }

    override suspend fun markResolved(
        ownerUid: String,
        deviceUid: String,
        resolvedVersion: String
    ) {
        val owner = requireOwnerUid(ownerUid)
        val device = requireDeviceUid(deviceUid)
        val version = resolvedVersion.trim()
        val deliveredAt = nowMillis().coerceAtLeast(1L)

        updateOwner(owner) { records ->
            records[device] = DeviceUpdateNotificationRecord.newBuilder()
                .setDeviceUid(device)
                .setTargetVersion(version)
                .setDeliveryKey("resolved:$version")
                .setDeliveredAtEpochMillis(deliveredAt)
                .setResolved(true)
                .build()
        }
    }

    override suspend fun clearDevice(ownerUid: String, deviceUid: String) {
        val owner = requireOwnerUid(ownerUid)
        val device = requireDeviceUid(deviceUid)
        updateOwner(owner) { records -> records.remove(device) }
    }

    override suspend fun clearOwner(ownerUid: String) {
        val owner = requireOwnerUid(ownerUid)
        context.deviceUpdateNotificationStateDataStore.updateData { current ->
            val validated = DeviceUpdateNotificationStateRules.validateStore(current)
            DeviceUpdateNotificationStateRules.validateStore(
                validated.toBuilder()
                    .clearOwnerStates()
                    .addAllOwnerStates(
                        validated.ownerStatesList.filterNot { state ->
                            state.ownerUid == owner
                        }
                    )
                    .build()
            )
        }
    }

    private suspend fun updateOwner(
        ownerUid: String,
        transform: (MutableMap<String, DeviceUpdateNotificationRecord>) -> Unit
    ) {
        context.deviceUpdateNotificationStateDataStore.updateData { current ->
            val validated = DeviceUpdateNotificationStateRules.validateStore(current)
            val ownerState = validated.ownerStatesList.firstOrNull { state ->
                state.ownerUid == ownerUid
            }
            val records = ownerState?.recordsList.orEmpty()
                .associateByTo(linkedMapOf()) { record -> record.deviceUid }
            transform(records)

            val retainedOwners = validated.ownerStatesList
                .filterNot { state -> state.ownerUid == ownerUid }
                .toMutableList()
            if (records.isNotEmpty()) {
                retainedOwners += OwnerDeviceUpdateNotificationState.newBuilder()
                    .setOwnerUid(ownerUid)
                    .addAllRecords(records.values.sortedBy { record -> record.deviceUid })
                    .build()
            }

            DeviceUpdateNotificationStateRules.validateStore(
                validated.toBuilder()
                    .clearOwnerStates()
                    .addAllOwnerStates(retainedOwners.sortedBy { state -> state.ownerUid })
                    .build()
            )
        }
    }

    private fun requireOwnerUid(ownerUid: String): String {
        return UserDataScope.normalizeOwnerUid(ownerUid).also { normalized ->
            require(normalized.isNotBlank() && normalized == ownerUid) {
                "ownerUid must be canonical and non-blank"
            }
        }
    }

    private fun requireDeviceUid(deviceUid: String): String {
        return deviceUid.trim().also { normalized ->
            require(normalized.isNotBlank() && normalized == deviceUid) {
                "deviceUid must be canonical and non-blank"
            }
        }
    }

    private fun DeviceUpdateNotificationRecord.toSnapshot():
        DeviceUpdateNotificationLedgerRecord = DeviceUpdateNotificationLedgerRecord(
        deviceUid = deviceUid,
        targetVersion = targetVersion,
        deliveryKey = deliveryKey,
        deliveredAtEpochMillis = deliveredAtEpochMillis,
        resolved = resolved
    )
}

private object NoOpDeviceUpdateNotificationLedger : DeviceUpdateNotificationLedger {
    override suspend fun record(
        ownerUid: String,
        deviceUid: String
    ): DeviceUpdateNotificationLedgerRecord? = null

    override suspend fun recordedDeviceUids(ownerUid: String): Set<String> = emptySet()

    override suspend fun shouldDeliverAvailability(
        ownerUid: String,
        deviceUid: String,
        targetVersion: String
    ): Boolean = true

    override suspend fun markDelivered(
        ownerUid: String,
        deviceUid: String,
        targetVersion: String,
        deliveryKey: String,
        resolved: Boolean
    ) = Unit

    override suspend fun markResolved(
        ownerUid: String,
        deviceUid: String,
        resolvedVersion: String
    ) = Unit

    override suspend fun clearDevice(ownerUid: String, deviceUid: String) = Unit

    override suspend fun clearOwner(ownerUid: String) = Unit
}
