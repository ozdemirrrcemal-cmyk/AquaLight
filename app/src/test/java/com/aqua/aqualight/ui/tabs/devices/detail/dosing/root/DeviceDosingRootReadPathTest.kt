package com.aqua.aqualight.ui.tabs.devices.detail.dosing.root

import com.aqua.aqualight.application.devices.DeviceChannelSlots
import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.DeviceSlotIndex
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.dosing.DeviceDosingActiveRun
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelControls
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingDailyUsageSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRunSource
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRuntimeReason
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import com.aqua.aqualight.data.devices.dosing.UnavailableDeviceDosingChannelNavigationOperations
import com.aqua.aqualight.data.devices.dosing.UnavailableDeviceDosingChannelOperations
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.pump.DosingPumpVisualState
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDosingRootReadPathTest {

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
    fun `two channel root switches atomically to authoritative cards and reconnect bootstrap`() =
        runTest(dispatcher) {
            val channels = FakeChannelOperations()
            val viewModel = viewModel(channelCount = 2, channelOperations = channels)

            viewModel.bind(DEVICE_UID, "Fallback")

            assertEquals(listOf("Catalog 1", "Catalog 2"), channelNames(viewModel))
            assertEquals(
                listOf(DosingPumpVisualState.IDLE, DosingPumpVisualState.IDLE),
                viewModel.uiState.value.pumpStates
            )

            channels.emit(
                listOf(
                    channelSnapshot(channelNumber = 2, pumpCount = 2),
                    channelSnapshot(channelNumber = 1, pumpCount = 2, active = true)
                )
            )

            val authoritative = viewModel.uiState.value
            assertEquals(listOf("Authoritative 1", "Authoritative 2"), channelNames(viewModel))
            assertEquals(
                listOf(1, 2),
                authoritative.channels.map { channel -> channel.channelNumber }
            )
            assertEquals(DosingPumpVisualState.RUNNING, authoritative.pumpStates.first())
            assertEquals(2.0, authoritative.channels.first().programProgress.dailyDoseMl, 0.0)
            assertEquals(
                1.0,
                authoritative.channels.first().programProgress.scheduledDeliveredTodayMl,
                0.0
            )
            assertEquals(
                0.5,
                authoritative.channels.first().programProgress.manualDeliveredTodayMl,
                0.0
            )
            assertEquals(50.0, authoritative.channels.first().reservoir?.remainingMl ?: 0.0, 0.0)

            channels.emit(emptyList())

            assertEquals(listOf("Catalog 1", "Catalog 2"), channelNames(viewModel))
            assertEquals(
                List(2) { DosingPumpVisualState.IDLE },
                viewModel.uiState.value.pumpStates
            )
        }

    @Test
    fun `four channel root orders a complete authoritative set by physical channel`() =
        runTest(dispatcher) {
            val channels = FakeChannelOperations()
            val viewModel = viewModel(channelCount = 4, channelOperations = channels)
            viewModel.bind(DEVICE_UID, "Fallback")

            channels.emit(
                (4 downTo 1).map { channelNumber ->
                    channelSnapshot(channelNumber = channelNumber, pumpCount = 4)
                }
            )

            assertEquals(4, viewModel.uiState.value.pumpCount)
            assertEquals("4", viewModel.uiState.value.primaryCountText)
            assertEquals(
                listOf("Authoritative 1", "Authoritative 2", "Authoritative 3", "Authoritative 4"),
                channelNames(viewModel)
            )
        }

    @Test
    fun `partial foreign or duplicate state cannot replace catalog bootstrap`() =
        runTest(dispatcher) {
            val channels = FakeChannelOperations()
            val viewModel = viewModel(channelCount = 2, channelOperations = channels)
            viewModel.bind(DEVICE_UID, "Fallback")

            channels.emit(listOf(channelSnapshot(channelNumber = 1, pumpCount = 2)))
            assertEquals(listOf("Catalog 1", "Catalog 2"), channelNames(viewModel))

            channels.emit(
                listOf(
                    channelSnapshot(channelNumber = 1, pumpCount = 2),
                    channelSnapshot(channelNumber = 2, pumpCount = 2).copy(deviceUid = "other")
                )
            )
            assertEquals(listOf("Catalog 1", "Catalog 2"), channelNames(viewModel))

            val duplicate = channelSnapshot(channelNumber = 1, pumpCount = 2)
            channels.emit(listOf(duplicate, duplicate))
            assertEquals(listOf("Catalog 1", "Catalog 2"), channelNames(viewModel))
            assertTrue(channels.refreshAllCalled)
            assertFalse(viewModel.uiState.value.channels.isEmpty())
        }

    private fun viewModel(
        channelCount: Int,
        channelOperations: DeviceDosingChannelOperations
    ) = DeviceDosingRootViewModel(
        operations = FakeRootOperations(rootSnapshot(channelCount)),
        channelNavigationOperations = UnavailableDeviceDosingChannelNavigationOperations,
        channelOperations = channelOperations
    )

    private fun channelNames(viewModel: DeviceDosingRootViewModel): List<String> =
        viewModel.uiState.value.channels.map { channel -> channel.displayName }

    private class FakeRootOperations(
        private val snapshot: DeviceRootSnapshot
    ) : DeviceRootOperations {
        private val snapshots = MutableStateFlow<DeviceRootSnapshot?>(snapshot)

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = snapshots

        override fun current(deviceUid: String): DeviceRootSnapshot = snapshot

        override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeChannelOperations :
        DeviceDosingChannelOperations by UnavailableDeviceDosingChannelOperations {
        private val snapshots = MutableStateFlow<List<DeviceDosingChannelSnapshot>>(emptyList())
        var refreshAllCalled: Boolean = false

        override fun observeAll(deviceUid: String): Flow<List<DeviceDosingChannelSnapshot>> =
            snapshots

        override suspend fun refreshAll(deviceUid: String): Boolean {
            refreshAllCalled = true
            return true
        }

        fun emit(value: List<DeviceDosingChannelSnapshot>) {
            snapshots.value = value
        }
    }

    private companion object {
        const val DEVICE_UID = "device-1"
    }
}

private fun rootSnapshot(channelCount: Int) = DeviceRootSnapshot(
    deviceUid = "device-1",
    title = "Dose Pro",
    availability = OwnerDeviceAvailability.REACHABLE,
    family = OwnerDeviceFamily.DOSING,
    catalogState = DeviceRootCatalogState.VALID,
    channelSlots = DeviceChannelSlots(
        lightChannels = emptyList(),
        timerChannels = emptyList(),
        dosingChannels = List(channelCount) { index ->
            DeviceDosingChannelSlot(
                index = DeviceSlotIndex(index),
                wireKey = DeviceChannelWireKey("channel${index + 1}"),
                defaultDisplayName = "Catalog ${index + 1}",
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

private fun channelSnapshot(
    channelNumber: Int,
    pumpCount: Int,
    active: Boolean = false
) = DeviceDosingChannelSnapshot(
    deviceUid = "device-1",
    slotId = "dosing:channel$channelNumber",
    pumpCount = pumpCount,
    channelNumber = channelNumber,
    channelTitle = "Authoritative $channelNumber",
    revision = 7L,
    runtimeEnabled = true,
    runtimeReason = if (active) DeviceDosingRuntimeReason.BUSY else DeviceDosingRuntimeReason.NONE,
    deliveryAccountingCertain = true,
    calibrated = true,
    lastCalibratedAtEpochSeconds = 1_786_320_000L,
    scheduling = DeviceDosingSchedulingPolicy(),
    program = DeviceDosingProgram(
        enabled = true,
        weekdays = List(7) { true },
        schedule = DeviceDosingProgramSchedule.Single(
            dailyDoseMicroliters = 2_000L,
            startTimeMillis = 36_000_000L
        ),
        missedDoseRecoveryEnabled = false
    ),
    progress = DeviceDosingChannelProgress(
        scheduledAmountMicroliters = 2_000L,
        completedAmountMicroliters = 1_000L,
        executionCurrent = true
    ),
    reservoir = DeviceDosingReservoirSnapshot(
        trackingEnabled = true,
        capacityMicroliters = 100_000L,
        remainingMicroliters = 50_000L
    ),
    activeRun = DeviceDosingActiveRun(
        active = active,
        source = if (active) DeviceDosingRunSource.MANUAL else DeviceDosingRunSource.NONE,
        targetAmountMicroliters = if (active) 1_000L else 0L,
        remainingMillis = if (active) 1_000L else 0L
    ),
    controls = DeviceDosingChannelControls(),
    usageToday = DeviceDosingDailyUsageSnapshot(
        valid = true,
        scheduledDeliveredMicroliters = 1_000L,
        manualDeliveredMicroliters = 500L,
        totalDeliveredMicroliters = 1_500L
    )
)
