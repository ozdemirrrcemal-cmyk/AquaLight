package com.aqua.aqualight.data.devices.light.dashboard

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlFailure
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlMode
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightModeDiagnostic
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

internal fun DeviceRuntimeCommandOutcome<*>.toModeDiagnostic(
    stage: String,
    requestedMode: DeviceLightControlMode,
    expectedWireMode: String,
    actualWireMode: String? = null,
    failure: DeviceLightControlFailure? = null
): DeviceLightModeDiagnostic {
    val details = diagnosticBase(
        requestedMode = requestedMode,
        expectedWireMode = expectedWireMode,
        actualWireMode = actualWireMode,
        failure = failure
    )
    appendDiagnosticOutcomeDetails(details)
    return DeviceLightModeDiagnostic(
        stage = stage,
        requestedMode = requestedMode,
        details = details
    )
}

private fun DeviceRuntimeCommandOutcome<*>.diagnosticBase(
    requestedMode: DeviceLightControlMode,
    expectedWireMode: String,
    actualWireMode: String?,
    failure: DeviceLightControlFailure?
): MutableList<String> = mutableListOf(
    "requestedMode=" + requestedMode,
    "expectedWireMode=" + expectedWireMode,
    "module=" + module,
    "action=" + action,
    "outcome=" + diagnosticOutcomeName()
).apply {
    actualWireMode?.let { add("actualWireMode=" + it) }
    failure?.let { add("failure=" + it) }
}

private fun DeviceRuntimeCommandOutcome<*>.appendDiagnosticOutcomeDetails(
    details: MutableList<String>
) {
    when (this) {
        is DeviceRuntimeCommandOutcome.Success -> details.addAll(
            listOf(
                "messageId=" + messageId,
                "generation=" + generation.value,
                "statusCode=" + statusCode
            )
        )
        is DeviceRuntimeCommandOutcome.NotConnected -> Unit
        is DeviceRuntimeCommandOutcome.NotAuthenticated ->
            details += "generation=" + generation.value
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> Unit
        is DeviceRuntimeCommandOutcome.SendFailed -> details.addAll(
            listOf(
                "messageId=" + messageId,
                "generation=" + generation.value
            )
        )
        is DeviceRuntimeCommandOutcome.Timeout -> details.addAll(
            listOf(
                "messageId=" + messageId,
                "generation=" + generation.value,
                "timeoutMillis=" + timeoutMillis
            )
        )
        is DeviceRuntimeCommandOutcome.FirmwareError -> details.addAll(
            listOf(
                "messageId=" + messageId,
                "generation=" + generation.value,
                "statusCode=" + statusCode,
                "firmwareCode=" + code,
                "firmwareField=" + field,
                "firmwareMessage=" + message,
                "firmwareData=" + structuredDataJson
            )
        )
        is DeviceRuntimeCommandOutcome.ProtocolError -> details.addAll(
            listOf(
                "messageId=" + messageId,
                "generation=" + generation.value,
                "protocolReason=" + reason
            )
        )
        is DeviceRuntimeCommandOutcome.Cancelled -> details.addAll(
            listOf(
                "messageId=" + messageId,
                "generation=" + generation.value,
                "cancelReason=" + reason
            )
        )
    }
}

private fun DeviceRuntimeCommandOutcome<*>.diagnosticOutcomeName(): String = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> "SUCCESS"
    is DeviceRuntimeCommandOutcome.NotConnected -> "NOT_CONNECTED"
    is DeviceRuntimeCommandOutcome.NotAuthenticated -> "NOT_AUTHENTICATED"
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> "UNSUPPORTED_BY_DEVICE"
    is DeviceRuntimeCommandOutcome.SendFailed -> "SEND_FAILED"
    is DeviceRuntimeCommandOutcome.Timeout -> "TIMEOUT"
    is DeviceRuntimeCommandOutcome.FirmwareError -> "FIRMWARE_ERROR"
    is DeviceRuntimeCommandOutcome.ProtocolError -> "PROTOCOL_ERROR"
    is DeviceRuntimeCommandOutcome.Cancelled -> "CANCELLED"
}
