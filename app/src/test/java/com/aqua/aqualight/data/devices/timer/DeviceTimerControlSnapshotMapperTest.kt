package com.aqua.aqualight.data.devices.timer

import com.aqua.aqualight.application.devices.DeviceChannelSlots
import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.DeviceSlotIndex
import com.aqua.aqualight.application.devices.DeviceTimerChannelSlot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeFixtures
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeState
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerStatus
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerStatusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTimerControlSnapshotMapperTest {

    @Test
    fun `authoritative global and channel statuses map to stable application slots`() {
        val global = DeviceTimerStatusParser.parse(DeviceTimerRuntimeFixtures.globalStatus())
        val channel = DeviceTimerStatusParser.parse(DeviceTimerRuntimeFixtures.channelStatus())

        val snapshot = DeviceTimerControlSnapshotMapper.map(
            root = timerRoot(),
            state = authoritativeState(
                status = global,
                channelDetails = mapOf("channel1" to channel)
            )
        )

        requireNotNull(snapshot)
        assertEquals(listOf("timer:channel1", "timer:channel2"), snapshot.channels.map { it.slotId })
        assertEquals("Filter", snapshot.channels.first().displayName)
        assertEquals("Day Filter", snapshot.channels.first().schedules?.single()?.name)
        assertNull(snapshot.channels.last().schedules)
        assertTrue(snapshot.capabilities.supportsSchedules)
    }

    @Test
    fun `partial channel status cannot become a root control snapshot`() {
        val channelScoped = DeviceTimerStatusParser.parse(
            DeviceTimerRuntimeFixtures.channelStatus()
        )

        val snapshot = DeviceTimerControlSnapshotMapper.map(
            root = timerRoot(),
            state = authoritativeState(status = channelScoped)
        )

        assertNull(snapshot)
    }

    @Test
    fun `partial global topology cannot become a root control snapshot`() {
        val partial = DeviceTimerStatusParser.parse(DeviceTimerRuntimeFixtures.globalStatus())
            .let { status -> status.copy(channels = status.channels.dropLast(1)) }

        val snapshot = DeviceTimerControlSnapshotMapper.map(
            root = timerRoot(),
            state = authoritativeState(status = partial)
        )

        assertNull(snapshot)
    }

    @Test
    fun `commercial catalog identity mismatch fails closed`() {
        val global = DeviceTimerStatusParser.parse(DeviceTimerRuntimeFixtures.globalStatus())
        val mismatchedRoot = timerRoot().copy(
            channelSlots = timerSlots(defaultNamePrefix = "Relay")
        )

        val snapshot = DeviceTimerControlSnapshotMapper.map(
            root = mismatchedRoot,
            state = authoritativeState(status = global)
        )

        assertNull(snapshot)
    }

    @Test
    fun `non authoritative generation is never projected`() {
        val global = DeviceTimerStatusParser.parse(DeviceTimerRuntimeFixtures.globalStatus())

        val snapshot = DeviceTimerControlSnapshotMapper.map(
            root = timerRoot(),
            state = authoritativeState(status = global).copy(authoritative = false)
        )

        assertNull(snapshot)
    }
}

private fun authoritativeState(
    status: DeviceTimerStatus,
    channelDetails: Map<String, DeviceTimerStatus> = emptyMap()
) = DeviceTimerRuntimeState(
    connectionGeneration = DeviceRuntimeConnectionGeneration(1L),
    authoritative = true,
    status = status,
    channelDetails = channelDetails
)

private fun timerRoot() = DeviceRootSnapshot(
    deviceUid = "timer-pro-2",
    title = "Relay Pro 2",
    availability = OwnerDeviceAvailability.REACHABLE,
    family = OwnerDeviceFamily.TIMER,
    catalogState = DeviceRootCatalogState.VALID,
    timerChannelCount = 2,
    channelSlots = timerSlots()
)

private fun timerSlots(defaultNamePrefix: String = "Channel") = DeviceChannelSlots(
    lightChannels = emptyList(),
    timerChannels = List(2) { index ->
        DeviceTimerChannelSlot(
            index = DeviceSlotIndex(index),
            wireKey = DeviceChannelWireKey("channel${index + 1}"),
            defaultDisplayName = "$defaultNamePrefix ${index + 1}",
            displayNameEditable = true
        )
    },
    dosingChannels = emptyList(),
    fanOutputs = emptyList(),
    temperatureSensors = emptyList()
)
