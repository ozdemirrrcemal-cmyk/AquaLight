package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeGenerationAuthorityTest {
    @Test
    fun `new generation revokes old authority and rejects late old snapshot`() {
        val authority = DeviceRuntimeGenerationAuthority()

        assertTrue(authority.beginGeneration(DEVICE_UID, G1))
        assertTrue(authority.acceptAuthoritativeSnapshot(DEVICE_UID, G1))
        assertTrue(authority.isAuthoritative(DEVICE_UID, G1))

        assertTrue(authority.beginGeneration(DEVICE_UID, G2))
        assertFalse(authority.isAuthoritative(DEVICE_UID, G1))
        assertFalse(authority.isAuthoritative(DEVICE_UID, G2))
        assertFalse(authority.acceptAuthoritativeSnapshot(DEVICE_UID, G1))

        assertTrue(authority.acceptAuthoritativeSnapshot(DEVICE_UID, G2))
        assertTrue(authority.isAuthoritative(DEVICE_UID, G2))
    }

    @Test
    fun `invalidation revokes authority without changing generation`() {
        val authority = DeviceRuntimeGenerationAuthority()
        authority.beginGeneration(DEVICE_UID, G1)
        authority.acceptAuthoritativeSnapshot(DEVICE_UID, G1)

        authority.invalidate(DEVICE_UID, G1)

        assertTrue(authority.isCurrentGeneration(DEVICE_UID, G1))
        assertFalse(authority.isAuthoritative(DEVICE_UID, G1))
        assertFalse(authority.acceptsPatch(DEVICE_UID, G1))
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-AUTHORITY-TEST")
        val G1 = DeviceRuntimeConnectionGeneration(1L)
        val G2 = DeviceRuntimeConnectionGeneration(2L)
    }
}
