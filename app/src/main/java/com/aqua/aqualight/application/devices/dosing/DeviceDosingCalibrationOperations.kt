@file:Suppress("LongParameterList", "TooManyFunctions")

package com.aqua.aqualight.application.devices.dosing

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/** Application boundary for the six-step pump calibration workflow. */
interface DeviceDosingCalibrationOperations {
    val constraints: DeviceDosingCalibrationConstraints
        get() = DeviceDosingCalibrationConstraints()

    fun observe(deviceUid: String, slotId: String): Flow<DeviceDosingCalibrationSnapshot?>

    suspend fun refresh(deviceUid: String, slotId: String): DeviceDosingCalibrationResult

    suspend fun primeStart(deviceUid: String, slotId: String): DeviceDosingCalibrationResult
    suspend fun primeStop(deviceUid: String, slotId: String): DeviceDosingCalibrationResult

    /** Semantic safety stop used by host/timeout lifecycle events. */
    suspend fun primeSafetyStop(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = primeStop(deviceUid, slotId)

    /** Product-owned safety deadline; the caller performs the stop after this returns. */
    suspend fun awaitPrimeSafetyStop() {
        delay(constraints.primeSafetyTimeoutMs)
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

    /**
     * Commits the final user-visible channel identity and confirmed calibration atomically in the
     * firmware-owned calibration confirmation transaction.
     */
    suspend fun confirm(
        deviceUid: String,
        slotId: String,
        displayName: String
    ): DeviceDosingCalibrationResult

    suspend fun cancel(deviceUid: String, slotId: String): DeviceDosingCalibrationResult

    /**
     * Owns calibration exit safety ordering in the application layer.
     *
     * Every cleanup command remains on the existing central runtime path. A fresh authoritative
     * refresh is mandatory after any already-running workflow mutation has settled, and exit only
     * succeeds after the final snapshot proves the channel is idle. Cleanup failures are returned
     * to presentation instead of being swallowed.
     */
    suspend fun exitSafely(
        deviceUid: String,
        slotId: String,
        primeMayBeActive: Boolean,
        lastKnownSnapshot: DeviceDosingCalibrationSnapshot?
    ): DeviceDosingCalibrationResult {
        var cleanupFailure: DeviceDosingCalibrationResult.Rejected? = null
        if (primeMayBeActive) {
            val result = primeSafetyStop(deviceUid, slotId)
            if (result is DeviceDosingCalibrationResult.Rejected) cleanupFailure = result
        }

        val refreshResult = refresh(deviceUid, slotId)
        var snapshot = when (refreshResult) {
            is DeviceDosingCalibrationResult.Success -> refreshResult.snapshot
            is DeviceDosingCalibrationResult.Rejected ->
                lastKnownSnapshot ?: return cleanupFailure ?: refreshResult
        }

        if (snapshot.verificationDoseStarted && !snapshot.verificationDoseComplete) {
            when (val result = stopVerificationDose(deviceUid, slotId)) {
                is DeviceDosingCalibrationResult.Success -> snapshot = result.snapshot
                is DeviceDosingCalibrationResult.Rejected ->
                    if (cleanupFailure == null) cleanupFailure = result
            }
        }
        if (snapshot.sessionPhase != DeviceDosingCalibrationSessionPhase.IDLE) {
            when (val result = cancel(deviceUid, slotId)) {
                is DeviceDosingCalibrationResult.Success -> Unit
                is DeviceDosingCalibrationResult.Rejected ->
                    if (cleanupFailure == null) cleanupFailure = result
            }
        }

        return when (val finalResult = refresh(deviceUid, slotId)) {
            is DeviceDosingCalibrationResult.Rejected -> cleanupFailure ?: finalResult
            is DeviceDosingCalibrationResult.Success -> when {
                cleanupFailure != null -> cleanupFailure
                finalResult.snapshot.sessionPhase != DeviceDosingCalibrationSessionPhase.IDLE ||
                    finalResult.snapshot.manualActive -> DeviceDosingCalibrationResult.Rejected(
                    DeviceDosingCalibrationFailure.CALIBRATION_STATE_MISMATCH
                )
                else -> finalResult
            }
        }
    }
}

data class DeviceDosingCalibrationConstraints(
    val minMeasuredMl: Double = 0.05,
    val maxMeasuredMl: Double = 1_000.0,
    val primeSafetyTimeoutMs: Long = 30_000L
) {
    init {
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
    /** Firmware-authoritative effective name: default name or the persisted user name. */
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
) {
    init {
        require(deviceUid.isNotBlank())
        require(slotId.isNotBlank())
        require(pumpCount > 0)
        require(channelNumber in 1..pumpCount)
        require(channelTitle.isNotBlank())
    }
}

sealed interface DeviceDosingCalibrationResult {
    data class Success(
        val snapshot: DeviceDosingCalibrationSnapshot,
        val operationDurationMs: Long? = null
    ) : DeviceDosingCalibrationResult

    data class Rejected(
        val failure: DeviceDosingCalibrationFailure
    ) : DeviceDosingCalibrationResult
}

/**
 * Application-owned calibration failure semantics.
 *
 * Firmware error codes, fields and messages are mapped into this closed set in data. Presentation
 * must never inspect the wire error identity. [INTERNAL] is a forward-compatible fail-closed
 * fallback and is rendered only as a generic operation failure.
 */
enum class DeviceDosingCalibrationFailure {
    CONNECTION,
    STORAGE,
    HARDWARE,
    OPERATION_IN_PROGRESS,
    DEVICE_TIME_NOT_READY,
    CALIBRATION_STATE_MISMATCH,
    INVALID_MEASUREMENT,
    INTERNAL
}
