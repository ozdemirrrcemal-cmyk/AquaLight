package com.aqua.aqualight.data.devices.provisioning.repository

import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Test

class AqlProvisioningRegistrationModeResolverTest {

    @Test
    fun `missing local registration resolves to new device`() {
        val result = AqlProvisioningRegistrationModeResolver.resolve(
            existingDeviceUid = null,
            verifiedDeviceUid = DeviceUid(DEVICE_UID)
        )

        assertEquals(
            AqlProvisioningRegistrationMode.NEW_DEVICE,
            result
        )
    }

    @Test
    fun `same local device resolves to existing reconfiguration`() {
        val result = AqlProvisioningRegistrationModeResolver.resolve(
            existingDeviceUid = DeviceUid(DEVICE_UID.lowercase()),
            verifiedDeviceUid = DeviceUid(DEVICE_UID)
        )

        assertEquals(
            AqlProvisioningRegistrationMode.RECONFIGURE_EXISTING,
            result
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mismatched local and verified identities are rejected`() {
        AqlProvisioningRegistrationModeResolver.resolve(
            existingDeviceUid = DeviceUid("OTHER-DEVICE"),
            verifiedDeviceUid = DeviceUid(DEVICE_UID)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank verified identity is rejected`() {
        AqlProvisioningRegistrationModeResolver.resolve(
            existingDeviceUid = null,
            verifiedDeviceUid = DeviceUid("   ")
        )
    }

    private companion object {
        const val DEVICE_UID = "AQL-DEVICE-001"
    }
}
