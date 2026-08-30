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
     * Owns expired verification reconciliation in the application safety boundary.
     *
     * Presentation schedules the deadline only. The stop and final authoritative refresh remain on
     * the existing central calibration operations path so lifecycle safety never moves into UI.
     */
    suspend fun reconcileVerificationDeadline(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = when (
        val stopResult = stopVerificationDose(deviceUid, slotId)
    ) {
        is DeviceDosingCalibrationResult.Success -> {
            if (stopResult.snapshot.verificationDoseComplete) {
                stopResult
            } else {
                refresh(deviceUid, slotId)
            }
        }
        is DeviceDosingCalibrationResult.Rejected -> stopResult
    }

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
    ): DeviceDosingCalibrationResult = DeviceDosingCalibrationExitCleanup(
        operations = this,
        deviceUid = deviceUid,
        slotId = slotId,
        primeMayBeActive = primeMayBeActive,
        lastKnownSnapshot = lastKnownSnapshot
    ).execute()
}

private class DeviceDosingCalibrationExitCleanup(
    private val operations: DeviceDosingCalibrationOperations,
    private val deviceUid: String,
    private val slotId: String,
    private val primeMayBeActive: Boolean,
    lastKnownSnapshot: DeviceDosingCalibrationSnapshot?
) {
    private var snapshot: DeviceDosingCalibrationSnapshot? = lastKnownSnapshot
    private var cleanupFailure: DeviceDosingCalibrationResult.Rejected? = null

    suspend fun execute(): DeviceDosingCalibrationResult {
        stopPrimeIfNeeded()
        val refreshFailure = refreshAuthoritativeSnapshot()
        return if (refreshFailure != null) {
            refreshFailure
        } else {
            stopVerificationIfNeeded()
            cancelCalibrationIfNeeded()
            verifyIdle()
        }
    }

    private suspend fun stopPrimeIfNeeded() {
        if (primeMayBeActive) {
            applyCleanupResult(operations.primeSafetyStop(deviceUid, slotId))
        }
    }

    private suspend fun refreshAuthoritativeSnapshot(): DeviceDosingCalibrationResult.Rejected? =
        when (val result = operations.refresh(deviceUid, slotId)) {
            is DeviceDosingCalibrationResult.Success -> {
                snapshot = result.snapshot
                null
            }
            is DeviceDosingCalibrationResult.Rejected ->
                if (snapshot == null) cleanupFailure ?: result else null
        }

    private suspend fun stopVerificationIfNeeded() {
        val current = snapshot
        if (current != null && current.verificationDoseStarted && !current.verificationDoseComplete) {
            applyCleanupResult(operations.stopVerificationDose(deviceUid, slotId))
        }
    }

    private suspend fun cancelCalibrationIfNeeded() {
        val current = snapshot
        if (current != null && current.sessionPhase != DeviceDosingCalibrationSessionPhase.IDLE) {
            applyCleanupResult(operations.cancel(deviceUid, slotId))
        }
    }

    private fun applyCleanupResult(result: DeviceDosingCalibrationResult) {
        when (result) {
            is DeviceDosingCalibrationResult.Success -> snapshot = result.snapshot
            is DeviceDosingCalibrationResult.Rejected ->
                if (cleanupFailure == null) cleanupFailure = result
        }
    }

    private suspend fun verifyIdle(): DeviceDosingCalibrationResult {
        val failure = cleanupFailure
        return when (val result = operations.refresh(deviceUid, slotId)) {
            is DeviceDosingCalibrationResult.Rejected -> failure ?: result
            is DeviceDosingCalibrationResult.Success -> when {
                failure != null -> failure
                result.snapshot.isSafeForCalibrationExit -> result
                else -> DeviceDosingCalibrationResult.Rejected(
                    DeviceDosingCalibrationFailure.CALIBRATION_STATE_MISMATCH
                )
            }
        }
    }
}

private val DeviceDosingCalibrationSnapshot.isSafeForCalibrationExit: Boolean
    get() = sessionPhase == DeviceDosingCalibrationSessionPhase.IDLE && !manualActive

data class DeviceDosingCalibrationConstraints(
    val minMeasuredMl: Double = 0.05,
    val maxMeasuredMl: Double = 1_000.0,
    val primeSafetyTimeoutMs: Long = 30_000L,
    /** Product-selected raw collection run; the later verification dose remains amount-based. */
    val calibrationRunDurationMs: Long = 3_000L
) {
    init {
        require(minMeasuredMl.isFinite() && minMeasuredMl > 0.0)
        require(maxMeasuredMl.isFinite() && maxMeasuredMl >= minMeasuredMl)
        require(primeSafetyTimeoutMs > 0L)
        require(calibrationRunDurationMs > 0L)
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
