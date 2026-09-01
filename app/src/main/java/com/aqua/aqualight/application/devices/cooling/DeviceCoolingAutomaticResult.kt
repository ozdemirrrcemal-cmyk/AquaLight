package com.aqua.aqualight.application.devices.cooling

/** Product-level failure semantics for Automatic Cooling operations. */
sealed interface DeviceCoolingAutomaticFailure {
    data object Unavailable : DeviceCoolingAutomaticFailure
    data object Unsupported : DeviceCoolingAutomaticFailure
    data object ReadOnly : DeviceCoolingAutomaticFailure
    data object NotConnected : DeviceCoolingAutomaticFailure
    data object InvalidConfiguration : DeviceCoolingAutomaticFailure
    data object TemporaryFailure : DeviceCoolingAutomaticFailure
    data object Rejected : DeviceCoolingAutomaticFailure
}

/** Typed result for Automatic Cooling commands that do not return a payload. */
sealed interface DeviceCoolingAutomaticCommandResult {
    data object Success : DeviceCoolingAutomaticCommandResult

    data class Failed(
        val failure: DeviceCoolingAutomaticFailure
    ) : DeviceCoolingAutomaticCommandResult
}
