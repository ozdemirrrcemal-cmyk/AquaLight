package com.aqua.aqualight.application.devices

/**
 * Owner-scoped application boundary for the Dose Pro calibration wizard.
 *
 * Presentation never sees WebSocket payloads, firmware repository types or transport outcomes.
 * Firmware remains authoritative for channel identity, capabilities and calibration results.
 */
interface DeviceDosingCalibrationOperations {
    suspend fun loadChannel(
        deviceUid: String,
        channelKey: String
    ): Result<DeviceDosingCalibrationChannelSnapshot>

    suspend fun updateDisplayName(
        deviceUid: String,
        channelKey: String,
        displayName: String
    ): Result<DeviceDosingCalibrationChannelSnapshot>

    suspend fun startPrime(deviceUid: String, channelKey: String): Result<Unit>

    suspend fun stopPrime(deviceUid: String, channelKey: String): Result<Unit>

    suspend fun startCalibrationDose(
        deviceUid: String,
        channelKey: String
    ): Result<DeviceDosingCalibrationRun>

    suspend fun finishCalibrationDose(
        deviceUid: String,
        channelKey: String,
        measuredMl: Double
    ): Result<DeviceDosingCalibrationCandidate>

    suspend fun startVerificationDose(
        deviceUid: String,
        channelKey: String,
        amountMl: Double
    ): Result<DeviceDosingVerificationRun>

    suspend fun stopVerificationDose(deviceUid: String, channelKey: String): Result<Unit>

    suspend fun confirmCalibration(
        deviceUid: String,
        channelKey: String
    ): Result<DeviceDosingCalibrationChannelSnapshot>

    suspend fun cancelCalibration(
        deviceUid: String,
        channelKey: String
    ): Result<DeviceDosingCalibrationChannelSnapshot>
}

data class DeviceDosingCalibrationChannelSnapshot(
    val pumpCount: Int,
    val channelNumber: Int,
    val channelKey: String,
    val displayName: String,
    val calibrated: Boolean,
    val calibrationEditable: Boolean,
    val supportsPrime: Boolean,
    val supportsManualDose: Boolean,
    val minimumMeasuredMl: Double,
    val maximumMeasuredMl: Double,
    val maximumVerificationDoseMl: Double
) {
    init {
        require(pumpCount > 0) { "Dosing calibration requires a positive pump count." }
        require(channelNumber in 1..pumpCount) { "Dosing calibration channel is outside the device." }
        require(channelKey.isNotBlank()) { "Dosing calibration requires a channel key." }
        require(displayName.isNotBlank()) { "Dosing calibration requires a display name." }
        require(minimumMeasuredMl > 0.0 && minimumMeasuredMl.isFinite())
        require(maximumMeasuredMl >= minimumMeasuredMl && maximumMeasuredMl.isFinite())
        require(maximumVerificationDoseMl > 0.0 && maximumVerificationDoseMl.isFinite())
    }
}

data class DeviceDosingCalibrationRun(
    val durationMs: Long
) {
    init {
        require(durationMs > 0L) { "Calibration duration must be positive." }
    }
}

data class DeviceDosingCalibrationCandidate(
    val measuredMl: Double,
    val durationMs: Long,
    val pendingDoseMsPerMl: Long
) {
    init {
        require(measuredMl > 0.0 && measuredMl.isFinite())
        require(durationMs > 0L)
        require(pendingDoseMsPerMl > 0L)
    }
}

data class DeviceDosingVerificationRun(
    val amountMl: Double,
    val durationMs: Long
) {
    init {
        require(amountMl > 0.0 && amountMl.isFinite())
        require(durationMs > 0L)
    }
}
