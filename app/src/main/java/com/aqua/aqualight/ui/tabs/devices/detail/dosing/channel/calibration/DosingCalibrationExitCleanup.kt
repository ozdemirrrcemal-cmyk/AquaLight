package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationSessionPhase
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationSnapshot

internal suspend fun cleanupDosingCalibrationSession(
    operations: DeviceDosingCalibrationOperations,
    route: DeviceDosingCalibrationRoute,
    state: DeviceDosingCalibrationUiState,
    snapshot: DeviceDosingCalibrationSnapshot?,
    primeRequested: Boolean
) {
    if (state.step == DeviceDosingCalibrationStep.PRIME &&
        (primeRequested || state.isPumpActive)
    ) {
        operations.primeStop(route.deviceUid, route.slotId)
    }
    if (state.step == DeviceDosingCalibrationStep.VERIFICATION &&
        snapshot?.verificationDoseStarted == true &&
        !snapshot.verificationDoseComplete
    ) {
        operations.stopVerificationDose(route.deviceUid, route.slotId)
    }
    if (snapshot?.sessionPhase != null &&
        snapshot.sessionPhase != DeviceDosingCalibrationSessionPhase.IDLE
    ) {
        operations.cancel(route.deviceUid, route.slotId)
    }
}
