package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSessionPhase
import com.aqua.aqualight.base.diagnostics.AppDiagnosticTrace

internal object DeviceDosingCalibrationDiagnosticTrace {
    fun action(
        action: DeviceDosingCalibrationAction,
        state: DeviceDosingCalibrationUiState
    ) {
        AppDiagnosticTrace.event(
            category = "ui",
            name = "dosing.calibration.action",
            "action" to action.diagnosticName,
            "step" to state.step.name,
            "busy" to state.isBusy
        )
    }

    fun requestExit(state: DeviceDosingCalibrationUiState) {
        lifecycleAction(action = "request_exit", state = state)
    }

    fun hostStopped(state: DeviceDosingCalibrationUiState) {
        lifecycleAction(action = "host_stopped", state = state)
    }

    private fun lifecycleAction(action: String, state: DeviceDosingCalibrationUiState) {
        AppDiagnosticTrace.event(
            category = "ui",
            name = "dosing.calibration.action",
            "action" to action,
            "step" to state.step.name,
            "busy" to state.isBusy
        )
    }

    fun result(
        slotId: String?,
        operation: DosingCalibrationOperation,
        result: DeviceDosingCalibrationResult,
        step: DeviceDosingCalibrationStep
    ) {
        AppDiagnosticTrace.event(
            category = "ui",
            name = "dosing.calibration.result",
            "slot" to slotId,
            "operation" to operation.diagnosticName,
            "result" to if (result is DeviceDosingCalibrationResult.Success) {
                "success"
            } else {
                "rejected"
            },
            "reason" to (result as? DeviceDosingCalibrationResult.Rejected)?.failure?.name,
            "step" to step.name
        )
    }

    fun stepTransition(
        slotId: String?,
        fromStep: DeviceDosingCalibrationStep,
        toStep: DeviceDosingCalibrationStep,
        operation: DosingCalibrationOperation?
    ) {
        if (fromStep == toStep) return
        AppDiagnosticTrace.event(
            category = "ui",
            name = "dosing.calibration.step",
            "slot" to slotId,
            "fromStep" to fromStep.name,
            "toStep" to toStep.name,
            "operation" to (operation?.diagnosticName ?: "snapshot")
        )
    }

    fun phaseTransition(
        slotId: String,
        fromPhase: DeviceDosingCalibrationSessionPhase?,
        toPhase: DeviceDosingCalibrationSessionPhase
    ) {
        if (fromPhase == toPhase) return
        AppDiagnosticTrace.event(
            category = "ui",
            name = "dosing.calibration.phase",
            "slot" to slotId,
            "fromPhase" to fromPhase?.name,
            "toPhase" to toPhase.name
        )
    }

    fun snapshotTransition(
        slotId: String,
        fromStep: DeviceDosingCalibrationStep,
        toStep: DeviceDosingCalibrationStep,
        fromPhase: DeviceDosingCalibrationSessionPhase?,
        toPhase: DeviceDosingCalibrationSessionPhase
    ) {
        stepTransition(slotId, fromStep, toStep, operation = null)
        phaseTransition(slotId, fromPhase, toPhase)
    }

    private val DeviceDosingCalibrationAction.diagnosticName: String
        get() = when (this) {
            is DeviceDosingCalibrationAction.DisplayNameChanged -> "display_name_changed"
            DeviceDosingCalibrationAction.SaveDisplayName -> "save_display_name"
            DeviceDosingCalibrationAction.PrimePressed -> "prime_pressed"
            DeviceDosingCalibrationAction.PrimeReleased -> "prime_released"
            DeviceDosingCalibrationAction.PrimeContinue -> "prime_continue"
            DeviceDosingCalibrationAction.StartCalibration -> "start_calibration"
            is DeviceDosingCalibrationAction.MeasuredMlChanged -> "measurement_changed"
            DeviceDosingCalibrationAction.SaveMeasurement -> "save_measurement"
            DeviceDosingCalibrationAction.StartVerification -> "start_verification"
            DeviceDosingCalibrationAction.AcceptVerification -> "accept_verification"
            DeviceDosingCalibrationAction.RejectVerification -> "reject_verification"
        }

    private val DosingCalibrationOperation.diagnosticName: String
        get() = when (this) {
            DosingCalibrationOperation.Refresh -> "refresh"
            DosingCalibrationOperation.PrimeStart -> "prime_start"
            DosingCalibrationOperation.PrimeStop -> "prime_stop"
            DosingCalibrationOperation.ContinueFromPrime -> "continue_from_prime"
            DosingCalibrationOperation.StartCalibration -> "start_calibration"
            is DosingCalibrationOperation.FinishMeasurement -> "finish_measurement"
            DosingCalibrationOperation.StartVerification -> "start_verification"
            is DosingCalibrationOperation.ConfirmVerification -> "confirm_verification"
            DosingCalibrationOperation.RejectVerification -> "reject_verification"
        }
}
