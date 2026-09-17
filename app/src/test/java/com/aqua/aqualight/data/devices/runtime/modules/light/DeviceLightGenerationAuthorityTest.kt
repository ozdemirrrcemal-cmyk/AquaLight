package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightGenerationAuthorityTest {
    @Test
    fun `reconnect retains last Light presentation snapshot until new generation hydrates`() {
        val owner = DeviceLightRuntimeStateOwner()
        val first = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val second = first.copy(mode = DeviceLightMode.AUTO)

        owner.beginGeneration(DEVICE_UID, G1)
        assertTrue(owner.recordStatus(DEVICE_UID, G1, first))
        owner.invalidate(DEVICE_UID, G1)
        owner.beginGeneration(DEVICE_UID, G2)

        assertEquals(first, owner.statuses.value[DEVICE_UID])
        assertFalse(
            owner.isAuthoritative(DeviceLightRuntimeProjection.STATUS, DEVICE_UID, G2)
        )
        assertTrue(owner.recordStatus(DEVICE_UID, G2, second))
        assertEquals(second, owner.statuses.value[DEVICE_UID])
    }

    @Test
    fun `late old generation Light status cannot overwrite new state`() {
        val owner = DeviceLightRuntimeStateOwner()
        val first = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val second = first.copy(mode = DeviceLightMode.AUTO)
        val lateOld = first.copy(mode = DeviceLightMode.CUSTOM)

        owner.beginGeneration(DEVICE_UID, G1)
        owner.recordStatus(DEVICE_UID, G1, first)
        owner.beginGeneration(DEVICE_UID, G2)
        owner.recordStatus(DEVICE_UID, G2, second)

        assertFalse(owner.recordStatus(DEVICE_UID, G1, lateOld))
        assertEquals(second, owner.statuses.value[DEVICE_UID])
        assertTrue(
            owner.isAuthoritative(DeviceLightRuntimeProjection.STATUS, DEVICE_UID, G2)
        )
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-LIGHT-GENERATION")
        val G1 = DeviceRuntimeConnectionGeneration(1L)
        val G2 = DeviceRuntimeConnectionGeneration(2L)
    }
}
