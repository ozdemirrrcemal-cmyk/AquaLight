package com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.root

import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerChannelIdentity
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerChannelRuntime
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerChannelSnapshot
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerChannelState
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerChannelTransition
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerCommandFailure
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlCapabilities
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlFailure
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlOperations
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlResult
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlSnapshot
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlAuthority
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerDisplayNameUpdate
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerMutationCapabilities
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerNextTransitionType
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerOperatingState
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerOutputHealth
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerRuntimeReason
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerScheduleDraft
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerScheduleIdentity
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerScheduleSnapshot
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerScheduleWindow
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceTimerRootViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fresh handoff consumes authoritative application snapshot without another preparation`() =
        runTest {
            val controls = FakeTimerControlOperations(availableControl())
            val preparation = FakePreparationOperations(fresh = true)
            val viewModel = viewModel(controls, preparation)

            viewModel.bind(DEVICE_UID)

            val state = viewModel.uiState.value
            assertEquals(0, preparation.prepareCalls)
            assertEquals(1, preparation.consumeCalls)
            assertTrue(state.contentEnabled)
            assertFalse(state.showBlockingPreparation)
            assertEquals(DeviceConnectionVisualState.ONLINE, state.connectionVisualState)
            assertEquals(TEST_REVISION, state.control?.revision)
            assertEquals("Morning", state.control?.channels?.single()?.schedules?.single()?.name)
        }

    @Test
    fun `restored surface prepares again and enables only the refreshed application snapshot`() =
        runTest {
            val controls = FakeTimerControlOperations(unavailableControl())
            val preparation = FakePreparationOperations(
                onPrepare = { controls.publish(availableControl()) }
            )
            val viewModel = viewModel(controls, preparation)

            viewModel.bind(DEVICE_UID)

            assertEquals(1, preparation.prepareCalls)
            assertEquals(2, preparation.consumeCalls)
            assertTrue(viewModel.uiState.value.contentEnabled)
            assertFalse(viewModel.uiState.value.showBlockingPreparation)
            assertNotNull(viewModel.uiState.value.control)
        }

    @Test
    fun `unavailable restored preparation emits exit reason and remains fail closed`() = runTest {
        val controls = FakeTimerControlOperations(unavailableControl())
        val reason = DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
        val preparation = FakePreparationOperations(
            result = DeviceControlSurfacePreparationResult.Unavailable(reason)
        )
        val viewModel = viewModel(controls, preparation)

        viewModel.bind(DEVICE_UID)

        assertEquals(reason, viewModel.surfaceUnavailableEvents.first())
        assertFalse(viewModel.uiState.value.contentEnabled)
        assertFalse(viewModel.uiState.value.showBlockingPreparation)
        assertEquals(DeviceConnectionVisualState.OFFLINE, viewModel.uiState.value.connectionVisualState)
    }

    @Test
    fun `loss of central Timer authority disables content but retains presentation continuity`() =
        runTest {
            val controls = FakeTimerControlOperations(availableControl())
            val viewModel = viewModel(controls, FakePreparationOperations(fresh = true))
            viewModel.bind(DEVICE_UID)
            assertTrue(viewModel.uiState.value.contentEnabled)

            controls.publish(unavailableControl())

            assertFalse(viewModel.uiState.value.contentEnabled)
            assertEquals(DeviceConnectionVisualState.OFFLINE, viewModel.uiState.value.connectionVisualState)
            assertEquals(TEST_REVISION, viewModel.uiState.value.control?.revision)
        }

    @Test
    fun `closed command failure is retained without replacing the last presentation`() = runTest {
        val controls = FakeTimerControlOperations(availableControl())
        val viewModel = viewModel(controls, FakePreparationOperations(fresh = true))
        viewModel.bind(DEVICE_UID)

        val failure = DeviceTimerControlFailure.Rejected(DeviceTimerCommandFailure.CONFLICT)
        controls.publish(DeviceTimerControlResult.Failed(failure))

        assertEquals(failure, viewModel.uiState.value.controlFailure)
        assertEquals(TEST_REVISION, viewModel.uiState.value.control?.revision)
        assertFalse(viewModel.uiState.value.contentEnabled)
    }

    @Test
    fun `clock not ready becomes a derived commercial status notice`() = runTest {
        val controls = FakeTimerControlOperations(availableControl(clockReady = false))
        val viewModel = viewModel(controls, FakePreparationOperations(fresh = true))

        viewModel.bind(DEVICE_UID)

        val state = viewModel.uiState.value
        assertTrue(state.contentEnabled)
        assertEquals(
            setOf(DeviceTimerStatusNotice.CLOCK_UNAVAILABLE),
            state.control?.statusNotices
        )
        assertEquals(null, state.controlFailure)
    }

    @Test
    fun `root summary derives active outputs from authoritative channel state`() = runTest {
        val controls = FakeTimerControlOperations(availableControl())
        val viewModel = viewModel(controls, FakePreparationOperations(fresh = true))

        viewModel.bind(DEVICE_UID)

        assertEquals(1, viewModel.uiState.value.control?.activeChannelCount)
    }

    @Test
    fun `dashboard power keeps program mode and overrides only until the next transition`() =
        runTest {
            val controls = FakeTimerControlOperations(availableControl())
            val viewModel = viewModel(controls, FakePreparationOperations(fresh = true))
            viewModel.bind(DEVICE_UID)

            viewModel.togglePower(CHANNEL_SLOT_ID)

            assertEquals(
                listOf(
                    TemporaryOverrideCall(
                        DEVICE_UID,
                        CHANNEL_SLOT_ID,
                        DeviceTimerChannelRegime.OFF,
                        FIVE_MINUTES_MILLIS
                    )
                ),
                controls.temporaryOverrideCalls
            )
            assertTrue(controls.regimeCalls.isEmpty())
            assertEquals(
                DeviceTimerChannelRegime.AUTO,
                viewModel.uiState.value.control?.channels?.single()?.regime
            )
        }

    @Test
    fun `dashboard power in manual mode persists the opposite output state`() = runTest {
        val controls = FakeTimerControlOperations(
            availableControl(regime = DeviceTimerChannelRegime.ON)
        )
        val viewModel = viewModel(controls, FakePreparationOperations(fresh = true))
        viewModel.bind(DEVICE_UID)

        viewModel.togglePower(CHANNEL_SLOT_ID)

        assertEquals(
            listOf(RegimeCall(DEVICE_UID, CHANNEL_SLOT_ID, DeviceTimerChannelRegime.OFF)),
            controls.regimeCalls
        )
        assertTrue(controls.temporaryOverrideCalls.isEmpty())
    }

    @Test
    fun `dashboard power can cancel a temporary override using the existing persistent regime`() =
        runTest {
            val controls = FakeTimerControlOperations(
                availableControl(
                    regime = DeviceTimerChannelRegime.OFF,
                    operatingState = DeviceTimerOperatingState.ON,
                    temporaryOverrideActive = true
                )
            )
            val viewModel = viewModel(controls, FakePreparationOperations(fresh = true))
            viewModel.bind(DEVICE_UID)

            viewModel.togglePower(CHANNEL_SLOT_ID)

            assertEquals(
                listOf(RegimeCall(DEVICE_UID, CHANNEL_SLOT_ID, DeviceTimerChannelRegime.OFF)),
                controls.regimeCalls
            )
        }

    @Test
    fun `rejected channel mutation retains authoritative content and reports reason`() = runTest {
        val failure = DeviceTimerControlFailure.Rejected(DeviceTimerCommandFailure.CONFLICT)
        val controls = FakeTimerControlOperations(
            availableControl(regime = DeviceTimerChannelRegime.ON)
        ).apply {
            regimeResult = DeviceTimerControlResult.Failed(failure)
        }
        val viewModel = viewModel(controls, FakePreparationOperations(fresh = true))
        viewModel.bind(DEVICE_UID)

        viewModel.togglePower(CHANNEL_SLOT_ID)

        assertTrue(viewModel.uiState.value.contentEnabled)
        assertEquals(failure, viewModel.uiState.value.controlFailure)
        assertEquals(TEST_REVISION, viewModel.uiState.value.control?.revision)
    }

    @Test
    fun `read only Timer never sends a channel mutation`() = runTest {
        val controls = FakeTimerControlOperations(availableControl(readOnly = true))
        val viewModel = viewModel(controls, FakePreparationOperations(fresh = true))
        viewModel.bind(DEVICE_UID)

        viewModel.togglePower(CHANNEL_SLOT_ID)

        assertTrue(controls.regimeCalls.isEmpty())
        assertTrue(controls.temporaryOverrideCalls.isEmpty())
    }

    @Test
    fun `root availability remains a fail closed interaction gate`() = runTest {
        val roots = FakeRootOperations(timerRoot())
        val controls = FakeTimerControlOperations(availableControl())
        val viewModel = DeviceTimerRootViewModel(
            operations = roots,
            timerControlOperations = controls,
            controlSurfacePreparationOperations = FakePreparationOperations(fresh = true)
        )
        viewModel.bind(DEVICE_UID)
        assertTrue(viewModel.uiState.value.contentEnabled)

        roots.publish(timerRoot().copy(availability = OwnerDeviceAvailability.UNREACHABLE))

        assertFalse(viewModel.uiState.value.contentEnabled)
        assertEquals(DeviceConnectionVisualState.OFFLINE, viewModel.uiState.value.connectionVisualState)
    }

    private fun viewModel(
        controls: DeviceTimerControlOperations,
        preparation: DeviceControlSurfacePreparationOperations
    ) = DeviceTimerRootViewModel(
        operations = FakeRootOperations(timerRoot()),
        timerControlOperations = controls,
        controlSurfacePreparationOperations = preparation,
        currentEpochMillis = { TEST_NOW_EPOCH_MILLIS }
    )

    private class FakeRootOperations(
        initial: DeviceRootSnapshot
    ) : DeviceRootOperations {
        private val snapshots = MutableStateFlow<DeviceRootSnapshot?>(initial)

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = snapshots

        override fun current(deviceUid: String): DeviceRootSnapshot? = snapshots.value

        override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)

        fun publish(snapshot: DeviceRootSnapshot) {
            snapshots.value = snapshot
        }
    }

    private class FakePreparationOperations(
        private var fresh: Boolean = false,
        private val result: DeviceControlSurfacePreparationResult =
            DeviceControlSurfacePreparationResult.Ready,
        private val onPrepare: suspend () -> Unit = {}
    ) : DeviceControlSurfacePreparationOperations {
        var prepareCalls = 0
        var consumeCalls = 0

        override suspend fun prepare(
            request: DeviceControlSurfacePreparationRequest
        ): DeviceControlSurfacePreparationResult {
            prepareCalls += 1
            onPrepare()
            fresh = result == DeviceControlSurfacePreparationResult.Ready
            return result
        }

        override fun consumeFreshPreparation(
            deviceUid: String,
            family: OwnerDeviceFamily
        ): Boolean {
            consumeCalls += 1
            val consumed = fresh && family == OwnerDeviceFamily.TIMER
            fresh = false
            return consumed
        }
    }

    private class FakeTimerControlOperations(
        initial: DeviceTimerControlResult
    ) : DeviceTimerControlOperations {
        private val results = MutableStateFlow(initial)
        val regimeCalls = mutableListOf<RegimeCall>()
        val temporaryOverrideCalls = mutableListOf<TemporaryOverrideCall>()
        var regimeResult: DeviceTimerControlResult? = null

        override fun observeControl(deviceUid: String): Flow<DeviceTimerControlResult> = results

        override fun currentControl(deviceUid: String): DeviceTimerControlResult = results.value

        override suspend fun refreshControl(deviceUid: String): DeviceTimerControlResult =
            results.value

        override suspend fun refreshChannel(
            deviceUid: String,
            slotId: String
        ): DeviceTimerControlResult = results.value

        override suspend fun setRegime(
            deviceUid: String,
            slotId: String,
            regime: DeviceTimerChannelRegime
        ): DeviceTimerControlResult {
            regimeCalls += RegimeCall(deviceUid, slotId, regime)
            return regimeResult ?: results.value
        }

        override suspend fun setTemporaryOverride(
            deviceUid: String,
            slotId: String,
            regime: DeviceTimerChannelRegime,
            durationMillis: Long
        ): DeviceTimerControlResult {
            temporaryOverrideCalls += TemporaryOverrideCall(
                deviceUid,
                slotId,
                regime,
                durationMillis
            )
            return results.value
        }

        override suspend fun setDisplayName(
            deviceUid: String,
            slotId: String,
            update: DeviceTimerDisplayNameUpdate
        ): DeviceTimerControlResult = results.value

        override suspend fun replaceSchedules(
            deviceUid: String,
            slotId: String,
            expectedRevision: Long,
            schedules: List<DeviceTimerScheduleDraft>
        ): DeviceTimerControlResult = results.value

        fun publish(result: DeviceTimerControlResult) {
            results.value = result
        }
    }

    private companion object {
        const val DEVICE_UID = "timer-pro-1"
        const val CHANNEL_SLOT_ID = "timer:timer1"
        const val TEST_NOW_EPOCH_MILLIS = 1_800_000_000_000L
        const val FIVE_MINUTES_MILLIS = 300_000L
    }
}

private data class RegimeCall(
    val deviceUid: String,
    val slotId: String,
    val regime: DeviceTimerChannelRegime
)

private data class TemporaryOverrideCall(
    val deviceUid: String,
    val slotId: String,
    val regime: DeviceTimerChannelRegime,
    val durationMillis: Long
)

private fun timerRoot() = DeviceRootSnapshot(
    deviceUid = "timer-pro-1",
    title = "Timer Pro",
    availability = OwnerDeviceAvailability.REACHABLE,
    family = OwnerDeviceFamily.TIMER,
    catalogState = DeviceRootCatalogState.VALID,
    timerChannelCount = 1
)

private fun availableControl(
    clockReady: Boolean = true,
    readOnly: Boolean = false,
    regime: DeviceTimerChannelRegime = DeviceTimerChannelRegime.AUTO,
    operatingState: DeviceTimerOperatingState = DeviceTimerOperatingState.ON,
    temporaryOverrideActive: Boolean = false
): DeviceTimerControlResult = DeviceTimerControlResult.Available(
    DeviceTimerControlSnapshot(
        deviceUid = "timer-pro-1",
        authority = DeviceTimerControlAuthority(
            revision = TEST_REVISION,
            lockLoop = false,
            uptimeMillis = TEST_UPTIME_MILLIS,
            maxSchedulesPerChannel = TEST_MAX_SCHEDULES
        ),
        capabilities = DeviceTimerControlCapabilities(
            readOnly = readOnly,
            mutations = DeviceTimerMutationCapabilities(
                supportsConfigApply = true,
                supportsChannelState = true,
                supportsSchedules = true,
                supportsSpansMidnight = true,
                supportsTemporaryOverride = true,
                supportsChannelDisplayName = true
            )
        ),
        channels = listOf(
            DeviceTimerChannelSnapshot(
                identity = DeviceTimerChannelIdentity(
                    slotId = "timer:timer1",
                    channelNumber = 1,
                    defaultName = "Timer 1",
                    displayName = "Display Timer",
                    displayNameEditable = true
                ),
                state = DeviceTimerChannelState(
                    regime = regime,
                    operatingState = operatingState,
                    scheduleCount = 1,
                    outputHealth = DeviceTimerOutputHealth.UNVERIFIED
                ),
                transition = DeviceTimerChannelTransition(
                    activeScheduleSlotId = MORNING_SCHEDULE_SLOT_ID,
                    activeScheduleName = "Morning",
                    nextTransitionType = DeviceTimerNextTransitionType.OFF,
                    nextTransitionAtEpochMillis = TEST_NEXT_TRANSITION_EPOCH_MILLIS
                ),
                runtime = DeviceTimerChannelRuntime(
                    reason = if (clockReady) {
                        DeviceTimerRuntimeReason.SCHEDULE_ACTIVE
                    } else {
                        DeviceTimerRuntimeReason.CLOCK_UNAVAILABLE
                    },
                    clockReady = clockReady,
                    temporaryOverrideActive = temporaryOverrideActive,
                    temporaryOverrideRemainingMillis = if (temporaryOverrideActive) {
                        ACTIVE_OVERRIDE_REMAINING_MILLIS
                    } else {
                        0L
                    },
                    physicalFeedbackAvailable = false
                ),
                schedules = listOf(morningSchedule())
            )
        )
    )
)

private fun morningSchedule() = DeviceTimerScheduleSnapshot(
    identity = DeviceTimerScheduleIdentity(
        index = 0,
        slotId = MORNING_SCHEDULE_SLOT_ID,
        enabled = true,
        name = "Morning"
    ),
    window = DeviceTimerScheduleWindow(
        weekdays = listOf(true, true, true, true, true, false, false),
        startTimeMillis = MORNING_START_MILLIS,
        endTimeMillis = MORNING_END_MILLIS,
        spansMidnight = false
    )
)

private fun unavailableControl(): DeviceTimerControlResult =
    DeviceTimerControlResult.Failed(DeviceTimerControlFailure.Unavailable)

private const val TEST_REVISION = 7L
private const val TEST_UPTIME_MILLIS = 50_000L
private const val TEST_MAX_SCHEDULES = 8
private const val MORNING_SCHEDULE_SLOT_ID = 10
private const val TEST_NEXT_TRANSITION_EPOCH_MILLIS = 1_800_000_300_000L
private const val ACTIVE_OVERRIDE_REMAINING_MILLIS = 1_800_000L
private const val MORNING_START_MILLIS = 28_800_000L
private const val MORNING_END_MILLIS = 36_000_000L
