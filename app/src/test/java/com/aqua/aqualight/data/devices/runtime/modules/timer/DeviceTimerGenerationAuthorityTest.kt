package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTimerGenerationAuthorityTest {
    @Test
    fun `reconnect retains presentation snapshot and accepts lower reboot uptime`() {
        val store = DeviceTimerRuntimeStateStore()
        val first = DeviceTimerStatusParser.parse(
            DeviceTimerRuntimeFixtures.globalStatus(
                uptimeMs = TIMER_TEST_FIRST_SESSION_UPTIME_MILLIS
            )
        )
        val rebooted = DeviceTimerStatusParser.parse(
            DeviceTimerRuntimeFixtures.globalStatus(uptimeMs = TIMER_TEST_REBOOT_UPTIME_MILLIS)
        )
        val channelDetail = DeviceTimerStatusParser.parse(
            DeviceTimerRuntimeFixtures.channelStatus(
                uptimeMs = TIMER_TEST_FIRST_SESSION_UPTIME_MILLIS
            )
        )

        store.beginGeneration(DEVICE_UID, G1)
        assertTrue(store.recordStatus(DEVICE_UID, G1, first))
        assertTrue(store.recordStatus(DEVICE_UID, G1, channelDetail))
        assertTrue(store.states.value.getValue(DEVICE_UID).channelDetails.isNotEmpty())
        store.invalidate(DEVICE_UID, G1)
        assertFalse(store.states.value.getValue(DEVICE_UID).authoritative)
        assertNull(store.currentAuthoritativeState(DEVICE_UID))
        store.beginGeneration(DEVICE_UID, G2)

        assertEquals(
            TIMER_TEST_FIRST_SESSION_UPTIME_MILLIS,
            store.states.value.getValue(DEVICE_UID).status?.uptimeMs
        )
        assertEquals(G2, store.states.value.getValue(DEVICE_UID).connectionGeneration)
        assertFalse(store.states.value.getValue(DEVICE_UID).authoritative)
        assertTrue(store.states.value.getValue(DEVICE_UID).channelDetails.isEmpty())
        assertNull(store.currentAuthoritativeState(DEVICE_UID))
        assertFalse(store.isAuthoritative(DEVICE_UID, G2))
        assertTrue(store.recordStatus(DEVICE_UID, G2, rebooted))
        assertEquals(
            TIMER_TEST_REBOOT_UPTIME_MILLIS,
            store.states.value.getValue(DEVICE_UID).status?.uptimeMs
        )
        assertTrue(store.states.value.getValue(DEVICE_UID).authoritative)
        assertEquals(
            store.states.value.getValue(DEVICE_UID),
            store.currentAuthoritativeState(DEVICE_UID)
        )
        assertTrue(store.isAuthoritative(DEVICE_UID, G2))
    }

    @Test
    fun `late previous generation status cannot overwrite new authoritative Timer state`() {
        val store = DeviceTimerRuntimeStateStore()
        val oldSession = DeviceTimerStatusParser.parse(
            DeviceTimerRuntimeFixtures.globalStatus(
                uptimeMs = TIMER_TEST_FIRST_SESSION_UPTIME_MILLIS
            )
        )
        val newSession = DeviceTimerStatusParser.parse(
            DeviceTimerRuntimeFixtures.globalStatus(
                uptimeMs = TIMER_TEST_NEW_SESSION_UPTIME_MILLIS
            )
        )
        val lateOldReply = DeviceTimerStatusParser.parse(
            DeviceTimerRuntimeFixtures.globalStatus(
                uptimeMs = TIMER_TEST_LATE_SESSION_UPTIME_MILLIS
            )
        )

        store.beginGeneration(DEVICE_UID, G1)
        store.recordStatus(DEVICE_UID, G1, oldSession)
        store.beginGeneration(DEVICE_UID, G2)
        assertFalse(store.states.value.getValue(DEVICE_UID).authoritative)
        assertNull(store.currentAuthoritativeState(DEVICE_UID))
        store.recordStatus(DEVICE_UID, G2, newSession)

        assertFalse(store.recordStatus(DEVICE_UID, G1, lateOldReply))
        assertEquals(
            TIMER_TEST_NEW_SESSION_UPTIME_MILLIS,
            store.states.value.getValue(DEVICE_UID).status?.uptimeMs
        )
        assertTrue(store.isAuthoritative(DEVICE_UID, G2))
    }

    @Test
    fun `same-millisecond older revision cannot roll back authoritative Timer state`() {
        val store = DeviceTimerRuntimeStateStore()
        val current = DeviceTimerStatusParser.parse(
            DeviceTimerRuntimeFixtures.globalStatus(
                uptimeMs = TIMER_TEST_DEFAULT_UPTIME_MILLIS,
                revision = TIMER_TEST_APPLIED_REVISION
            )
        )
        val stale = DeviceTimerStatusParser.parse(
            DeviceTimerRuntimeFixtures.globalStatus(
                uptimeMs = TIMER_TEST_DEFAULT_UPTIME_MILLIS,
                revision = TIMER_TEST_BASE_REVISION
            )
        )

        store.beginGeneration(DEVICE_UID, G1)
        assertTrue(store.recordStatus(DEVICE_UID, G1, current))
        assertFalse(store.recordStatus(DEVICE_UID, G1, stale))
        assertEquals(
            TIMER_TEST_APPLIED_REVISION,
            store.states.value.getValue(DEVICE_UID).status?.revision
        )
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-TIMER-GENERATION")
        val G1 = DeviceRuntimeConnectionGeneration(1L)
        val G2 = DeviceRuntimeConnectionGeneration(2L)
    }
}
