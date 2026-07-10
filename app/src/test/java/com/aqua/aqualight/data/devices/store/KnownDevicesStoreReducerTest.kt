package com.aqua.aqualight.data.devices.store

import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownDevicesStoreReducerTest {

    @Test
    fun sameDeviceUid_isIsolatedBetweenOwners() {
        val store = KnownDevicesStoreReducer.upsertDevices(
            store = KnownDevicesStore.getDefaultInstance(),
            ownerUid = OWNER_A,
            snapshots = listOf(snapshot(DEVICE_UID, "Owner A device"))
        ).let { current ->
            KnownDevicesStoreReducer.upsertDevices(
                store = current,
                ownerUid = OWNER_B,
                snapshots = listOf(snapshot(DEVICE_UID, "Owner B device"))
            )
        }

        val ownerADevices = KnownDevicesStoreReducer.devicesForOwner(store, OWNER_A)
        val ownerBDevices = KnownDevicesStoreReducer.devicesForOwner(store, OWNER_B)

        assertEquals(listOf("Owner A device"), ownerADevices.map { it.identity.customName })
        assertEquals(listOf("Owner B device"), ownerBDevices.map { it.identity.customName })
    }

    @Test
    fun clearingOneOwner_preservesOtherOwnersDevices() {
        val initialStore = KnownDevicesStoreReducer.upsertDevices(
            store = KnownDevicesStore.getDefaultInstance(),
            ownerUid = OWNER_A,
            snapshots = listOf(snapshot(DEVICE_UID, "Owner A device"))
        ).let { current ->
            KnownDevicesStoreReducer.upsertDevices(
                store = current,
                ownerUid = OWNER_B,
                snapshots = listOf(snapshot(DEVICE_UID, "Owner B device"))
            )
        }

        val clearedStore = KnownDevicesStoreReducer.clearOwnerDevices(
            store = initialStore,
            ownerUid = OWNER_A
        )

        assertTrue(
            KnownDevicesStoreReducer.devicesForOwner(clearedStore, OWNER_A).isEmpty()
        )
        assertEquals(
            listOf("Owner B device"),
            KnownDevicesStoreReducer.devicesForOwner(clearedStore, OWNER_B)
                .map { it.identity.customName }
        )
    }

    @Test
    fun ignoredDeviceState_isScopedPerOwner() {
        val ignoredForOwnerA = KnownDevicesStoreReducer.ignoreDevice(
            store = KnownDevicesStore.getDefaultInstance(),
            ownerUid = OWNER_A,
            deviceUid = DeviceUid(DEVICE_UID)
        )

        assertTrue(
            DEVICE_UID in KnownDevicesStoreReducer.ignoredDeviceUidsForOwner(
                ignoredForOwnerA,
                OWNER_A
            )
        )
        assertFalse(
            DEVICE_UID in KnownDevicesStoreReducer.ignoredDeviceUidsForOwner(
                ignoredForOwnerA,
                OWNER_B
            )
        )
    }

    @Test
    fun clearingIgnoredStateForOneOwner_preservesOtherOwner() {
        val store = KnownDevicesStoreReducer.ignoreDevice(
            store = KnownDevicesStore.getDefaultInstance(),
            ownerUid = OWNER_A,
            deviceUid = DeviceUid(DEVICE_UID)
        ).let { current ->
            KnownDevicesStoreReducer.ignoreDevice(
                store = current,
                ownerUid = OWNER_B,
                deviceUid = DeviceUid(DEVICE_UID)
            )
        }

        val clearedStore = KnownDevicesStoreReducer.clearOwnerIgnoredDevices(
            store = store,
            ownerUid = OWNER_A
        )

        assertTrue(
            KnownDevicesStoreReducer.ignoredDeviceUidsForOwner(
                clearedStore,
                OWNER_A
            ).isEmpty()
        )
        assertTrue(
            DEVICE_UID in KnownDevicesStoreReducer.ignoredDeviceUidsForOwner(
                clearedStore,
                OWNER_B
            )
        )
    }

    private fun snapshot(
        deviceUid: String,
        customName: String
    ): DeviceSnapshot {
        return DeviceSnapshot(
            identity = DeviceIdentity(
                uid = DeviceUid(deviceUid),
                customName = customName
            )
        )
    }

    private companion object {
        const val OWNER_A = "owner-a"
        const val OWNER_B = "owner-b"
        const val DEVICE_UID = "AQL-DEVICE-001"
    }
}
