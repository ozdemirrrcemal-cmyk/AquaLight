package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

internal data class DosingCalibrationSuccessTransition(
    val state: DeviceDosingCalibrationUiState? = null,
    val applySnapshot: Boolean = false,
    val markLocalProgress: Boolean = false,
    val emitCompleted: Boolean = false
)

internal fun dosingCalibrationSuccessTransition(
    operation: DosingCalibrationOperation,
    current: DeviceDosingCalibrationUiState
): DosingCalibrationSuccessTransition = when (operation) {
    DosingCalibrationOperation.Refresh -> DosingCalibrationSuccessTransition(
        applySnapshot = true
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
    is DosingCalibrationOperation.ConfirmVerification -> DosingCalibrationSuccessTransition(
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
