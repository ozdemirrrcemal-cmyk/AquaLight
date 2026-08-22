package com.aqua.aqualight.ui.tabs.devices.detail.dosing.root

import com.aqua.aqualight.application.devices.DeviceChannelSlots
import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `prepared two channel root renders authoritative cards on its first frame`() =
        runTest(dispatcher) {
            val channels = FakeChannelOperations(
                initialSnapshots = listOf(
                    channelSnapshot(channelNumber = 2, pumpCount = 2),
                    channelSnapshot(channelNumber = 1, pumpCount = 2, active = true)
                )
            )
            val viewModel = viewModel(channelCount = 2, channelOperations = channels)

            viewModel.bind(
                deviceUidText = DEVICE_UID,
                fallbackTitle = "Fallback",
                presentationPrepared = true
            )

            val authoritative = viewModel.uiState.value
            assertEquals("Dose Pro", authoritative.title)
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
        }

    @Test
    fun `reconnect clears authoritative cards to topology-only bootstrap`() = runTest(dispatcher) {
        val channels = FakeChannelOperations(
            initialSnapshots = listOf(
                channelSnapshot(channelNumber = 1, pumpCount = 2, active = true),
                channelSnapshot(channelNumber = 2, pumpCount = 2)
            )
        )
        val viewModel = viewModel(channelCount = 2, channelOperations = channels)
        viewModel.bind(
            deviceUidText = DEVICE_UID,
            fallbackTitle = "Fallback",
            presentationPrepared = true
        )

        channels.emit(emptyList())

        assertEquals(listOf("Catalog 1", "Catalog 2"), channelNames(viewModel))
        assertTrue(viewModel.uiState.value.pumpStates.isEmpty())
        viewModel.uiState.value.channels.forEach { channel ->
            assertNull(channel.visualState)
        }
    }

    @Test
    fun `four channel root orders a complete authoritative set by physical channel`() =
        runTest(dispatcher) {
            val channels = FakeChannelOperations(
                initialSnapshots = (4 downTo 1).map { channelNumber ->
                    channelSnapshot(channelNumber = channelNumber, pumpCount = 4)
                }
            )
            val viewModel = viewModel(channelCount = 4, channelOperations = channels)
            viewModel.bind(
                deviceUidText = DEVICE_UID,
                fallbackTitle = "Fallback",
                presentationPrepared = true
            )

            assertEquals(4, viewModel.uiState.value.pumpCount)
            assertEquals("4", viewModel.uiState.value.primaryCountText)
            assertEquals(
                listOf("Authoritative 1", "Authoritative 2", "Authoritative 3", "Authoritative 4"),
                channelNames(viewModel)
            )
        }

    @Test
    fun `transient metadata invalidation preserves last validated firmware cards`() =
        runTest(dispatcher) {
            val root = FakeRootOperations(rootSnapshot(channelCount = 4))
            val channels = FakeChannelOperations(
                initialSnapshots = (1..4).map { channelNumber ->
                    channelSnapshot(
                        channelNumber = channelNumber,
                        pumpCount = 4,
                        active = channelNumber == 1
                    )
                }
            )
            val viewModel = viewModel(root, channels)
            viewModel.bind(
                deviceUidText = DEVICE_UID,
                fallbackTitle = "Fallback",
                presentationPrepared = true
            )
            val validated = viewModel.uiState.value

            root.emit(invalidRootSnapshot())

            val reconnecting = viewModel.uiState.value
            assertEquals(4, reconnecting.pumpCount)
            assertEquals(validated.channels, reconnecting.channels)
            assertEquals(validated.pumpStates, reconnecting.pumpStates)
            assertEquals(
                listOf(
                    "Authoritative 1",
                    "Authoritative 2",
                    "Authoritative 3",
                    "Authoritative 4"
                ),
                channelNames(viewModel)
            )
        }

    @Test
    fun `invalid cold start cannot invent card topology from firmware snapshots`() =
        runTest(dispatcher) {
            val channels = FakeChannelOperations(
                initialSnapshots = (1..4).map { channelNumber ->
                    channelSnapshot(channelNumber = channelNumber, pumpCount = 4)
                }
            )
            val viewModel = viewModel(FakeRootOperations(invalidRootSnapshot()), channels)
            viewModel.bind(
                deviceUidText = DEVICE_UID,
                fallbackTitle = "Fallback",
                presentationPrepared = true
            )

            assertEquals(UNKNOWN_DOSING_PUMP_COUNT, viewModel.uiState.value.pumpCount)
            assertTrue(viewModel.uiState.value.channels.isEmpty())
            assertTrue(viewModel.uiState.value.pumpStates.isEmpty())
        }

    @Test
    fun `partial foreign or duplicate state cannot replace catalog bootstrap`() =
        runTest(dispatcher) {
            val channels = FakeChannelOperations(
                initialSnapshots = listOf(
                    channelSnapshot(channelNumber = 1, pumpCount = 2),
                    channelSnapshot(channelNumber = 2, pumpCount = 2)
                )
            )
            val viewModel = viewModel(channelCount = 2, channelOperations = channels)
            viewModel.bind(
                deviceUidText = DEVICE_UID,
                fallbackTitle = "Fallback",
                presentationPrepared = true
            )

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
            assertFalse(channels.refreshAllCalled)
            assertFalse(viewModel.uiState.value.channels.isEmpty())
        }

    @Test
    fun `unprepared entry never renders cached active run before fresh read`() =
        runTest(dispatcher) {
            val stale = listOf(
                channelSnapshot(channelNumber = 1, pumpCount = 2, active = true),
                channelSnapshot(channelNumber = 2, pumpCount = 2)
            )
            val fresh = stale.withoutRuntimeHistory()
            val channels = FakeChannelOperations(
                initialSnapshots = stale
            )
            val menuAccess = FakeMenuAccessOperations {
                channels.emit(fresh)
                DeviceMenuAccessResult.Available(
                    deviceUid = DEVICE_UID,
                    title = "Dose Pro",
                    family = OwnerDeviceFamily.DOSING,
                    presentationPrepared = true
                )
            }
            val viewModel = viewModel(
                rootOperations = FakeRootOperations(rootSnapshot(channelCount = 2)),
                channelOperations = channels,
                menuAccessOperations = menuAccess
            )
            val rendered = mutableListOf<DeviceDosingRootUiState>()
            val collection = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                viewModel.uiState.collect(rendered::add)
            }

            viewModel.bind(
                deviceUidText = DEVICE_UID,
                fallbackTitle = "Fallback",
                presentationPrepared = false
            )

            assertTrue(menuAccess.resolveCalled)
            assertFalse(channels.refreshAllCalled)
            assertFalse(viewModel.uiState.value.isPreparing)
            assertTrue(rendered.none { state ->
                DosingPumpVisualState.RUNNING in state.pumpStates
            })
            assertEquals(
                0.0,
                viewModel.uiState.value.channels.first()
                    .programProgress.scheduledDeliveredTodayMl,
                0.0
            )
            assertEquals(
                0.0,
                viewModel.uiState.value.channels.first()
                    .programProgress.manualDeliveredTodayMl,
                0.0
            )
            collection.cancel()
        }

    private fun viewModel(
        channelCount: Int,
        channelOperations: DeviceDosingChannelOperations
    ) = viewModel(FakeRootOperations(rootSnapshot(channelCount)), channelOperations)

    private fun viewModel(
        rootOperations: DeviceRootOperations,
        channelOperations: DeviceDosingChannelOperations,
        menuAccessOperations: DeviceMenuAccessOperations = FakeMenuAccessOperations {
            DeviceMenuAccessResult.Unavailable(
                title = "",
                reason = DeviceMenuUnavailableReason.CURRENT_DATA_NOT_READY
            )
        }
    ) = DeviceDosingRootViewModel(
        operations = rootOperations,
        channelNavigationOperations = UnavailableDeviceDosingChannelNavigationOperations,
        channelOperations = channelOperations,
        menuAccessOperations = menuAccessOperations
    )

    private fun channelNames(viewModel: DeviceDosingRootViewModel): List<String> =
        viewModel.uiState.value.channels.map { channel -> channel.displayName }

    private class FakeRootOperations(
        snapshot: DeviceRootSnapshot
    ) : DeviceRootOperations {
        private val snapshots = MutableStateFlow<DeviceRootSnapshot?>(snapshot)

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = snapshots

        override fun current(deviceUid: String): DeviceRootSnapshot? = snapshots.value

        override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)

        fun emit(value: DeviceRootSnapshot?) {
            snapshots.value = value
        }
    }

    private class FakeChannelOperations(
        initialSnapshots: List<DeviceDosingChannelSnapshot> = emptyList(),
        private val refreshSucceeds: Boolean = true
    ) :
        DeviceDosingChannelOperations by UnavailableDeviceDosingChannelOperations {
        private val snapshots = MutableStateFlow(initialSnapshots)
        var refreshAllCalled: Boolean = false

        override fun observeAll(deviceUid: String): Flow<List<DeviceDosingChannelSnapshot>> =
            snapshots

        override fun currentAll(deviceUid: String): List<DeviceDosingChannelSnapshot> =
            snapshots.value

        override suspend fun refreshAll(deviceUid: String): Boolean {
            refreshAllCalled = true
            return refreshSucceeds
        }

        fun emit(value: List<DeviceDosingChannelSnapshot>) {
            snapshots.value = value
        }
    }

    private class FakeMenuAccessOperations(
        private val result: () -> DeviceMenuAccessResult
    ) : DeviceMenuAccessOperations {
        var resolveCalled: Boolean = false

        override suspend fun resolve(deviceUid: String): DeviceMenuAccessResult {
            resolveCalled = true
            return result()
        }
    }

    private companion object {
        const val DEVICE_UID = "device-1"
    }
}

private fun List<DeviceDosingChannelSnapshot>.withoutRuntimeHistory() = map { snapshot ->
    snapshot.copy(
        runtimeReason = DeviceDosingRuntimeReason.NONE,
        activeRun = DeviceDosingActiveRun(),
        progress = snapshot.progress.copy(
            completedAmountMicroliters = 0L,
            remainingAmountMicroliters = snapshot.progress.scheduledAmountMicroliters,
            completionPercent = 0.0
        ),
        usageToday = DeviceDosingDailyUsageSnapshot()
    )
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

private fun invalidRootSnapshot() = DeviceRootSnapshot(
    deviceUid = "device-1",
    title = "Dose Pro",
    availability = OwnerDeviceAvailability.REACHABLE,
    catalogState = DeviceRootCatalogState.INVALID
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
