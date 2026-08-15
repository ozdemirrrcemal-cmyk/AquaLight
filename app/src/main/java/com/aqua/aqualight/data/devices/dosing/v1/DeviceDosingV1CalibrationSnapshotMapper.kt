package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSessionPhase
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid

internal object DeviceDosingV1CalibrationSnapshotMapper {
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
        manualActive = detail.activeRun.active
    )

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
        "idle" -> DeviceDosingCalibrationSessionPhase.IDLE
        "running" -> DeviceDosingCalibrationSessionPhase.RUNNING
        "pendingVerification" -> DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION
        else -> error("Unknown firmware Dosing calibration state.")
    }

    private val CALIBRATION_RUN_SOURCES = setOf("calibration", "verification")
}
