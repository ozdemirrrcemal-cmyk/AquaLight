package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.DeviceDosingChannelDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        val operations = FakeDosingCalibrationOperations(calibrationSnapshot())
        val viewModel = viewModel(operations)

        bind(viewModel)
        advanceUntilIdle()
        assertEquals(DeviceDosingCalibrationStep.NAME, viewModel.uiState.value.step)

        viewModel.onAction(DeviceDosingCalibrationAction.DisplayNameChanged(" Trace Elements "))
        viewModel.onAction(DeviceDosingCalibrationAction.SaveDisplayName)
        advanceUntilIdle()
        assertEquals(DeviceDosingCalibrationStep.PRIME, viewModel.uiState.value.step)
        assertEquals("Trace Elements", operations.savedName)

        viewModel.onAction(DeviceDosingCalibrationAction.PrimePressed)
        runCurrent()
        assertTrue(viewModel.uiState.value.isPumpActive)
        viewModel.onAction(DeviceDosingCalibrationAction.PrimeReleased)
        advanceUntilIdle()

        assertEquals(1, operations.primeStarts)
        assertEquals(1, operations.primeStops)
        assertFalse(viewModel.uiState.value.isPumpActive)
    }

    @Test
    fun `completed verification resumes at confirmation and emits detail target`() = runTest(dispatcher) {
        val operations = FakeDosingCalibrationOperations(completedVerificationSnapshot()).apply {
            confirmResult = calibrationSuccess(calibratedCalibrationSnapshot())
        }
        val viewModel = viewModel(operations)

        bind(viewModel)
        advanceUntilIdle()
        assertEquals(DeviceDosingCalibrationStep.CONFIRMATION, viewModel.uiState.value.step)

        viewModel.onAction(DeviceDosingCalibrationAction.AcceptVerification)
        advanceUntilIdle()
        val event = viewModel.events.first() as DeviceDosingCalibrationEvent.Completed

        assertEquals(DeviceDosingChannelDestination.DETAIL, event.target.destination)
        assertEquals("channel-1", event.target.slotId)
        assertEquals(100L, event.target.lastCalibratedAtEpochSeconds)
        assertEquals(1, operations.confirms)
    }

    @Test
    fun `already committed calibration recovers directly to detail`() = runTest(dispatcher) {
        val operations = FakeDosingCalibrationOperations(calibratedCalibrationSnapshot())
        val viewModel = viewModel(operations)

        bind(viewModel)
        advanceUntilIdle()
        val event = viewModel.events.first() as DeviceDosingCalibrationEvent.Completed

        assertEquals(DeviceDosingChannelDestination.DETAIL, event.target.destination)
        assertEquals(0, operations.confirms)
    }

    @Test
    fun `calibrated channel remains in flow when recalibration is requested`() = runTest(dispatcher) {
        val operations = FakeDosingCalibrationOperations(calibratedCalibrationSnapshot())
        val viewModel = viewModel(operations)

        bind(viewModel, recalibration = true)
        advanceUntilIdle()

        assertEquals(DeviceDosingCalibrationStep.NAME, viewModel.uiState.value.step)
        assertNull(withTimeoutOrNull(1L) { viewModel.events.first() })
    }

    @Test
    fun `exiting active verification stops dose then discards pending session`() = runTest(dispatcher) {
        val operations = FakeDosingCalibrationOperations(activeVerificationSnapshot())
        val viewModel = viewModel(operations)

        bind(viewModel)
        advanceUntilIdle()
        viewModel.requestExit()
        advanceUntilIdle()

        assertEquals(1, operations.verificationStops)
        assertEquals(1, operations.cancels)
        assertTrue(viewModel.events.first() is DeviceDosingCalibrationEvent.Exit)
    }

    private fun viewModel(operations: FakeDosingCalibrationOperations) =
        DeviceDosingChannelCalibrationViewModel(
            operations = operations,
            clock = DeviceDosingCalibrationClock { dispatcher.scheduler.currentTime }
        )

    private fun bind(
        viewModel: DeviceDosingChannelCalibrationViewModel,
        recalibration: Boolean = false
    ) {
        viewModel.bind(calibrationRoute(recalibration))
    }
}
