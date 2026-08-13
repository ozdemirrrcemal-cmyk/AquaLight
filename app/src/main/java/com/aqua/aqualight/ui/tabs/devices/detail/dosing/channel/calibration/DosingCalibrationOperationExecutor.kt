package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationResult

internal suspend fun performDosingCalibrationOperation(
    operations: DeviceDosingCalibrationOperations,
    route: DeviceDosingCalibrationRoute,
    operation: DosingCalibrationOperation
): DeviceDosingCalibrationResult = when (operation) {
    DosingCalibrationOperation.Refresh -> operations.refresh(route.deviceUid, route.slotId)
    is DosingCalibrationOperation.SaveDisplayName -> operations.saveDisplayName(
        route.deviceUid,
        route.slotId,
        operation.name
    )
    DosingCalibrationOperation.PrimeStart -> operations.primeStart(route.deviceUid, route.slotId)
    DosingCalibrationOperation.PrimeStop,
    DosingCalibrationOperation.ContinueFromPrime -> operations.primeStop(
        route.deviceUid,
        route.slotId
    )
    DosingCalibrationOperation.StartCalibration -> operations.start(route.deviceUid, route.slotId)
    is DosingCalibrationOperation.FinishMeasurement -> operations.finish(
        route.deviceUid,
        route.slotId,
        operation.measuredMl
    )
    DosingCalibrationOperation.StartVerification -> operations.startVerificationDose(
        route.deviceUid,
        route.slotId
    )
    DosingCalibrationOperation.ConfirmVerification -> operations.confirm(
        route.deviceUid,
        route.slotId
    )
    DosingCalibrationOperation.RejectVerification -> operations.cancel(
        route.deviceUid,
        route.slotId
    )
}
