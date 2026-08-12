package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.DeviceDosingCalibrationSnapshot

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
        state = current.copy(
            isLoading = false,
            isBusy = false,
            step = DeviceDosingCalibrationStep.PRIME,
            channelTitle = snapshot.channelTitle,
            displayName = snapshot.channelTitle,
            error = null
        ),
        markLocalProgress = true
    )
    DosingCalibrationOperation.PrimeStart,
    DosingCalibrationOperation.PrimeStop -> DosingCalibrationSuccessTransition()
    DosingCalibrationOperation.ContinueFromPrime -> DosingCalibrationSuccessTransition(
        state = current.copy(
            isBusy = false,
            isPumpActive = false,
            step = DeviceDosingCalibrationStep.CALIBRATION_RUN,
            error = null
        ),
        markLocalProgress = true
    )
    DosingCalibrationOperation.StartCalibration,
    is DosingCalibrationOperation.FinishMeasurement,
    DosingCalibrationOperation.StartVerification -> DosingCalibrationSuccessTransition(
        applySnapshot = true,
        markLocalProgress = true
    )
    DosingCalibrationOperation.ConfirmVerification -> DosingCalibrationSuccessTransition(
        state = current.copy(
            isBusy = false,
            isPumpActive = false,
            error = null
        ),
        emitCompleted = true
    )
    DosingCalibrationOperation.RejectVerification -> DosingCalibrationSuccessTransition(
        state = current.copy(
            isBusy = false,
            isPumpActive = false,
            remainingMs = 0L,
            measuredMl = "",
            step = DeviceDosingCalibrationStep.CALIBRATION_RUN,
            error = null
        ),
        markLocalProgress = true
    )
}
