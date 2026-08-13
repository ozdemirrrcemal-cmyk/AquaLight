package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot

internal data class DosingCalibrationSuccessTransition(
    val state: DeviceDosingCalibrationUiState? = null,
    val applySnapshot: Boolean = false,
    val markLocalProgress: Boolean = false,
    val emitCompleted: Boolean = false
)

internal fun dosingCalibrationSuccessTransition(
    operation: DosingCalibrationOperation,
    current: DeviceDosingCalibrationUiState,
    snapshot: DeviceDosingCalibrationSnapshot
): DosingCalibrationSuccessTransition = when (operation) {
    DosingCalibrationOperation.Refresh -> DosingCalibrationSuccessTransition(
        applySnapshot = true
    )
    is DosingCalibrationOperation.SaveDisplayName -> DosingCalibrationSuccessTransition(
        state = current
            .updateProgress { progress ->
                progress.copy(
                    isLoading = false,
                    isBusy = false,
                    step = DeviceDosingCalibrationStep.PRIME
                )
            }
            .updateChannel { channel -> channel.copy(channelTitle = snapshot.channelTitle) }
            .updateInput { input -> input.copy(displayName = snapshot.channelTitle) }
            .copy(error = null),
        markLocalProgress = true
    )
    DosingCalibrationOperation.PrimeStart,
    DosingCalibrationOperation.PrimeStop -> DosingCalibrationSuccessTransition()
    DosingCalibrationOperation.ContinueFromPrime -> DosingCalibrationSuccessTransition(
        state = current
            .updateProgress { progress ->
                progress.copy(
                    isBusy = false,
                    isPumpActive = false,
                    step = DeviceDosingCalibrationStep.CALIBRATION_RUN
                )
            }
            .copy(error = null),
        markLocalProgress = true
    )
    DosingCalibrationOperation.StartCalibration,
    is DosingCalibrationOperation.FinishMeasurement,
    DosingCalibrationOperation.StartVerification -> DosingCalibrationSuccessTransition(
        applySnapshot = true,
        markLocalProgress = true
    )
    DosingCalibrationOperation.ConfirmVerification -> DosingCalibrationSuccessTransition(
        state = current
            .updateProgress { progress -> progress.copy(isBusy = false, isPumpActive = false) }
            .copy(error = null),
        emitCompleted = true
    )
    DosingCalibrationOperation.RejectVerification -> DosingCalibrationSuccessTransition(
        state = current
            .updateProgress { progress ->
                progress.copy(
                    isBusy = false,
                    isPumpActive = false,
                    remainingMs = 0L,
                    step = DeviceDosingCalibrationStep.CALIBRATION_RUN
                )
            }
            .updateInput { input -> input.copy(measuredMl = "") }
            .copy(error = null),
        markLocalProgress = true
    )
}
