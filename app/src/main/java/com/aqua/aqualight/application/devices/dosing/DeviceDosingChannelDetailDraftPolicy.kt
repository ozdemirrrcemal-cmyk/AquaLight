package com.aqua.aqualight.application.devices.dosing

object DeviceDosingChannelDetailDraftPolicy {
    fun isValidCalibrationEpochSeconds(epochSeconds: Long): Boolean =
        epochSeconds in 1L..MAX_CALIBRATION_EPOCH_SECONDS

    private const val MAX_CALIBRATION_EPOCH_SECONDS = 0xFFFF_FFFFL
}
