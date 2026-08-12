@file:Suppress("LongParameterList", "TooManyFunctions")

package com.aqua.aqualight.application.devices

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/** Application boundary for the six-step pump calibration workflow. */
interface DeviceDosingCalibrationOperations {
    val constraints: DeviceDosingCalibrationConstraints
        get() = DeviceDosingCalibrationConstraints()

    fun observe(deviceUid: String, slotId: String): Flow<DeviceDosingCalibrationSnapshot?>

    suspend fun refresh(deviceUid: String, slotId: String): DeviceDosingCalibrationResult

    suspend fun saveDisplayName(
        deviceUid: String,
        slotId: String,
        displayName: String
    ): DeviceDosingCalibrationResult

    suspend fun primeStart(deviceUid: String, slotId: String): DeviceDosingCalibrationResult
    suspend fun primeStop(deviceUid: String, slotId: String): DeviceDosingCalibrationResult

    /** Semantic safety stop used by host/timeout lifecycle events. */
    suspend fun primeSafetyStop(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = primeStop(deviceUid, slotId)

    /**
     * Owns the product prime safety window outside presentation.
     * Cancelling the caller cancels the pending stop before the window expires.
     */
    suspend fun awaitPrimeSafetyStop(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult {
        delay(constraints.primeSafetyTimeoutMs)
        return primeSafetyStop(deviceUid, slotId)
    }

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

    /**
     * Owns calibration exit safety ordering in the application layer.
     *
     * This composes existing central runtime operations only; it does not introduce a new
     * firmware action. The last observed snapshot is accepted as a safety hint so loss of
     * connectivity during host exit does not erase already-known cleanup obligations.
     */
    suspend fun exitSafely(
        deviceUid: String,
        slotId: String,
        primeMayBeActive: Boolean,
        lastKnownSnapshot: DeviceDosingCalibrationSnapshot?
    ) {
        if (primeMayBeActive) {
            runCatching { primeSafetyStop(deviceUid, slotId) }
        }

        val snapshot = lastKnownSnapshot ?: runCatching { refresh(deviceUid, slotId) }
            .getOrNull()
            .successSnapshotOrNull()

        if (snapshot?.verificationDoseStarted == true && !snapshot.verificationDoseComplete) {
            runCatching { stopVerificationDose(deviceUid, slotId) }
        }

        if (snapshot?.sessionPhase?.let { phase ->
                phase != DeviceDosingCalibrationSessionPhase.IDLE
            } == true
        ) {
            runCatching { cancel(deviceUid, slotId) }
        }
    }
}

data class DeviceDosingCalibrationConstraints(
    val maxDisplayNameCharacters: Int = 32,
    val minMeasuredMl: Double = 0.05,
    val maxMeasuredMl: Double = 1_000.0,
    val primeSafetyTimeoutMs: Long = 30_000L
) {
    init {
        require(maxDisplayNameCharacters > 0)
        require(minMeasuredMl.isFinite() && minMeasuredMl > 0.0)
        require(maxMeasuredMl.isFinite() && maxMeasuredMl >= minMeasuredMl)
        require(primeSafetyTimeoutMs > 0L)
    }
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

private fun DeviceDosingCalibrationResult?.successSnapshotOrNull(): DeviceDosingCalibrationSnapshot? =
    (this as? DeviceDosingCalibrationResult.Success)?.snapshot
