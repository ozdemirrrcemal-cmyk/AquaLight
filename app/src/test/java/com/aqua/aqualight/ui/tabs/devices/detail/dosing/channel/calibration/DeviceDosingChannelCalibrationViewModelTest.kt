@file:Suppress("LongMethod", "LongParameterList", "TooManyFunctions")

package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationSessionPhase
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.application.devices.DeviceDosingChannelDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceDosingChannelCalibrationViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `name and prime steps use application boundary with release safety`() = runTest(dispatcher) {
        val operations = FakeCalibrationOperations(snapshot())
        val viewModel = viewModel(operations)

        bind(viewModel)
        advanceUntilIdle()
        assertEquals(DeviceDosingCalibrationStep.NAME, viewModel.uiState.value.step)

        viewModel.updateDisplayName(" Trace Elements ")
        viewModel.saveDisplayNameAndContinue()
        advanceUntilIdle()
        assertEquals(DeviceDosingCalibrationStep.PRIME, viewModel.uiState.value.step)
        assertEquals("Trace Elements", operations.savedName)

        viewModel.primePressed()
        runCurrent()
        assertTrue(viewModel.uiState.value.isPumpActive)
        viewModel.primeReleased()
        advanceUntilIdle()

        assertEquals(1, operations.primeStarts)
        assertEquals(1, operations.primeStops)
        assertFalse(viewModel.uiState.value.isPumpActive)
    }

    @Test
    fun `completed verification resumes at confirmation and emits detail target`() =
        runTest(dispatcher) {
            val pending = snapshot(
                phase = DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION,
                verificationStarted = true,
                verificationComplete = true,
                pendingDoseMsPerMl = 1_250L
            )
            val operations = FakeCalibrationOperations(pending).apply {
                confirmResult = success(
                    snapshot(
                        phase = DeviceDosingCalibrationSessionPhase.IDLE,
                        calibrated = true,
                        pendingDoseMsPerMl = -1L
                    )
                )
            }
            val viewModel = viewModel(operations)

            bind(viewModel)
            advanceUntilIdle()
            assertEquals(
                DeviceDosingCalibrationStep.CONFIRMATION,
                viewModel.uiState.value.step
            )

            viewModel.acceptVerification()
            advanceUntilIdle()
            val event = viewModel.events.first() as DeviceDosingCalibrationEvent.Completed

            assertEquals(DeviceDosingChannelDestination.DETAIL, event.target.destination)
            assertEquals("channel-1", event.target.slotId)
            assertEquals(1, operations.confirms)
        }

    @Test
    fun `exiting an active verification stops dose then discards pending session`() =
        runTest(dispatcher) {
            val operations = FakeCalibrationOperations(
                snapshot(
                    phase = DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION,
                    verificationStarted = true,
                    verificationComplete = false,
                    verificationRemainingMs = 0L,
                    manualActive = true
                )
            )
            val viewModel = viewModel(operations)

            bind(viewModel)
            advanceUntilIdle()
            viewModel.requestExit()
            advanceUntilIdle()

            assertEquals(1, operations.verificationStops)
            assertEquals(1, operations.cancels)
            assertTrue(viewModel.events.first() is DeviceDosingCalibrationEvent.Exit)
        }

    private fun viewModel(operations: FakeCalibrationOperations) =
        DeviceDosingChannelCalibrationViewModel(
            operations = operations,
            clock = DeviceDosingCalibrationClock { dispatcher.scheduler.currentTime }
        )

    private fun bind(viewModel: DeviceDosingChannelCalibrationViewModel) {
        viewModel.bind(
            deviceUid = "device-1",
            slotId = "channel-1",
            pumpCount = 2,
            channelNumber = 1,
            channelTitle = "Channel 1"
        )
    }

    private class FakeCalibrationOperations(
        initial: DeviceDosingCalibrationSnapshot
    ) : DeviceDosingCalibrationOperations {
        private val state = MutableStateFlow<DeviceDosingCalibrationSnapshot?>(initial)
        var savedName = ""
        var primeStarts = 0
        var primeStops = 0
        var verificationStops = 0
        var confirms = 0
        var cancels = 0
        var confirmResult: DeviceDosingCalibrationResult = success(initial)

        override fun observe(
            deviceUid: String,
            slotId: String
        ): Flow<DeviceDosingCalibrationSnapshot?> = state

        override suspend fun refresh(deviceUid: String, slotId: String) = success(current())

        override suspend fun saveDisplayName(
            deviceUid: String,
            slotId: String,
            displayName: String
        ): DeviceDosingCalibrationResult {
            savedName = displayName.trim()
            return success(current().copy(channelTitle = savedName))
        }

        override suspend fun primeStart(deviceUid: String, slotId: String) =
            success(current().copy(manualActive = true)).also { primeStarts += 1 }

        override suspend fun primeStop(deviceUid: String, slotId: String) =
            success(current().copy(manualActive = false)).also { primeStops += 1 }

        override suspend fun start(deviceUid: String, slotId: String) = success(current())

        override suspend fun finish(
            deviceUid: String,
            slotId: String,
            measuredMl: Double
        ) = success(current())

        override suspend fun startVerificationDose(deviceUid: String, slotId: String) =
            success(current())

        override suspend fun stopVerificationDose(deviceUid: String, slotId: String) =
            success(current().copy(manualActive = false)).also { verificationStops += 1 }

        override suspend fun confirm(deviceUid: String, slotId: String) =
            confirmResult.also { confirms += 1 }

        override suspend fun cancel(deviceUid: String, slotId: String) =
            success(current().copy(
                sessionPhase = DeviceDosingCalibrationSessionPhase.IDLE,
                manualActive = false
            )).also { cancels += 1 }

        private fun current() = requireNotNull(state.value)
    }

    private companion object {
        fun success(snapshot: DeviceDosingCalibrationSnapshot) =
            DeviceDosingCalibrationResult.Success(snapshot)

        fun snapshot(
            phase: DeviceDosingCalibrationSessionPhase =
                DeviceDosingCalibrationSessionPhase.IDLE,
            calibrated: Boolean = false,
            verificationStarted: Boolean = false,
            verificationComplete: Boolean = false,
            verificationRemainingMs: Long = 0L,
            pendingDoseMsPerMl: Long = -1L,
            manualActive: Boolean = false
        ) = DeviceDosingCalibrationSnapshot(
            deviceUid = "device-1",
            slotId = "channel-1",
            pumpCount = 2,
            channelNumber = 1,
            channelTitle = "Channel 1",
            deviceUptimeMs = 12_000L,
            calibrated = calibrated,
            lastCalibratedAt = if (calibrated) 100L else 0L,
            sessionPhase = phase,
            startedAtUptimeMs = if (phase == DeviceDosingCalibrationSessionPhase.RUNNING) {
                10_000L
            } else {
                0L
            },
            durationMs = if (phase == DeviceDosingCalibrationSessionPhase.IDLE) 0L else 5_000L,
            measuredMl = if (
                phase == DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION
            ) 4.0 else 0.0,
            pendingDoseMsPerMl = pendingDoseMsPerMl,
            verificationDoseStarted = verificationStarted,
            verificationDoseComplete = verificationComplete,
            verificationDoseRemainingMs = verificationRemainingMs,
            manualActive = manualActive
        )
    }
}
