package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSessionPhase
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDestination
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationTarget

internal sealed interface DosingCalibrationCountdown {
    data class CalibrationRun(val durationMs: Long) : DosingCalibrationCountdown
    data class Verification(val durationMs: Long) : DosingCalibrationCountdown
}

internal data class DosingCalibrationSnapshotPresentation(
    val state: DeviceDosingCalibrationUiState,
    val countdown: DosingCalibrationCountdown? = null
)

internal fun reduceDosingCalibrationSnapshot(
    snapshot: DeviceDosingCalibrationSnapshot,
    current: DeviceDosingCalibrationUiState,
    hasLocalProgress: Boolean
): DosingCalibrationSnapshotPresentation {
    val remainingMs = snapshot.remainingOperationMs()
    val recoveryStep = snapshot.recoveryStep(current.step, hasLocalProgress)
    val state = current
        .updateProgress { progress ->
            progress.copy(
                isLoading = false,
                isBusy = remainingMs > 0L,
                step = recoveryStep,
                isPumpActive = snapshot.manualActive,
                remainingMs = remainingMs,
                candidateDoseMsPerMl = snapshot.pendingDoseMsPerMl.takeIf { it > 0L }
            )
        }
        .updateChannel { channel ->
            channel.copy(
                pumpCount = snapshot.pumpCount,
                channelNumber = snapshot.channelNumber,
                channelTitle = snapshot.channelTitle
            )
        }
        .updateInput { input -> input.copy(displayName = snapshot.channelTitle) }
        .copy(error = null)
    return DosingCalibrationSnapshotPresentation(
        state = state,
        countdown = snapshot.countdownOrNull(remainingMs)
    )
}

internal fun DeviceDosingCalibrationSnapshot.shouldAutoComplete(
    isRecalibration: Boolean,
    hasLocalProgress: Boolean,
    completionEmitted: Boolean
): Boolean = sessionPhase == DeviceDosingCalibrationSessionPhase.IDLE &&
    calibrated &&
    !isRecalibration &&
    !hasLocalProgress &&
    !completionEmitted

internal fun DeviceDosingCalibrationSnapshot.toDetailTarget() =
    DeviceDosingChannelNavigationTarget(
        deviceUid = deviceUid,
        slotId = slotId,
        pumpCount = pumpCount,
        channelNumber = channelNumber,
        channelTitle = channelTitle,
        lastCalibratedAtEpochSeconds = lastCalibratedAt,
        destination = DeviceDosingChannelDestination.DETAIL
    )

private fun DeviceDosingCalibrationSnapshot.recoveryStep(
    currentStep: DeviceDosingCalibrationStep,
    hasLocalProgress: Boolean
): DeviceDosingCalibrationStep = when (sessionPhase) {
    DeviceDosingCalibrationSessionPhase.IDLE ->
        if (hasLocalProgress) currentStep else DeviceDosingCalibrationStep.NAME
    DeviceDosingCalibrationSessionPhase.RUNNING ->
        if (runningRemainingMs() > 0L) {
            DeviceDosingCalibrationStep.CALIBRATION_RUN
        } else {
            DeviceDosingCalibrationStep.MEASUREMENT
        }
    DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION ->
        if (verificationDoseComplete) {
            DeviceDosingCalibrationStep.CONFIRMATION
        } else {
            DeviceDosingCalibrationStep.VERIFICATION
        }
}

private fun DeviceDosingCalibrationSnapshot.remainingOperationMs(): Long = when {
    sessionPhase == DeviceDosingCalibrationSessionPhase.RUNNING -> runningRemainingMs()
    verificationDoseStarted && !verificationDoseComplete -> verificationDoseRemainingMs
    else -> 0L
}

private fun DeviceDosingCalibrationSnapshot.countdownOrNull(
    remainingMs: Long
): DosingCalibrationCountdown? = when {
    sessionPhase == DeviceDosingCalibrationSessionPhase.RUNNING && remainingMs > 0L ->
        DosingCalibrationCountdown.CalibrationRun(remainingMs)
    sessionPhase == DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION &&
        verificationDoseStarted &&
        !verificationDoseComplete &&
        verificationDoseRemainingMs > 0L ->
        DosingCalibrationCountdown.Verification(verificationDoseRemainingMs)
    else -> null
}

private fun DeviceDosingCalibrationSnapshot.runningRemainingMs(): Long {
    val elapsedMs = (deviceUptimeMs - startedAtUptimeMs).and(UINT32_MASK)
    return (durationMs - elapsedMs).coerceAtLeast(0L)
}

private const val UINT32_MASK = 0xFFFF_FFFFL
