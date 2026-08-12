package com.aqua.aqualight.application.devices

object DeviceDosingReservoirDraftPolicy {
    const val DEFAULT_CAPACITY_ML = 450.0

    fun validCapacityOrNull(capacityMl: Double): Double? =
        capacityMl.takeIf { value -> value.isFinite() && value > 0.0 }
}

object DeviceDosingChannelDetailDraftPolicy {
    fun isValidCalibrationEpochSeconds(epochSeconds: Long): Boolean =
        epochSeconds in 1L..MAX_CALIBRATION_EPOCH_SECONDS

    private const val MAX_CALIBRATION_EPOCH_SECONDS = 0xFFFF_FFFFL
}
