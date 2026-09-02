package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationFailure
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDestination
import kotlinx.coroutines.CompletableDeferred
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
    fun `name remains a local draft until final calibration confirmation`() = runTest(dispatcher) {
        val operations = FakeDosingCalibrationOperations(calibrationSnapshot())
        val viewModel = viewModel(operations)

        bind(viewModel)
        advanceUntilIdle()
        assertEquals(DeviceDosingCalibrationStep.NAME, viewModel.uiState.value.step)

        viewModel.onAction(DeviceDosingCalibrationAction.DisplayNameChanged(" Trace Elements "))
        viewModel.onAction(DeviceDosingCalibrationAction.SaveDisplayName)
        runCurrent()

        assertEquals(DeviceDosingCalibrationStep.PRIME, viewModel.uiState.value.step)
        assertEquals("Trace Elements", viewModel.uiState.value.displayName)
        assertEquals("", operations.confirmedName)
        assertEquals(0, operations.confirms)

        viewModel.onAction(DeviceDosingCalibrationAction.PrimePressed)
        runCurrent()
        assertTrue(viewModel.uiState.value.isPumpActive)
        viewModel.onAction(DeviceDosingCalibrationAction.PrimeReleased)
        runCurrent()

        assertEquals(1, operations.primeStarts)
        assertEquals(1, operations.primeStops)
        assertFalse(viewModel.uiState.value.isPumpActive)
    }

    @Test
    fun `firmware refresh never overwrites the uncommitted name draft`() = runTest(dispatcher) {
        val operations = FakeDosingCalibrationOperations(calibrationSnapshot())
        val viewModel = viewModel(operations)

        bind(viewModel)
        advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.displayName)

        viewModel.onAction(DeviceDosingCalibrationAction.DisplayNameChanged("Trace Elements"))
        operations.publish(calibrationSnapshot().copy(channelTitle = "Firmware Name"))
        runCurrent()

        assertEquals("Trace Elements", viewModel.uiState.value.displayName)
        viewModel.onAction(DeviceDosingCalibrationAction.SaveDisplayName)
        runCurrent()

        assertEquals(DeviceDosingCalibrationStep.PRIME, viewModel.uiState.value.step)
        assertEquals("Trace Elements", viewModel.uiState.value.displayName)
        assertEquals("", operations.confirmedName)
    }

    @Test
    fun `calibration name starts empty and is mandatory`() = runTest(dispatcher) {
        val operations = FakeDosingCalibrationOperations(calibrationSnapshot())
        val viewModel = viewModel(operations)

        bind(viewModel)
        advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.displayName)
        viewModel.onAction(DeviceDosingCalibrationAction.SaveDisplayName)
        runCurrent()

        assertEquals(DeviceDosingCalibrationStep.NAME, viewModel.uiState.value.step)
        assertEquals(
            DeviceDosingCalibrationError.DISPLAY_NAME_REQUIRED,
            viewModel.uiState.value.error
        )
        assertEquals("", operations.confirmedName)
    }

    @Test
    fun `name draft is not truncated and byte overflow is rendered semantically`() =
        runTest(dispatcher) {
            val operations = FakeDosingCalibrationOperations(calibrationSnapshot())
            val viewModel = viewModel(operations)

            bind(viewModel)
            advanceUntilIdle()
            val oversizedName = "ş".repeat(17)
            viewModel.onAction(DeviceDosingCalibrationAction.DisplayNameChanged(oversizedName))
            viewModel.onAction(DeviceDosingCalibrationAction.SaveDisplayName)
            runCurrent()

            assertEquals(oversizedName, viewModel.uiState.value.displayName)
            assertEquals(
                DeviceDosingCalibrationError.DISPLAY_NAME_TOO_LONG,
                viewModel.uiState.value.error
            )
            assertEquals("", operations.confirmedName)
        }

    @Test
    fun `failed prime start clears request and the semantic error survives status refresh`() =
        runTest(dispatcher) {
            val operations = FakeDosingCalibrationOperations(calibrationSnapshot()).apply {
                primeStartResult = DeviceDosingCalibrationResult.Rejected(
                    DeviceDosingCalibrationFailure.OPERATION_IN_PROGRESS
                )
            }
            val viewModel = viewModel(operations)

            bind(viewModel)
            advanceUntilIdle()
            viewModel.onAction(DeviceDosingCalibrationAction.DisplayNameChanged("Trace Elements"))
            viewModel.onAction(DeviceDosingCalibrationAction.SaveDisplayName)
            runCurrent()

            viewModel.onAction(DeviceDosingCalibrationAction.PrimePressed)
            runCurrent()

            assertEquals(
                DeviceDosingCalibrationError.OPERATION_IN_PROGRESS,
                viewModel.uiState.value.error
            )
            assertFalse(viewModel.uiState.value.isPumpActive)
            assertEquals(1, operations.primeStarts)
            assertEquals(0, operations.primeStops)

            operations.publish(calibrationSnapshot())
            runCurrent()
            assertEquals(
                DeviceDosingCalibrationError.OPERATION_IN_PROGRESS,
                viewModel.uiState.value.error
            )

            viewModel.onAction(DeviceDosingCalibrationAction.PrimeReleased)
            runCurrent()
            assertEquals(0, operations.primeStops)

            viewModel.onAction(DeviceDosingCalibrationAction.PrimePressed)
            runCurrent()
            assertEquals(2, operations.primeStarts)
            assertEquals(0, operations.primeStops)
        }

    @Test
    fun `failed physical prime stop keeps the authoritative pump active`() = runTest(dispatcher) {
        val operations = FakeDosingCalibrationOperations(calibrationSnapshot())
        val viewModel = viewModel(operations)

        bind(viewModel)
        advanceUntilIdle()
        viewModel.onAction(DeviceDosingCalibrationAction.DisplayNameChanged("Trace Elements"))
        viewModel.onAction(DeviceDosingCalibrationAction.SaveDisplayName)
        runCurrent()
        viewModel.onAction(DeviceDosingCalibrationAction.PrimePressed)
        runCurrent()
        assertTrue(viewModel.uiState.value.isPumpActive)

        operations.primeStopResult = DeviceDosingCalibrationResult.Rejected(
            DeviceDosingCalibrationFailure.OUTPUT_STOP_UNCONFIRMED
        )
        viewModel.onAction(DeviceDosingCalibrationAction.PrimeReleased)
        runCurrent()

        assertEquals(
            DeviceDosingCalibrationError.OUTPUT_STOP_UNCONFIRMED,
            viewModel.uiState.value.error
        )
        assertTrue(viewModel.uiState.value.isPumpActive)
        assertEquals(1, operations.primeStops)
    }

    @Test
    fun `mutation invalidation does not replace the active calibration screen with loading state`() =
        runTest(dispatcher) {
            val operations = FakeDosingCalibrationOperations(calibrationSnapshot())
            val viewModel = viewModel(operations)
            bind(viewModel)
            advanceUntilIdle()
            viewModel.onAction(DeviceDosingCalibrationAction.DisplayNameChanged("Trace Elements"))
            viewModel.onAction(DeviceDosingCalibrationAction.SaveDisplayName)
            runCurrent()

            val release = CompletableDeferred<Unit>()
            operations.primeStartBlocker = release
            viewModel.onAction(DeviceDosingCalibrationAction.PrimePressed)
            runCurrent()
            operations.publish(null)
            runCurrent()

            assertEquals(DeviceDosingCalibrationStep.PRIME, viewModel.uiState.value.step)
            assertFalse(viewModel.uiState.value.isLoading)
            assertTrue(viewModel.uiState.value.isPumpActive)

            release.complete(Unit)
            runCurrent()
            viewModel.onAction(DeviceDosingCalibrationAction.PrimeReleased)
            runCurrent()
            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.isPumpActive)
        }

    @Test
    fun `application failures retain semantic presentation identity`() {
        val expected = mapOf(
            DeviceDosingCalibrationFailure.CONNECTION to
                DeviceDosingCalibrationError.CONNECTION,
            DeviceDosingCalibrationFailure.STORAGE to DeviceDosingCalibrationError.STORAGE,
            DeviceDosingCalibrationFailure.HARDWARE to DeviceDosingCalibrationError.HARDWARE,
            DeviceDosingCalibrationFailure.OUTPUT_STOP_UNCONFIRMED to
                DeviceDosingCalibrationError.OUTPUT_STOP_UNCONFIRMED,
            DeviceDosingCalibrationFailure.OPERATION_IN_PROGRESS to
                DeviceDosingCalibrationError.OPERATION_IN_PROGRESS,
            DeviceDosingCalibrationFailure.DEVICE_TIME_NOT_READY to
                DeviceDosingCalibrationError.DEVICE_TIME_NOT_READY,
            DeviceDosingCalibrationFailure.CALIBRATION_STATE_MISMATCH to
                DeviceDosingCalibrationError.CALIBRATION_STATE_MISMATCH,
            DeviceDosingCalibrationFailure.INVALID_MEASUREMENT to
                DeviceDosingCalibrationError.INVALID_MEASUREMENT,
            DeviceDosingCalibrationFailure.INTERNAL to
                DeviceDosingCalibrationError.OPERATION_FAILED
        )

        assertEquals(DeviceDosingCalibrationFailure.entries.toSet(), expected.keys)
        expected.forEach { (failure, presentation) ->
            assertEquals(presentation, failure.toCalibrationError())
        }
    }

    @Test
    fun `same channel binding ignores presentation argument changes`() = runTest(dispatcher) {
        val operations = FakeDosingCalibrationOperations(calibrationSnapshot())
        val viewModel = viewModel(operations)

        bind(viewModel)
        advanceUntilIdle()
        assertEquals(1, operations.refreshes)

        viewModel.bind(
            calibrationRoute().copy(
                pumpCount = 4,
                channelNumber = 2
            )
        )
        advanceUntilIdle()

        assertEquals(1, operations.refreshes)
    }

    @Test
    fun `completed verification commits final name and emits detail target`() = runTest(dispatcher) {
        val operations = FakeDosingCalibrationOperations(completedVerificationSnapshot()).apply {
            confirmResult = calibrationSuccess(
                calibratedCalibrationSnapshot().copy(channelTitle = "Trace Elements")
            )
        }
        val viewModel = viewModel(operations)

        bind(viewModel, restoredName = "Trace Elements")
        advanceUntilIdle()
        assertEquals(DeviceDosingCalibrationStep.CONFIRMATION, viewModel.uiState.value.step)

        viewModel.onAction(DeviceDosingCalibrationAction.AcceptVerification)
        advanceUntilIdle()
        val event = viewModel.events.first() as DeviceDosingCalibrationEvent.Completed

        assertEquals("Trace Elements", operations.confirmedName)
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
    fun `recalibration prefills the existing persisted channel name`() = runTest(dispatcher) {
        val operations = FakeDosingCalibrationOperations(
            calibratedCalibrationSnapshot().copy(channelTitle = "Nitrat")
        )
        val viewModel = viewModel(operations)

        bind(viewModel, recalibration = true)
        advanceUntilIdle()

        assertEquals(DeviceDosingCalibrationStep.NAME, viewModel.uiState.value.step)
        assertEquals("Nitrat", viewModel.uiState.value.displayName)
        assertNull(withTimeoutOrNull(1L) { viewModel.events.first() })
    }

    @Test
    fun `restored local name draft wins over recalibration prefill`() = runTest(dispatcher) {
        val operations = FakeDosingCalibrationOperations(
            calibratedCalibrationSnapshot().copy(channelTitle = "Nitrat")
        )
        val viewModel = viewModel(operations)

        bind(viewModel, recalibration = true, restoredName = "Restored Draft")
        advanceUntilIdle()

        assertEquals("Restored Draft", viewModel.uiState.value.displayName)
    }

    @Test
    fun `exiting active verification stops dose then discards pending session`() = runTest(dispatcher) {
        val operations = FakeDosingCalibrationOperations(activeVerificationSnapshot())
        val viewModel = viewModel(operations)

        bind(viewModel, restoredName = "Trace Elements")
        // Active verification owns a recurring authoritative poll; settle only immediate work.
        runCurrent()
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
        recalibration: Boolean = false,
        restoredName: String? = null
    ) {
        viewModel.bind(
            calibrationRoute(recalibration).copy(restoredDisplayNameDraft = restoredName)
        )
    }
}
