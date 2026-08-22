package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceChannelSlots
import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.DeviceSlotIndex
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.data.devices.dosing.UnavailableDeviceDosingChannelOperations
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.sampleDosingChannelSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDeviceMenuPresentationPreparationOperationsTest {

    @Test
    fun `dosing preparation succeeds only after complete authoritative refresh`() = runTest {
        val channels = FakeChannels(completeChannels())
        val preparation = preparation(channels)

        val prepared = preparation.prepare(access())

        assertTrue(prepared)
        assertTrue(channels.refreshCalled)
    }

    @Test
    fun `partial dosing set blocks device menu entry`() = runTest {
        val channels = FakeChannels(completeChannels().take(1))

        val prepared = preparation(channels).prepare(access())

        assertFalse(prepared)
    }

    @Test
    fun `failed dosing refresh blocks device menu entry`() = runTest {
        val channels = FakeChannels(
            snapshots = completeChannels(),
            refreshResult = false
        )

        val prepared = preparation(channels).prepare(access())

        assertFalse(prepared)
    }

    private fun preparation(channels: DeviceDosingChannelOperations) =
        DefaultDeviceMenuPresentationPreparationOperations(
            rootOperations = FakeRootOperations(rootSnapshot()),
            dosingOperations = channels
        )

    private fun access() = DeviceMenuAccessResult.Available(
        deviceUid = DEVICE_UID,
        title = "Dose Pro",
        family = OwnerDeviceFamily.DOSING
    )

    private class FakeRootOperations(
        private val snapshot: DeviceRootSnapshot
    ) : DeviceRootOperations {
        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = flowOf(snapshot)

        override fun current(deviceUid: String): DeviceRootSnapshot = snapshot

        override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeChannels(
        private val snapshots: List<DeviceDosingChannelSnapshot>,
        private val refreshResult: Boolean = true
    ) : DeviceDosingChannelOperations by UnavailableDeviceDosingChannelOperations {
        var refreshCalled: Boolean = false

        override suspend fun refreshAll(deviceUid: String): Boolean {
            refreshCalled = true
            return refreshResult
        }

        override fun currentAll(deviceUid: String): List<DeviceDosingChannelSnapshot> = snapshots
    }

    private companion object {
        const val DEVICE_UID = "device-1"
    }
}

private fun rootSnapshot() = DeviceRootSnapshot(
    deviceUid = "device-1",
    title = "Dose Pro",
    availability = OwnerDeviceAvailability.REACHABLE,
    family = OwnerDeviceFamily.DOSING,
    catalogState = DeviceRootCatalogState.VALID,
    dosingChannelCount = 2,
    channelSlots = DeviceChannelSlots(
        lightChannels = emptyList(),
        timerChannels = emptyList(),
        dosingChannels = List(2) { index ->
            DeviceDosingChannelSlot(
                index = DeviceSlotIndex(index),
                wireKey = DeviceChannelWireKey("channel${index + 1}"),
                defaultDisplayName = "Channel ${index + 1}",
                displayNameEditable = true
            )
        },
        fanOutputs = emptyList(),
        temperatureSensors = emptyList()
    )
)

private fun completeChannels(): List<DeviceDosingChannelSnapshot> {
    val second = sampleDosingChannelSnapshot().copy(deviceUid = "device-1")
    val first = second.copy(
        slotId = "dosing:channel1",
        channelNumber = 1,
        channelTitle = "Channel 1"
    )
    return listOf(first, second)
}
