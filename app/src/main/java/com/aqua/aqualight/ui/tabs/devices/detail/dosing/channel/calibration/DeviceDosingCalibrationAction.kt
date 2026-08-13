package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

internal sealed interface DeviceDosingCalibrationAction {
    data class DisplayNameChanged(val value: String) : DeviceDosingCalibrationAction
    data object SaveDisplayName : DeviceDosingCalibrationAction
    data object PrimePressed : DeviceDosingCalibrationAction
    data object PrimeReleased : DeviceDosingCalibrationAction
    data object PrimeContinue : DeviceDosingCalibrationAction
    data object StartCalibration : DeviceDosingCalibrationAction
    data class MeasuredMlChanged(val value: String) : DeviceDosingCalibrationAction
    data object SaveMeasurement : DeviceDosingCalibrationAction
    data object StartVerification : DeviceDosingCalibrationAction
    data object AcceptVerification : DeviceDosingCalibrationAction
    data object RejectVerification : DeviceDosingCalibrationAction
}
