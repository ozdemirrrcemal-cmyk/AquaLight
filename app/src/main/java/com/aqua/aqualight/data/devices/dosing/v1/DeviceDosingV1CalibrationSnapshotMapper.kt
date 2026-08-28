package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSessionPhase
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid

internal object DeviceDosingV1CalibrationSnapshotMapper {
    /**
     * Maps calibration runtime only from one coherent channel-status envelope.
     *
     * Calibration elapsed time combines [DeviceDosingV1Envelope.uptimeMillis] with the channel's
     * run-start event. A mutation ACK carries channel detail but no matching envelope uptime, so
     * active calibration projections must preserve the previous calibration snapshot until
     * authoritative global/channel/progress readback supplies a coherent time pair.
     */
    fun map(
        detail: DeviceDosingV1ChannelDetail,
        deviceUid: DeviceUid,
        slotId: String,
        envelope: DeviceDosingV1Envelope
    ): DeviceDosingCalibrationSnapshot = DeviceDosingCalibrationSnapshot(
        deviceUid = deviceUid.value,
        slotId = slotId,
        pumpCount = envelope.channelCount,
        channelNumber = detail.index + 1,
        channelTitle = detail.effectiveName,
        deviceUptimeMs = envelope.uptimeMillis,
        calibrated = detail.calibration.confirmed,
        lastCalibratedAt = detail.calibration.lastCalibratedAt,
        sessionPhase = calibrationPhase(detail.calibration.state),
        startedAtUptimeMs = calibrationStartedAtUptime(detail),
        durationMs = detail.calibration.durationMillis,
        measuredMl = detail.calibration.measuredMilliliters,
        pendingDoseMsPerMl = DeviceDosingV1AmountMapper.toExactLong(
            detail.calibration.pendingDoseMillisPerMilliliter
        ),
        verificationDoseStarted = detail.calibration.verificationDoseStarted,
        verificationDoseComplete = detail.calibration.verificationDoseComplete,
        verificationDoseRemainingMs = verificationRemainingMillis(detail),
        manualActive = detail.activeRun.active && detail.activeRun.remainingMillis > 0L
    )

    /**
     * Safely projects only the terminal calibration-confirm ACK.
     *
     * Unlike a running calibration, the committed idle state has no elapsed-time semantics. The
     * prior coherent snapshot therefore supplies identity/envelope fields while the ACK supplies
     * the durable calibration, name and terminal workflow state. Any weaker shape fails closed and
     * waits for normal authoritative readback.
     */
    fun projectCommittedConfirmation(
        current: DeviceDosingCalibrationSnapshot,
        detail: DeviceDosingV1ChannelDetail
    ): DeviceDosingCalibrationSnapshot? {
        val transitionValid = listOf(
            current.isCompletedPendingVerification(),
            detail.isTerminalConfirmedCalibration(),
            current.isCalibrationAdvancedBy(detail)
        ).all { valid -> valid }
        if (!transitionValid) return null

        return current.copy(
            channelNumber = detail.index + 1,
            channelTitle = detail.effectiveName,
            calibrated = true,
            lastCalibratedAt = detail.calibration.lastCalibratedAt,
            sessionPhase = DeviceDosingCalibrationSessionPhase.IDLE,
            startedAtUptimeMs = 0L,
            durationMs = 0L,
            measuredMl = 0.0,
            pendingDoseMsPerMl = 0L,
            verificationDoseStarted = false,
            verificationDoseComplete = false,
            verificationDoseRemainingMs = 0L,
            manualActive = false
        )
    }

    private fun DeviceDosingCalibrationSnapshot.isCompletedPendingVerification(): Boolean = listOf(
        sessionPhase == DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION,
        verificationDoseStarted,
        verificationDoseComplete,
        pendingDoseMsPerMl > 0L
    ).all { valid -> valid }

    private fun DeviceDosingV1ChannelDetail.isTerminalConfirmedCalibration(): Boolean = listOf(
        calibration.state.raw == CALIBRATION_IDLE,
        calibration.confirmed,
        calibration.doseMillisPerMilliliter > 0.0,
        calibration.lastCalibratedAt > 0L,
        calibration.pendingDoseMillisPerMilliliter == 0.0,
        !calibration.verificationDoseStarted,
        !calibration.verificationDoseComplete,
        !activeRun.active
    ).all { valid -> valid }

    private fun DeviceDosingCalibrationSnapshot.isCalibrationAdvancedBy(
        detail: DeviceDosingV1ChannelDetail
    ): Boolean = !calibrated || detail.calibration.lastCalibratedAt != lastCalibratedAt

    private fun calibrationStartedAtUptime(detail: DeviceDosingV1ChannelDetail): Long =
        detail.lastRuntimeEvent.occurredAtMillis.takeIf {
            detail.lastRuntimeEvent.valid &&
                detail.lastRuntimeEvent.kind.raw == "runStarted" &&
                detail.lastRuntimeEvent.source.raw in CALIBRATION_RUN_SOURCES
        } ?: 0L

    private fun verificationRemainingMillis(detail: DeviceDosingV1ChannelDetail): Long =
        detail.activeRun.remainingMillis.takeIf {
            detail.activeRun.active && detail.activeRun.source.raw == "verification"
        } ?: 0L

    private fun calibrationPhase(
        value: DeviceDosingV1WireValue
    ): DeviceDosingCalibrationSessionPhase = when (value.raw) {
        CALIBRATION_IDLE -> DeviceDosingCalibrationSessionPhase.IDLE
        "running" -> DeviceDosingCalibrationSessionPhase.RUNNING
        "pendingVerification" -> DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION
        else -> error("Unknown firmware Dosing calibration state.")
    }

    private const val CALIBRATION_IDLE = "idle"
    private val CALIBRATION_RUN_SOURCES = setOf("calibration", "verification")
}
