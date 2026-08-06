package com.aqua.aqualight.data.notifications

import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.user.UserDataScope

internal object DeviceUpdateNotificationStateRules {
    private const val SCHEMA_VERSION = 1
    private const val MAX_OWNERS = 32
    private const val MAX_RECORDS_PER_OWNER = 1_024

    fun defaultStore(): DeviceUpdateNotificationStateStore {
        return DeviceUpdateNotificationStateStore.newBuilder()
            .setSchemaVersion(SCHEMA_VERSION)
            .build()
    }

    fun validateStore(
        store: DeviceUpdateNotificationStateStore
    ): DeviceUpdateNotificationStateStore {
        validateStoreHeader(store)
        validateOwnerStates(store.ownerStatesList)
        return store
    }

    fun canonicalOwner(ownerUid: String): String {
        val canonical = UserDataScope.normalizeOwnerUid(ownerUid)
        if (canonical.isBlank() || canonical != ownerUid) {
            violation("Device update notification owner UID must be canonical and non-blank.")
        }
        return canonical
    }

    fun canonicalDeviceUid(deviceUid: String): String {
        val canonical = deviceUid.trim()
        if (canonical.isBlank() || canonical != deviceUid) {
            violation("Device update notification device UID must be canonical and non-blank.")
        }
        return canonical
    }

    private fun validateStoreHeader(store: DeviceUpdateNotificationStateStore) {
        if (store.schemaVersion != SCHEMA_VERSION) {
            violation("Device update notification state schema is unsupported.")
        }
        if (store.ownerStatesCount > MAX_OWNERS) {
            violation("Device update notification state exceeds the owner limit.")
        }
    }

    private fun validateOwnerStates(
        ownerStates: List<OwnerDeviceUpdateNotificationState>
    ) {
        var previousOwner = ""
        val owners = mutableSetOf<String>()
        ownerStates.forEach { ownerState ->
            val owner = canonicalOwner(ownerState.ownerUid)
            if (!owners.add(owner)) {
                violation("Duplicate device update notification owner: $owner")
            }
            if (previousOwner.isNotEmpty() && owner <= previousOwner) {
                violation("Device update notification owners must be sorted.")
            }
            validateRecords(ownerState)
            previousOwner = owner
        }
    }

    private fun validateRecords(ownerState: OwnerDeviceUpdateNotificationState) {
        if (ownerState.recordsCount > MAX_RECORDS_PER_OWNER) {
            violation("Device update notification record count exceeds the owner limit.")
        }

        var previousDeviceUid = ""
        val deviceUids = mutableSetOf<String>()
        ownerState.recordsList.forEach { record ->
            val deviceUid = canonicalDeviceUid(record.deviceUid)
            if (!deviceUids.add(deviceUid)) {
                violation("Duplicate device update notification record: $deviceUid")
            }
            if (previousDeviceUid.isNotEmpty() && deviceUid <= previousDeviceUid) {
                violation("Device update notification records must be sorted.")
            }
            validateRecord(record)
            previousDeviceUid = deviceUid
        }
    }

    private fun validateRecord(record: DeviceUpdateNotificationRecord) {
        if (record.targetVersion != record.targetVersion.trim()) {
            violation("Device update target version must be canonical.")
        }
        if (record.deliveryKey.isBlank() || record.deliveryKey != record.deliveryKey.trim()) {
            violation("Device update delivery key must be canonical and non-blank.")
        }
        if (record.deliveredAtEpochMillis <= 0L) {
            violation("Device update delivery time must be positive.")
        }
    }

    private fun violation(message: String): Nothing {
        throw StoreInvariantViolation(message)
    }
}
