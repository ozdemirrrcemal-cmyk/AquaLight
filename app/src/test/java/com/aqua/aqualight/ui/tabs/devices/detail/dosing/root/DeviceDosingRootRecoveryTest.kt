package com.aqua.aqualight.ui.tabs.devices.detail.dosing.root

import com.aqua.aqualight.application.devices.DeviceChannelSlots
import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.DeviceSlotIndex
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.data.devices.dosing.UnavailableDeviceDosingChannelNavigationOperations
import com.aqua.aqualight.data.devices.dosing.UnavailableDeviceDosingChannelOperations
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.sampleDosingChannelSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDosingRootRecoveryTest {

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
    fun `restored destination leaves instead of rendering an empty unavailable surface`() =
        runTest(dispatcher) {
            val preparation = FakePreparationOperations(
                DeviceControlSurfacePreparationResult.Unavailable(
                    DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
                )
            )
            val viewModel = viewModel(
                rootOperations = FakeRootOperations(null),
                channelOperations = FakeChannelOperations(emptyList()),
                preparationOperations = preparation
            )

            viewModel.bind(DEVICE_UID)

            assertEquals(
                DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN,
                viewModel.surfaceUnavailableEvents.first()
            )
            assertEquals(1, preparation.prepareCalls)
            assertEquals(UNKNOWN_DOSING_PUMP_COUNT, viewModel.uiState.value.pumpCount)
            assertTrue(viewModel.uiState.value.showBlockingPreparation)
            assertFalse(viewModel.uiState.value.contentEnabled)
        }

    @Test
    fun `restored destination enables content only after central preparation succeeds`() =
        runTest(dispatcher) {
            val preparation = FakePreparationOperations(
                DeviceControlSurfacePreparationResult.Ready
            )
            val viewModel = viewModel(
                rootOperations = FakeRootOperations(validRootSnapshot()),
                channelOperations = FakeChannelOperations(authoritativeChannels()),
                preparationOperations = preparation
            )

            viewModel.bind(DEVICE_UID)

            assertEquals(1, preparation.prepareCalls)
            assertEquals(CHANNEL_COUNT, viewModel.uiState.value.pumpCount)
            assertTrue(viewModel.uiState.value.contentEnabled)
            assertEquals(
                DeviceConnectionVisualState.ONLINE,
                viewModel.uiState.value.connectionVisualState
            )
            assertFalse(viewModel.uiState.value.showBlockingPreparation)
        }

    @Test
    fun `ready preparation without authoritative channels still rejects the surface`() =
        runTest(dispatcher) {
            val viewModel = viewModel(
                rootOperations = FakeRootOperations(validRootSnapshot()),
                channelOperations = FakeChannelOperations(emptyList()),
                preparationOperations = FakePreparationOperations(
                    DeviceControlSurfacePreparationResult.Ready
                )
            )

            viewModel.bind(DEVICE_UID)

            assertEquals(
                DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN,
                viewModel.surfaceUnavailableEvents.first()
            )
            assertFalse(viewModel.uiState.value.contentEnabled)
            assertEquals(
                DeviceConnectionVisualState.OFFLINE,
                viewModel.uiState.value.connectionVisualState
            )
        }

    private fun viewModel(
        rootOperations: DeviceRootOperations,
        channelOperations: DeviceDosingChannelOperations,
        preparationOperations: DeviceControlSurfacePreparationOperations
    ) = DeviceDosingRootViewModel(
        operations = rootOperations,
        channelNavigationOperations = UnavailableDeviceDosingChannelNavigationOperations,
        channelOperations = channelOperations,
        controlSurfacePreparationOperations = preparationOperations
    )

    private class FakePreparationOperations(
        private val result: DeviceControlSurfacePreparationResult
    ) : DeviceControlSurfacePreparationOperations {
        var prepareCalls = 0
        private var fresh = false

        override suspend fun prepare(
            request: DeviceControlSurfacePreparationRequest
        ): DeviceControlSurfacePreparationResult {
            prepareCalls += 1
            if (result == DeviceControlSurfacePreparationResult.Ready) fresh = true
            return result
        }

        override fun consumeFreshPreparation(
            deviceUid: String,
            family: OwnerDeviceFamily
        ): Boolean = fresh.also { fresh = false }
    }

    private class FakeRootOperations(
        snapshot: DeviceRootSnapshot?
    ) : DeviceRootOperations {
        private val snapshots = MutableStateFlow(snapshot)

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = snapshots

        override fun current(deviceUid: String): DeviceRootSnapshot? = snapshots.value

        override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeChannelOperations(
        values: List<DeviceDosingChannelSnapshot>
    ) : DeviceDosingChannelOperations by UnavailableDeviceDosingChannelOperations {
        private val snapshots = MutableStateFlow(values)

        override fun observeAll(deviceUid: String): Flow<List<DeviceDosingChannelSnapshot>> =
            snapshots

        override fun current(deviceUid: String, slotId: String): DeviceDosingChannelSnapshot? =
            snapshots.value.singleOrNull { snapshot ->
                snapshot.deviceUid == deviceUid && snapshot.slotId == slotId
            }
    }

    private companion object {
        const val DEVICE_UID = "device-1"
        const val CHANNEL_COUNT = 2
    }
}

private fun validRootSnapshot() = DeviceRootSnapshot(
    deviceUid = "device-1",
    title = "Dose Pro 2",
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
    ),
    allowedRoutes = setOf(
        DeviceRootRoute.DOSING_CHANNELS,
        DeviceRootRoute.DOSING_CALIBRATION
    )
)

private fun authoritativeChannels() = List(2) { index ->
    sampleDosingChannelSnapshot().copy(
        slotId = "dosing:channel${index + 1}",
        channelNumber = index + 1,
        channelTitle = "Channel ${index + 1}"
    )
}
