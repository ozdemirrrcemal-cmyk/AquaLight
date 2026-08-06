package com.aqua.aqualight.data.notifications

import com.aqua.aqualight.data.store.StoreInvariantViolation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceUpdateNotificationStateRulesTest {

    @Test
    fun `valid owner and device records remain stable`() {
        val store = DeviceUpdateNotificationStateStore.newBuilder()
            .setSchemaVersion(1)
            .addOwnerStates(
                OwnerDeviceUpdateNotificationState.newBuilder()
                    .setOwnerUid(OWNER_A)
                    .addRecords(record(DEVICE_A, VERSION_A))
                    .addRecords(record(DEVICE_B, VERSION_B))
            )
            .build()

        assertEquals(store, DeviceUpdateNotificationStateRules.validateStore(store))
    }

    @Test
    fun `duplicate device records fail closed`() {
        val store = DeviceUpdateNotificationStateStore.newBuilder()
            .setSchemaVersion(1)
            .addOwnerStates(
                OwnerDeviceUpdateNotificationState.newBuilder()
                    .setOwnerUid(OWNER_A)
                    .addRecords(record(DEVICE_A, VERSION_A))
                    .addRecords(record(DEVICE_A, VERSION_B))
            )
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            DeviceUpdateNotificationStateRules.validateStore(store)
        }
    }

    @Test
    fun `unsorted owner records fail closed`() {
        val store = DeviceUpdateNotificationStateStore.newBuilder()
            .setSchemaVersion(1)
            .addOwnerStates(
                OwnerDeviceUpdateNotificationState.newBuilder()
                    .setOwnerUid(OWNER_B)
                    .addRecords(record(DEVICE_A, VERSION_A))
            )
            .addOwnerStates(
                OwnerDeviceUpdateNotificationState.newBuilder()
                    .setOwnerUid(OWNER_A)
                    .addRecords(record(DEVICE_A, VERSION_A))
            )
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            DeviceUpdateNotificationStateRules.validateStore(store)
        }
    }

    private fun record(deviceUid: String, targetVersion: String) =
        DeviceUpdateNotificationRecord.newBuilder()
            .setDeviceUid(deviceUid)
            .setTargetVersion(targetVersion)
            .setDeliveryKey("available:$targetVersion")
            .setDeliveredAtEpochMillis(1L)
            .build()

    private companion object {
        const val OWNER_A = "owner-a"
        const val OWNER_B = "owner-b"
        const val DEVICE_A = "device-a"
        const val DEVICE_B = "device-b"
        const val VERSION_A = "1.2.0"
        const val VERSION_B = "1.3.0"
    }
}
