package com.aqua.aqualight.ui.tabs.devices.detail.dosing.root

import com.aqua.aqualight.application.devices.DeviceChannelSlots
import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.DeviceSlotIndex
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.data.devices.dosing.UnavailableDeviceDosingChannelNavigationOperations
import com.aqua.aqualight.data.devices.dosing.UnavailableDeviceDosingChannelOperations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDosingRootPendingTopologyTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pending exact catalog renders the real two or four pump layout`() = runTest(dispatcher) {
        listOf(2, 4).forEach { expectedCount ->
            val viewModel = DeviceDosingRootViewModel(
                operations = PendingRootOperations(rootSnapshot(expectedCount)),
                channelNavigationOperations = UnavailableDeviceDosingChannelNavigationOperations,
                channelOperations = UnavailableDeviceDosingChannelOperations
            )

            viewModel.bind(DEVICE_UID, "Dose Pro")

            assertEquals(expectedCount, viewModel.uiState.value.pumpCount)
            assertEquals(expectedCount, viewModel.uiState.value.channels.size)
            assertEquals(
                (1..expectedCount).toList(),
                viewModel.uiState.value.channels.map { channel -> channel.channelNumber }
            )
        }
    }

    private class PendingRootOperations(
        snapshot: DeviceRootSnapshot
    ) : DeviceRootOperations {
        private val snapshots = MutableStateFlow<DeviceRootSnapshot?>(snapshot)

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = snapshots

        override fun current(deviceUid: String): DeviceRootSnapshot = requireNotNull(snapshots.value)

        override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)
    }

    private companion object {
        const val DEVICE_UID = "pending-dose-pro"
    }
}

private fun rootSnapshot(channelCount: Int) = DeviceRootSnapshot(
    deviceUid = "pending-dose-pro",
    title = "Dose Pro $channelCount",
    family = OwnerDeviceFamily.DOSING,
    catalogState = DeviceRootCatalogState.PENDING,
    channelSlots = DeviceChannelSlots(
        lightChannels = emptyList(),
        timerChannels = emptyList(),
        dosingChannels = List(channelCount) { index ->
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
