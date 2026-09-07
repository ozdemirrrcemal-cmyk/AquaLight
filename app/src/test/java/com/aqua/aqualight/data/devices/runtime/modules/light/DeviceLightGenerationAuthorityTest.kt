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
        val store = DeviceLightRuntimeStateStore()
        val first = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val second = first.copy(programCount = first.programCount + 1)

        store.beginGeneration(DEVICE_UID, G1)
        assertTrue(store.recordStatus(DEVICE_UID, G1, first))
        store.invalidate(DEVICE_UID, G1)
        store.beginGeneration(DEVICE_UID, G2)

        assertEquals(first, store.statuses.value[DEVICE_UID])
        assertFalse(store.isStatusAuthoritative(DEVICE_UID, G2))
        assertTrue(store.recordStatus(DEVICE_UID, G2, second))
        assertEquals(second, store.statuses.value[DEVICE_UID])
    }

    @Test
    fun `late old generation Light status cannot overwrite new state`() {
        val store = DeviceLightRuntimeStateStore()
        val first = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val second = first.copy(programCount = first.programCount + 1)
        val lateOld = first.copy(programCount = first.programCount + 2)

        store.beginGeneration(DEVICE_UID, G1)
        store.recordStatus(DEVICE_UID, G1, first)
        store.beginGeneration(DEVICE_UID, G2)
        store.recordStatus(DEVICE_UID, G2, second)

        assertFalse(store.recordStatus(DEVICE_UID, G1, lateOld))
        assertEquals(second, store.statuses.value[DEVICE_UID])
        assertTrue(store.isStatusAuthoritative(DEVICE_UID, G2))
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-LIGHT-GENERATION")
        val G1 = DeviceRuntimeConnectionGeneration(1L)
        val G2 = DeviceRuntimeConnectionGeneration(2L)
    }
}
