package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationFailure
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationResult
import kotlinx.coroutines.Dispatchers
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

class DosingCalibrationCommercialCompletionTest {
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
    fun `failed final confirmation keeps the name draft and remains retryable`() =
        runTest(dispatcher) {
            val operations = FakeDosingCalibrationOperations(completedVerificationSnapshot()).apply {
                confirmResult = DeviceDosingCalibrationResult.Rejected(
                    DeviceDosingCalibrationFailure.INTERNAL
                )
            }
            val viewModel = viewModel(operations)

            bind(viewModel)
            advanceUntilIdle()
            assertEquals(DeviceDosingCalibrationStep.CONFIRMATION, viewModel.uiState.value.step)

            viewModel.onAction(DeviceDosingCalibrationAction.AcceptVerification)
            advanceUntilIdle()

            assertEquals(DeviceDosingCalibrationStep.CONFIRMATION, viewModel.uiState.value.step)
            assertEquals(DISPLAY_NAME, viewModel.uiState.value.displayName)
            assertEquals(DeviceDosingCalibrationError.OPERATION_FAILED, viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.isBusy)
            assertEquals(1, operations.confirms)

            operations.confirmResult = calibrationSuccess(
                calibratedCalibrationSnapshot().copy(channelTitle = DISPLAY_NAME)
            )
            viewModel.onAction(DeviceDosingCalibrationAction.AcceptVerification)
            advanceUntilIdle()

            assertTrue(viewModel.events.first() is DeviceDosingCalibrationEvent.Completed)
            assertEquals(2, operations.confirms)
            assertEquals(DISPLAY_NAME, operations.confirmedName)
        }

    @Test
    fun `authoritative completed state resolves an ambiguous confirmation without duplicate command`() =
        runTest(dispatcher) {
            val operations = FakeDosingCalibrationOperations(completedVerificationSnapshot()).apply {
                confirmResult = DeviceDosingCalibrationResult.Rejected(
                    DeviceDosingCalibrationFailure.CONNECTION
                )
            }
            val viewModel = viewModel(operations)

            bind(viewModel)
            advanceUntilIdle()
            viewModel.onAction(DeviceDosingCalibrationAction.AcceptVerification)
            advanceUntilIdle()

            assertEquals(DeviceDosingCalibrationError.CONNECTION, viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.isBusy)
            assertEquals(1, operations.confirms)

            operations.publish(
                calibratedCalibrationSnapshot().copy(channelTitle = DISPLAY_NAME)
            )
            runCurrent()

            assertTrue(viewModel.events.first() is DeviceDosingCalibrationEvent.Completed)
            assertEquals(1, operations.confirms)
        }

    private fun viewModel(operations: FakeDosingCalibrationOperations) =
        DeviceDosingChannelCalibrationViewModel(
            operations = operations,
            clock = DeviceDosingCalibrationClock { dispatcher.scheduler.currentTime }
        )

    private fun bind(viewModel: DeviceDosingChannelCalibrationViewModel) {
        viewModel.bind(
            calibrationRoute().copy(restoredDisplayNameDraft = DISPLAY_NAME)
        )
    }

    private companion object {
        const val DISPLAY_NAME = "Trace Elements"
    }
}
