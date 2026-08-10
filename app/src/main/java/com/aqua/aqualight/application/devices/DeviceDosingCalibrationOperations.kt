@file:Suppress("LongParameterList")

package com.aqua.aqualight.application.devices

import kotlinx.coroutines.flow.Flow

/** Application boundary for the six-step pump calibration workflow. */
interface DeviceDosingCalibrationOperations {
    fun observe(deviceUid: String, slotId: String): Flow<DeviceDosingCalibrationSnapshot?>

    suspend fun refresh(deviceUid: String, slotId: String): DeviceDosingCalibrationResult

    suspend fun saveDisplayName(
        deviceUid: String,
        slotId: String,
        displayName: String
    ): DeviceDosingCalibrationResult

    suspend fun primeStart(deviceUid: String, slotId: String): DeviceDosingCalibrationResult
    suspend fun primeStop(deviceUid: String, slotId: String): DeviceDosingCalibrationResult
    suspend fun start(deviceUid: String, slotId: String): DeviceDosingCalibrationResult

    suspend fun finish(
        deviceUid: String,
        slotId: String,
        measuredMl: Double
    ): DeviceDosingCalibrationResult

    suspend fun startVerificationDose(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult

    suspend fun stopVerificationDose(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult

    suspend fun confirm(deviceUid: String, slotId: String): DeviceDosingCalibrationResult
    suspend fun cancel(deviceUid: String, slotId: String): DeviceDosingCalibrationResult
}

enum class DeviceDosingCalibrationSessionPhase {
    IDLE,
    RUNNING,
    PENDING_VERIFICATION
}

data class DeviceDosingCalibrationSnapshot(
    val deviceUid: String,
    val slotId: String,
    val pumpCount: Int,
    val channelNumber: Int,
    val channelTitle: String,
    val deviceUptimeMs: Long,
    val calibrated: Boolean,
    val lastCalibratedAt: Long,
    val sessionPhase: DeviceDosingCalibrationSessionPhase,
    val startedAtUptimeMs: Long,
    val durationMs: Long,
    val measuredMl: Double,
    val pendingDoseMsPerMl: Long,
    val verificationDoseStarted: Boolean,
    val verificationDoseComplete: Boolean,
    val verificationDoseRemainingMs: Long,
    val manualActive: Boolean
)

sealed interface DeviceDosingCalibrationResult {
    data class Success(
        val snapshot: DeviceDosingCalibrationSnapshot,
        val operationDurationMs: Long? = null
    ) : DeviceDosingCalibrationResult

    data object Unavailable : DeviceDosingCalibrationResult
    data object Failed : DeviceDosingCalibrationResult
}
