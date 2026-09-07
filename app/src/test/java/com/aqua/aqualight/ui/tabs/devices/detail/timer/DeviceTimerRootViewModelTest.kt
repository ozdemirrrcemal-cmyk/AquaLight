@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.timer

import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelSnapshot
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlCapabilities
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlFailure
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlOperations
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlResult
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlSnapshot
import com.aqua.aqualight.application.devices.timer.DeviceTimerDisplayNameUpdate
import com.aqua.aqualight.application.devices.timer.DeviceTimerNextTransitionType
import com.aqua.aqualight.application.devices.timer.DeviceTimerOperatingState
import com.aqua.aqualight.application.devices.timer.DeviceTimerOutputHealth
import com.aqua.aqualight.application.devices.timer.DeviceTimerRuntimeReason
import com.aqua.aqualight.application.devices.timer.DeviceTimerScheduleDraft
import com.aqua.aqualight.application.devices.timer.DeviceTimerScheduleSnapshot
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
            assertEquals(7L, state.control?.revision)
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
            assertEquals(7L, viewModel.uiState.value.control?.revision)
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
        controlSurfacePreparationOperations = preparation
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
        ): DeviceTimerControlResult = results.value

        override suspend fun setTemporaryOverride(
            deviceUid: String,
            slotId: String,
            regime: DeviceTimerChannelRegime,
            durationMillis: Long
        ): DeviceTimerControlResult = results.value

        override suspend fun setDisplayName(
            deviceUid: String,
            slotId: String,
            update: DeviceTimerDisplayNameUpdate
        ): DeviceTimerControlResult = results.value

        override suspend fun replaceSchedules(
            deviceUid: String,
            slotId: String,
            schedules: List<DeviceTimerScheduleDraft>
        ): DeviceTimerControlResult = results.value

        fun publish(result: DeviceTimerControlResult) {
            results.value = result
        }
    }

    private companion object {
        const val DEVICE_UID = "timer-pro-1"
    }
}

private fun timerRoot() = DeviceRootSnapshot(
    deviceUid = "timer-pro-1",
    title = "Timer Pro",
    availability = OwnerDeviceAvailability.REACHABLE,
    family = OwnerDeviceFamily.TIMER,
    catalogState = DeviceRootCatalogState.VALID,
    timerChannelCount = 1
)

private fun availableControl(): DeviceTimerControlResult = DeviceTimerControlResult.Available(
    DeviceTimerControlSnapshot(
        deviceUid = "timer-pro-1",
        revision = 7L,
        lockLoop = false,
        uptimeMillis = 50_000L,
        maxSchedulesPerChannel = 8,
        capabilities = DeviceTimerControlCapabilities(
            readOnly = false,
            supportsConfigApply = true,
            supportsChannelState = true,
            supportsSchedules = true,
            supportsSpansMidnight = true,
            supportsTemporaryOverride = true,
            supportsChannelDisplayName = true
        ),
        channels = listOf(
            DeviceTimerChannelSnapshot(
                slotId = "timer:timer1",
                channelNumber = 1,
                defaultName = "Timer 1",
                displayName = "Display Timer",
                regime = DeviceTimerChannelRegime.AUTO,
                operatingState = DeviceTimerOperatingState.ON,
                scheduleCount = 1,
                activeScheduleSlotId = 10,
                activeScheduleName = "Morning",
                nextTransitionType = DeviceTimerNextTransitionType.OFF,
                nextTransitionAtEpochMillis = 60_000L,
                runtimeReason = DeviceTimerRuntimeReason.SCHEDULE_ACTIVE,
                clockReady = true,
                temporaryOverrideActive = false,
                temporaryOverrideRemainingMillis = 0L,
                outputHealth = DeviceTimerOutputHealth.UNVERIFIED,
                physicalFeedbackAvailable = false,
                displayNameEditable = true,
                schedules = listOf(
                    DeviceTimerScheduleSnapshot(
                        index = 0,
                        slotId = 10,
                        enabled = true,
                        name = "Morning",
                        weekdays = listOf(true, true, true, true, true, false, false),
                        startTimeMillis = 28_800_000L,
                        endTimeMillis = 36_000_000L,
                        spansMidnight = false
                    )
                )
            )
        )
    )
)

private fun unavailableControl(): DeviceTimerControlResult =
    DeviceTimerControlResult.Failed(DeviceTimerControlFailure.UNAVAILABLE)
