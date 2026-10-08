package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightRuntimeAuthorityCoordinatorTest {
    @Test
    fun `runtime projections gain authority independently`() {
        val coordinator = DeviceLightRuntimeAuthorityCoordinator()
        coordinator.beginGeneration(DEVICE_UID, G1)

        assertTrue(
            coordinator.acceptAuthoritativeSnapshot(
                DeviceLightRuntimeProjection.STATUS,
                DEVICE_UID,
                G1
            )
        )

        assertTrue(coordinator.isAuthoritative(DeviceLightRuntimeProjection.STATUS, DEVICE_UID, G1))
        assertFalse(
            coordinator.isAuthoritative(
                DeviceLightRuntimeProjection.TEMPERATURE_PROTECTION,
                DEVICE_UID,
                G1
            )
        )
        assertFalse(
            coordinator.isAuthoritative(DeviceLightRuntimeProjection.THERMAL, DEVICE_UID, G1)
        )
    }

    @Test
    fun `generation lifecycle advances and invalidates every projection`() {
        val coordinator = DeviceLightRuntimeAuthorityCoordinator()
        coordinator.beginGeneration(DEVICE_UID, G1)
        DeviceLightRuntimeProjection.entries.forEach { projection ->
            assertTrue(coordinator.acceptAuthoritativeSnapshot(projection, DEVICE_UID, G1))
        }

        coordinator.beginGeneration(DEVICE_UID, G2)

        DeviceLightRuntimeProjection.entries.forEach { projection ->
            assertFalse(coordinator.isAuthoritative(projection, DEVICE_UID, G2))
        }

        assertTrue(
            coordinator.acceptAuthoritativeSnapshot(
                DeviceLightRuntimeProjection.THERMAL,
                DEVICE_UID,
                G2
            )
        )
        coordinator.invalidate(DEVICE_UID, G2)
        assertFalse(
            coordinator.isAuthoritative(DeviceLightRuntimeProjection.THERMAL, DEVICE_UID, G2)
        )
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-LIGHT-AUTHORITY")
        val G1 = DeviceRuntimeConnectionGeneration(1L)
        val G2 = DeviceRuntimeConnectionGeneration(2L)
    }
}
