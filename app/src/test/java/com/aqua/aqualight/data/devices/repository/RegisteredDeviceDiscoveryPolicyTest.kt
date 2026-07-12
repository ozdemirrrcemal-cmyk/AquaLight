package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisteredDeviceDiscoveryPolicyTest {

    @Test
    fun `clean install rejects every discovered device`() {
        val result = RegisteredDeviceDiscoveryPolicy.filterRegisteredUpdates(
            registeredDeviceUids = emptySet(),
            discoveredDevices = listOf(snapshot(REGISTERED_UID), snapshot(UNKNOWN_UID))
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `only registered device discovery updates are accepted`() {
        val registeredUid = DeviceUid(REGISTERED_UID)

        val result = RegisteredDeviceDiscoveryPolicy.filterRegisteredUpdates(
            registeredDeviceUids = setOf(registeredUid),
            discoveredDevices = listOf(
                snapshot(UNKNOWN_UID),
                snapshot(REGISTERED_UID, lastSeenAtMillis = 42L)
            )
        )

        assertEquals(1, result.size)
        assertEquals(registeredUid, result.single().deviceUid)
        assertEquals(42L, result.single().lastSeenAtMillis)
    }

    @Test
    fun `removed device is rejected by later discovery`() {
        val result = RegisteredDeviceDiscoveryPolicy.filterRegisteredUpdates(
            registeredDeviceUids = setOf(DeviceUid(OTHER_REGISTERED_UID)),
            discoveredDevices = listOf(snapshot(REGISTERED_UID))
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `device uid comparison remains exact and fail closed`() {
        val result = RegisteredDeviceDiscoveryPolicy.filterRegisteredUpdates(
            registeredDeviceUids = setOf(DeviceUid(REGISTERED_UID)),
            discoveredDevices = listOf(snapshot(REGISTERED_UID.lowercase()))
        )

        assertTrue(result.isEmpty())
    }

    private fun snapshot(
        uid: String,
        lastSeenAtMillis: Long = 0L
    ): DeviceSnapshot {
        return DeviceSnapshot(
            identity = DeviceIdentity(uid = DeviceUid(uid)),
            product = DeviceProduct(displayName = "Test device"),
            lastSeenAtMillis = lastSeenAtMillis
        )
    }

    private companion object {
        const val REGISTERED_UID = "AQL-DEVICE-001"
        const val OTHER_REGISTERED_UID = "AQL-DEVICE-002"
        const val UNKNOWN_UID = "AQL-UNKNOWN-999"
    }
}
