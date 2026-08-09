package com.aqua.aqualight.ui.tabs.devices.detail.dosing.calibration

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationOperations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Coordinates press/release and exit cleanup around pump commands.
 *
 * This is deliberately best-effort Android cleanup. Firmware must still provide its own
 * disconnect/session fail-safe before indefinite priming can be considered commercially safe.
 */
internal class DosingCalibrationPumpSafety(
    private val operations: DeviceDosingCalibrationOperations,
    private val scope: CoroutineScope,
    private val currentState: () -> DosingCalibrationUiState,
    private val updateState: (DosingCalibrationUiState) -> Unit,
    private val events: MutableSharedFlow<DosingCalibrationEvent>,
    private val cancelTimedDose: () -> Unit,
    private val fail: (@StringRes Int) -> Unit
) {
    private var primeRequested = false

    fun primePressed() {
        val state = currentState()
        if (!state.loaded || state.step != DosingCalibrationStep.PRIME || state.busy) return
        if (state.primeActive) return
        primeRequested = true
        updateState(
            state.copy(
                operation = DosingCalibrationOperation.STARTING_PRIME,
                errorMessageRes = null
            )
        )
        scope.launch {
            operations.startPrime(state.deviceUid, state.channelKey)
                .onSuccess {
                    if (primeRequested) {
                        updateState(currentState().copy(operation = DosingCalibrationOperation.PRIMING))
                    } else {
                        operations.stopPrime(state.deviceUid, state.channelKey)
                        updateState(currentState().copy(operation = DosingCalibrationOperation.IDLE))
                    }
                }
                .onFailure {
                    primeRequested = false
                    fail(R.string.device_dosing_calibration_error_command)
                }
        }
    }

    fun primeReleased() {
        primeRequested = false
        val state = currentState()
        if (state.operation != DosingCalibrationOperation.PRIMING) return
        updateState(state.copy(operation = DosingCalibrationOperation.STOPPING_PRIME))
        scope.launch {
            operations.stopPrime(state.deviceUid, state.channelKey)
                .onSuccess {
                    updateState(
                        currentState().copy(
                            operation = DosingCalibrationOperation.IDLE,
                            errorMessageRes = null
                        )
                    )
                }
                .onFailure { fail(R.string.device_dosing_calibration_error_command) }
        }
    }

    fun exitCalibration() {
        val state = currentState()
        if (state.operation == DosingCalibrationOperation.EXITING) return
        cancelTimedDose()
        primeRequested = false
        updateState(state.copy(operation = DosingCalibrationOperation.EXITING))
        scope.launch {
            if (state.loaded) {
                operations.stopPrime(state.deviceUid, state.channelKey)
                operations.stopVerificationDose(state.deviceUid, state.channelKey)
                operations.cancelCalibration(state.deviceUid, state.channelKey)
            }
            events.emit(DosingCalibrationEvent.Exit)
        }
    }
}
