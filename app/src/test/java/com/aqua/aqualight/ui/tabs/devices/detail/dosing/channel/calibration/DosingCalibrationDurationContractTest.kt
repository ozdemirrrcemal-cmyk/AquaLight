package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationConstraints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingCalibrationDurationContractTest {
    @Test
    fun `collection illustration follows the firmware authoritative duration`() {
        val state = DeviceDosingCalibrationUiState(
            progress = DosingCalibrationProgressState(
                isLoading = false,
                step = DeviceDosingCalibrationStep.CALIBRATION_RUN,
                operationDurationMs = 2_000L
            )
        )

        assertEquals(2_000, state.illustrationOperationDurationMillis())
        assertEquals(
            DEFAULT_ILLUSTRATION_DURATION_MILLIS,
            state.updateProgress { it.copy(operationDurationMs = 0L) }
                .illustrationOperationDurationMillis()
        )
    }

    @Test
    fun `collection start disables duplicate actions until authoritative completion`() {
        val ready = DeviceDosingCalibrationUiState(
            progress = DosingCalibrationProgressState(
                isLoading = false,
                step = DeviceDosingCalibrationStep.CALIBRATION_RUN
            )
        )
        val first = reduceDosingCalibrationAction(
            state = ready,
            primeRequested = false,
            action = DeviceDosingCalibrationAction.StartCalibration,
            constraints = DeviceDosingCalibrationConstraints()
        )
        val duplicate = reduceDosingCalibrationAction(
            state = first.state,
            primeRequested = false,
            action = DeviceDosingCalibrationAction.StartCalibration,
            constraints = DeviceDosingCalibrationConstraints()
        )

        assertTrue(first.state.isBusy)
        assertEquals(DosingCalibrationOperation.StartCalibration, first.operation)
        assertTrue(duplicate.state.isBusy)
        assertNull(duplicate.operation)
        assertFalse(duplicate.state.isLoading)
    }
}
