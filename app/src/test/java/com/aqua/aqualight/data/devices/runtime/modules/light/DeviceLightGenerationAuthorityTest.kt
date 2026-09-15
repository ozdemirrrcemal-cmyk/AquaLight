package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightGenerationAuthorityTest {
    @Test
    fun `reconnect retains last Light presentation snapshot until new generation hydrates`() {
        val store = DeviceLightRuntimeStateStore()
        val first = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val second = first.copy(mode = DeviceLightMode.AUTO)

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
        val second = first.copy(mode = DeviceLightMode.AUTO)
        val lateOld = first.copy(mode = DeviceLightMode.CUSTOM)

        store.beginGeneration(DEVICE_UID, G1)
        store.recordStatus(DEVICE_UID, G1, first)
        store.beginGeneration(DEVICE_UID, G2)
        store.recordStatus(DEVICE_UID, G2, second)

        assertFalse(store.recordStatus(DEVICE_UID, G1, lateOld))
        assertEquals(second, store.statuses.value[DEVICE_UID])
        assertTrue(store.isStatusAuthoritative(DEVICE_UID, G2))
    }

    @Test
    fun `Light authority set is revoked together and retained only for presentation`() {
        val store = DeviceLightRuntimeStateStore()
        val status = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val plan = DeviceLightManagedPlanDocument(
            storageGeneration = status.storageGeneration,
            revision = status.auto.planRevision,
            installed = false,
            planId = null,
            initialStartPercent = 100,
            phaseCount = 0,
            phases = emptyList(),
            runtime = DeviceLightManagedPlanRuntime(
                clockReady = status.scheduler.ready,
                state = DeviceLightManagedPlanRuntimeState.NOT_INSTALLED,
                activePhaseIndex = null,
                transitionPermille = null,
                nextTransitionEpochDay = null
            ),
            event = null
        )
        val graph = DeviceLightGraph(
            mode = status.mode,
            available = false,
            reason = DeviceLightGraphReason.MODE_HAS_NO_SCHEDULE,
            sourceRevision = 0,
            schedulerGeneration = status.scheduler.generation,
            localDate = status.scheduler.localDate,
            currentWeekdayMask = status.scheduler.currentWeekdayMask,
            nowTimeMs = status.scheduler.currentTimeMs,
            basis = DeviceLightGraphBasis.NONE,
            channelScale = 1_000,
            hasScheduleToday = false,
            points = emptyList(),
            autoSpans = emptyList(),
            planSpans = emptyList()
        )

        store.beginGeneration(DEVICE_UID, G1)
        assertTrue(store.recordStatus(DEVICE_UID, G1, status))
        assertTrue(store.recordManagedPlan(DEVICE_UID, G1, plan))
        assertTrue(store.recordGraph(DEVICE_UID, G1, graph))
        assertTrue(store.isStatusAuthoritative(DEVICE_UID, G1))
        assertTrue(store.isManagedPlanAuthoritative(DEVICE_UID, G1))
        assertTrue(store.isGraphAuthoritative(DEVICE_UID, G1))

        store.invalidateAuthoritySet(DEVICE_UID, G1)

        assertEquals(status, store.statuses.value[DEVICE_UID])
        assertEquals(plan, store.managedPlans.value[DEVICE_UID])
        assertEquals(graph, store.graphs.value[DEVICE_UID])
        assertNull(store.currentAuthoritativeStatus(DEVICE_UID))
        assertNull(store.currentAuthoritativeManagedPlan(DEVICE_UID))
        assertNull(store.currentAuthoritativeGraph(DEVICE_UID))

        assertTrue(store.recordStatus(DEVICE_UID, G1, status))
        assertTrue(store.isStatusAuthoritative(DEVICE_UID, G1))
        assertFalse(store.isManagedPlanAuthoritative(DEVICE_UID, G1))
        assertFalse(store.isGraphAuthoritative(DEVICE_UID, G1))
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-LIGHT-GENERATION")
        val G1 = DeviceRuntimeConnectionGeneration(1L)
        val G2 = DeviceRuntimeConnectionGeneration(2L)
    }
}
