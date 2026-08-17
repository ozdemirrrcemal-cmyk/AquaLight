package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationFailure
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSessionPhase
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DosingCalibrationExitConcurrencyTest {
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
    fun `exit waits for in flight start then cleans the authoritative session`() =
        runTest(dispatcher) {
            val operations = ExitRaceOperations(calibrationSnapshot())
            val viewModel = viewModel(operations)
            advanceToCalibrationRun(viewModel)
            val gate = CompletableDeferred<Unit>()
            operations.startGate = gate

            viewModel.onAction(DeviceDosingCalibrationAction.StartCalibration)
            runCurrent()
            assertEquals(1, operations.starts)
            viewModel.requestExit()
            runCurrent()
            assertEquals(0, operations.cancelAttempts)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, operations.cancelAttempts)
            assertTrue(viewModel.events.first() is DeviceDosingCalibrationEvent.Exit)
        }

    @Test
    fun `exit during final confirm cannot race a completed terminal event`() = runTest(dispatcher) {
        val operations = ExitRaceOperations(completedVerificationSnapshot()).apply {
            confirmResult = calibrationSuccess(
                calibratedCalibrationSnapshot().copy(channelTitle = DISPLAY_NAME)
            )
        }
        val gate = CompletableDeferred<Unit>()
        operations.confirmGate = gate
        val viewModel = viewModel(operations)
        bind(viewModel, restoredName = DISPLAY_NAME)
        advanceUntilIdle()

        viewModel.onAction(DeviceDosingCalibrationAction.AcceptVerification)
        runCurrent()
        viewModel.requestExit()
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, operations.confirms)
        assertEquals(0, operations.cancelAttempts)
        assertTrue(viewModel.events.first() is DeviceDosingCalibrationEvent.Exit)
        assertNull(withTimeoutOrNull(NO_EVENT_WAIT_MS) { viewModel.events.first() })
    }

    @Test
    fun `cleanup failure remains visible and exit is retryable`() = runTest(dispatcher) {
        val operations = ExitRaceOperations(activeVerificationSnapshot()).apply {
            cancelFailureOnce = DeviceDosingCalibrationFailure.STORAGE
        }
        val viewModel = viewModel(operations)
        bind(viewModel, restoredName = DISPLAY_NAME)
        // Expired verification may own one scheduled authoritative poll; settle only immediate work.
        runCurrent()

        viewModel.requestExit()
        advanceUntilIdle()
        assertEquals(1, operations.cancelAttempts)
        assertEquals(DeviceDosingCalibrationError.STORAGE, viewModel.uiState.value.error)
        assertNull(withTimeoutOrNull(NO_EVENT_WAIT_MS) { viewModel.events.first() })

        viewModel.requestExit()
        advanceUntilIdle()
        assertEquals(2, operations.cancelAttempts)
        assertTrue(viewModel.events.first() is DeviceDosingCalibrationEvent.Exit)
    }

    private fun viewModel(operations: DeviceDosingCalibrationOperations) =
        DeviceDosingChannelCalibrationViewModel(
            operations = operations,
            clock = DeviceDosingCalibrationClock { dispatcher.scheduler.currentTime }
        )

    private fun bind(
        viewModel: DeviceDosingChannelCalibrationViewModel,
        restoredName: String? = null
    ) {
        viewModel.bind(calibrationRoute().copy(restoredDisplayNameDraft = restoredName))
    }

    private fun TestScope.advanceToCalibrationRun(
        viewModel: DeviceDosingChannelCalibrationViewModel
    ) {
        bind(viewModel)
        advanceUntilIdle()
        viewModel.onAction(DeviceDosingCalibrationAction.DisplayNameChanged(DISPLAY_NAME))
        viewModel.onAction(DeviceDosingCalibrationAction.SaveDisplayName)
        runCurrent()
        viewModel.onAction(DeviceDosingCalibrationAction.PrimeContinue)
        advanceUntilIdle()
        assertEquals(DeviceDosingCalibrationStep.CALIBRATION_RUN, viewModel.uiState.value.step)
    }

    private companion object {
        const val DISPLAY_NAME = "Trace Elements"
        const val NO_EVENT_WAIT_MS = 1L
    }
}

private class ExitRaceOperations private constructor(
    private val delegate: FakeDosingCalibrationOperations
) : DeviceDosingCalibrationOperations by delegate {
    constructor(initial: DeviceDosingCalibrationSnapshot) :
        this(FakeDosingCalibrationOperations(initial))

    var startGate: CompletableDeferred<Unit>? = null
    var confirmGate: CompletableDeferred<Unit>? = null
    var cancelFailureOnce: DeviceDosingCalibrationFailure? = null
    var starts = 0
    var cancelAttempts = 0

    val confirms: Int
        get() = delegate.confirms

    var confirmResult: DeviceDosingCalibrationResult
        get() = delegate.confirmResult
        set(value) {
            delegate.confirmResult = value
        }

    override suspend fun exitSafely(
        deviceUid: String,
        slotId: String,
        primeMayBeActive: Boolean,
        lastKnownSnapshot: DeviceDosingCalibrationSnapshot?
    ): DeviceDosingCalibrationResult =
        super<DeviceDosingCalibrationOperations>.exitSafely(
            deviceUid = deviceUid,
            slotId = slotId,
            primeMayBeActive = primeMayBeActive,
            lastKnownSnapshot = lastKnownSnapshot
        )

    override suspend fun start(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult {
        starts += 1
        val baseline = when (val result = delegate.refresh(deviceUid, slotId)) {
            is DeviceDosingCalibrationResult.Success -> result.snapshot
            is DeviceDosingCalibrationResult.Rejected -> return result
        }
        startGate?.await()
        val running = baseline.copy(
            sessionPhase = DeviceDosingCalibrationSessionPhase.RUNNING,
            startedAtUptimeMs = baseline.deviceUptimeMs,
            durationMs = CALIBRATION_DURATION_MS,
            manualActive = true
        )
        delegate.publish(running)
        return calibrationSuccess(running)
    }

    override suspend fun confirm(
        deviceUid: String,
        slotId: String,
        displayName: String
    ): DeviceDosingCalibrationResult {
        confirmGate?.await()
        return delegate.confirm(deviceUid, slotId, displayName)
    }

    override suspend fun cancel(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult {
        cancelAttempts += 1
        val failure = cancelFailureOnce
        if (failure != null) {
            cancelFailureOnce = null
            return DeviceDosingCalibrationResult.Rejected(failure)
        }
        val result = delegate.cancel(deviceUid, slotId)
        if (result is DeviceDosingCalibrationResult.Success) {
            delegate.publish(result.snapshot)
        }
        return result
    }

    private companion object {
        const val CALIBRATION_DURATION_MS = 5_000L
    }
}
