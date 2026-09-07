package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTimerGenerationAuthorityTest {
    @Test
    fun `reconnect retains presentation snapshot and accepts lower reboot uptime`() {
        val store = DeviceTimerRuntimeStateStore()
        val first = DeviceTimerStatusParser.parse(
            DeviceTimerRuntimeFixtures.status(uptimeMs = 90_000L)
        )
        val rebooted = DeviceTimerStatusParser.parse(
            DeviceTimerRuntimeFixtures.status(uptimeMs = 1_000L)
        )

        store.beginGeneration(DEVICE_UID, G1)
        assertTrue(store.recordStatus(DEVICE_UID, G1, first))
        store.invalidate(DEVICE_UID, G1)
        store.beginGeneration(DEVICE_UID, G2)

        assertEquals(90_000L, store.states.value.getValue(DEVICE_UID).status?.uptimeMs)
        assertFalse(store.isAuthoritative(DEVICE_UID, G2))
        assertTrue(store.recordStatus(DEVICE_UID, G2, rebooted))
        assertEquals(1_000L, store.states.value.getValue(DEVICE_UID).status?.uptimeMs)
        assertTrue(store.isAuthoritative(DEVICE_UID, G2))
    }

    @Test
    fun `late previous generation status cannot overwrite new authoritative Timer state`() {
        val store = DeviceTimerRuntimeStateStore()
        val oldSession = DeviceTimerStatusParser.parse(
            DeviceTimerRuntimeFixtures.status(uptimeMs = 90_000L)
        )
        val newSession = DeviceTimerStatusParser.parse(
            DeviceTimerRuntimeFixtures.status(uptimeMs = 2_000L)
        )
        val lateOldReply = DeviceTimerStatusParser.parse(
            DeviceTimerRuntimeFixtures.status(uptimeMs = 95_000L)
        )

        store.beginGeneration(DEVICE_UID, G1)
        store.recordStatus(DEVICE_UID, G1, oldSession)
        store.beginGeneration(DEVICE_UID, G2)
        store.recordStatus(DEVICE_UID, G2, newSession)

        assertFalse(store.recordStatus(DEVICE_UID, G1, lateOldReply))
        assertEquals(2_000L, store.states.value.getValue(DEVICE_UID).status?.uptimeMs)
        assertTrue(store.isAuthoritative(DEVICE_UID, G2))
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-TIMER-GENERATION")
        val G1 = DeviceRuntimeConnectionGeneration(1L)
        val G2 = DeviceRuntimeConnectionGeneration(2L)
    }
}
