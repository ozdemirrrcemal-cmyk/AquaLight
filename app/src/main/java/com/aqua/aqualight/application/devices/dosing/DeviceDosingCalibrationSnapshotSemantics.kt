package com.aqua.aqualight.application.devices.dosing

internal val DeviceDosingCalibrationSnapshot.hasActiveCalibrationSession: Boolean
    get() = sessionPhase != DeviceDosingCalibrationSessionPhase.IDLE

/**
 * Proves that a pending, completed verification became one committed calibration transaction.
 *
 * The display-name draft is accepted only as part of the firmware confirmation transaction; it is
 * never treated as persisted identity before the central state owner exposes a validated committed
 * transition from the firmware ACK or a later authoritative readback.
 */
internal fun DeviceDosingCalibrationSnapshot.isCommittedCalibrationTransitionFrom(
    previous: DeviceDosingCalibrationSnapshot?,
    expectedDisplayName: String
): Boolean {
    val pending = previous ?: return false
    val pendingVerificationComplete =
        pending.deviceUid == deviceUid &&
            pending.slotId == slotId &&
            pending.sessionPhase == DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION &&
            pending.verificationDoseStarted &&
            pending.verificationDoseComplete &&
            pending.pendingDoseMsPerMl > 0L
    val calibrationAdvanced = !pending.calibrated || lastCalibratedAt != pending.lastCalibratedAt

    return pendingVerificationComplete &&
        sessionPhase == DeviceDosingCalibrationSessionPhase.IDLE &&
        calibrated &&
        !manualActive &&
        lastCalibratedAt > 0L &&
        channelTitle == expectedDisplayName &&
        calibrationAdvanced
}
