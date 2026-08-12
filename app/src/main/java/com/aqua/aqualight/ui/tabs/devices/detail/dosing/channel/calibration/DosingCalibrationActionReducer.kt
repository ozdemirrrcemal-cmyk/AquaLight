package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

internal sealed interface DosingCalibrationOperation {
    data object Refresh : DosingCalibrationOperation
    data class SaveDisplayName(val name: String) : DosingCalibrationOperation
    data object PrimeStart : DosingCalibrationOperation
    data object PrimeStop : DosingCalibrationOperation
    data object ContinueFromPrime : DosingCalibrationOperation
    data object StartCalibration : DosingCalibrationOperation
    data class FinishMeasurement(val measuredMl: Double) : DosingCalibrationOperation
    data object StartVerification : DosingCalibrationOperation
    data object ConfirmVerification : DosingCalibrationOperation
    data object RejectVerification : DosingCalibrationOperation
}

internal enum class DosingCalibrationPrimeDirective {
    START,
    STOP
}

internal data class DosingCalibrationActionDecision(
    val state: DeviceDosingCalibrationUiState,
    val operation: DosingCalibrationOperation? = null,
    val primeDirective: DosingCalibrationPrimeDirective? = null
)

internal fun reduceDosingCalibrationAction(
    state: DeviceDosingCalibrationUiState,
    primeRequested: Boolean,
    action: DeviceDosingCalibrationAction
): DosingCalibrationActionDecision = when (action) {
    is DeviceDosingCalibrationAction.DisplayNameChanged -> DosingCalibrationActionDecision(
        state = if (state.isBusy) state else state
            .updateInput { input ->
                input.copy(displayName = action.value.take(MAX_DISPLAY_NAME_CHARACTERS))
            }
            .copy(error = null)
    )
    DeviceDosingCalibrationAction.SaveDisplayName -> saveDisplayNameDecision(state)
    DeviceDosingCalibrationAction.PrimePressed -> primePressedDecision(state, primeRequested)
    DeviceDosingCalibrationAction.PrimeReleased -> primeReleasedDecision(state, primeRequested)
    DeviceDosingCalibrationAction.PrimeContinue -> operationDecision(
        state = state,
        operation = DosingCalibrationOperation.ContinueFromPrime,
        nextState = state
            .updateProgress { progress ->
                progress.copy(isBusy = true, isPumpActive = false)
            }
            .copy(error = null)
    )
    DeviceDosingCalibrationAction.StartCalibration -> busyOperationDecision(
        state,
        DosingCalibrationOperation.StartCalibration
    )
    is DeviceDosingCalibrationAction.MeasuredMlChanged -> DosingCalibrationActionDecision(
        state = if (state.isBusy) state else state
            .updateInput { input -> input.copy(measuredMl = sanitizeMeasurement(action.value)) }
            .copy(error = null)
    )
    DeviceDosingCalibrationAction.SaveMeasurement -> saveMeasurementDecision(state)
    DeviceDosingCalibrationAction.StartVerification -> busyOperationDecision(
        state,
        DosingCalibrationOperation.StartVerification
    )
    DeviceDosingCalibrationAction.AcceptVerification -> busyOperationDecision(
        state,
        DosingCalibrationOperation.ConfirmVerification
    )
    DeviceDosingCalibrationAction.RejectVerification -> busyOperationDecision(
        state,
        DosingCalibrationOperation.RejectVerification
    )
}

private fun saveDisplayNameDecision(
    state: DeviceDosingCalibrationUiState
): DosingCalibrationActionDecision {
    val name = state.displayName.trim()
    return if (name.isBlank()) {
        DosingCalibrationActionDecision(
            state = state.copy(error = DeviceDosingCalibrationError.INVALID_NAME)
        )
    } else {
        busyOperationDecision(state, DosingCalibrationOperation.SaveDisplayName(name))
    }
}

private fun primePressedDecision(
    state: DeviceDosingCalibrationUiState,
    primeRequested: Boolean
): DosingCalibrationActionDecision {
    val canStart = state.step == DeviceDosingCalibrationStep.PRIME && !primeRequested
    return if (!canStart) {
        DosingCalibrationActionDecision(state)
    } else {
        DosingCalibrationActionDecision(
            state = state
                .updateProgress { progress -> progress.copy(isPumpActive = true) }
                .copy(error = null),
            operation = DosingCalibrationOperation.PrimeStart,
            primeDirective = DosingCalibrationPrimeDirective.START
        )
    }
}

private fun primeReleasedDecision(
    state: DeviceDosingCalibrationUiState,
    primeRequested: Boolean
): DosingCalibrationActionDecision {
    val canStop = primeRequested || state.isPumpActive
    return if (!canStop) {
        DosingCalibrationActionDecision(state)
    } else {
        DosingCalibrationActionDecision(
            state = state.updateProgress { progress -> progress.copy(isPumpActive = false) },
            operation = DosingCalibrationOperation.PrimeStop,
            primeDirective = DosingCalibrationPrimeDirective.STOP
        )
    }
}

private fun saveMeasurementDecision(
    state: DeviceDosingCalibrationUiState
): DosingCalibrationActionDecision {
    val measuredMl = parseMeasurement(state.measuredMl)
    return if (measuredMl == null || measuredMl !in MIN_MEASURED_ML..MAX_MEASURED_ML) {
        DosingCalibrationActionDecision(
            state = state.copy(error = DeviceDosingCalibrationError.INVALID_MEASUREMENT)
        )
    } else {
        busyOperationDecision(state, DosingCalibrationOperation.FinishMeasurement(measuredMl))
    }
}

private fun busyOperationDecision(
    state: DeviceDosingCalibrationUiState,
    operation: DosingCalibrationOperation
): DosingCalibrationActionDecision = operationDecision(
    state = state,
    operation = operation,
    nextState = state
        .updateProgress { progress -> progress.copy(isBusy = true) }
        .copy(error = null)
)

private fun operationDecision(
    state: DeviceDosingCalibrationUiState,
    operation: DosingCalibrationOperation,
    nextState: DeviceDosingCalibrationUiState
): DosingCalibrationActionDecision = if (state.isBusy) {
    DosingCalibrationActionDecision(state)
} else {
    DosingCalibrationActionDecision(state = nextState, operation = operation)
}

private fun sanitizeMeasurement(value: String): String = value
    .filter { character -> character.isDigit() || character == '.' || character == ',' }
    .take(MAX_MEASUREMENT_CHARACTERS)

private fun parseMeasurement(value: String): Double? = value
    .trim()
    .replace(',', '.')
    .toDoubleOrNull()
    ?.takeIf(Double::isFinite)

internal const val MAX_DISPLAY_NAME_CHARACTERS = 32
internal const val MAX_MEASUREMENT_CHARACTERS = 8
internal const val MIN_MEASURED_ML = 0.05
internal const val MAX_MEASURED_ML = 1_000.0
