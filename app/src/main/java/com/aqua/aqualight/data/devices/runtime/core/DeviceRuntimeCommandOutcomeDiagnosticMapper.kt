package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.application.devices.DeviceOperationCommandDiagnostic
import com.aqua.aqualight.application.devices.DeviceOperationDiagnostic
import com.aqua.aqualight.application.devices.DeviceOperationResponseDiagnostic
import com.aqua.aqualight.application.devices.DeviceOperationRuntimeStateDiagnostic

internal fun DeviceRuntimeCommandOutcome<*>.toOperationDiagnostic(
    stage: String,
    outcomeOverride: String? = null,
    detailOverride: String? = null,
    runtimeState: DeviceOperationRuntimeStateDiagnostic? = null
): DeviceOperationDiagnostic = DeviceOperationDiagnostic(
    stage = stage,
    outcome = outcomeOverride ?: diagnosticOutcome(),
    command = DeviceOperationCommandDiagnostic(
        deviceUid = deviceUid.value,
        module = module,
        action = action,
        messageId = diagnosticMessageId(),
        connectionGeneration = diagnosticGeneration(),
        timeoutMillis = (this as? DeviceRuntimeCommandOutcome.Timeout)?.timeoutMillis
    ),
    response = diagnosticResponse(),
    runtimeState = runtimeState,
    detail = detailOverride ?: diagnosticDetail()
)

private fun DeviceRuntimeCommandOutcome<*>.diagnosticOutcome(): String = when (this) {
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

private fun DeviceRuntimeCommandOutcome<*>.diagnosticMessageId(): String? = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> messageId
    is DeviceRuntimeCommandOutcome.SendFailed -> messageId
    is DeviceRuntimeCommandOutcome.Timeout -> messageId
    is DeviceRuntimeCommandOutcome.FirmwareError -> messageId
    is DeviceRuntimeCommandOutcome.ProtocolError -> messageId
    is DeviceRuntimeCommandOutcome.Cancelled -> messageId
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated,
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> null
}

private fun DeviceRuntimeCommandOutcome<*>.diagnosticGeneration(): Long? = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> generation.value
    is DeviceRuntimeCommandOutcome.NotAuthenticated -> generation.value
    is DeviceRuntimeCommandOutcome.SendFailed -> generation.value
    is DeviceRuntimeCommandOutcome.Timeout -> generation.value
    is DeviceRuntimeCommandOutcome.FirmwareError -> generation.value
    is DeviceRuntimeCommandOutcome.ProtocolError -> generation.value
    is DeviceRuntimeCommandOutcome.Cancelled -> generation.value
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> null
}

private fun DeviceRuntimeCommandOutcome<*>.diagnosticResponse(): DeviceOperationResponseDiagnostic? =
    when (this) {
        is DeviceRuntimeCommandOutcome.Success -> DeviceOperationResponseDiagnostic(
            statusCode = statusCode
        )
        is DeviceRuntimeCommandOutcome.FirmwareError -> DeviceOperationResponseDiagnostic(
            statusCode = statusCode,
            firmwareCode = code,
            firmwareField = field,
            firmwareMessage = message
        )
        is DeviceRuntimeCommandOutcome.NotConnected,
        is DeviceRuntimeCommandOutcome.NotAuthenticated,
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice,
        is DeviceRuntimeCommandOutcome.SendFailed,
        is DeviceRuntimeCommandOutcome.Timeout,
        is DeviceRuntimeCommandOutcome.ProtocolError,
        is DeviceRuntimeCommandOutcome.Cancelled -> null
    }

private fun DeviceRuntimeCommandOutcome<*>.diagnosticDetail(): String? = when (this) {
    is DeviceRuntimeCommandOutcome.ProtocolError -> reason
    is DeviceRuntimeCommandOutcome.Cancelled -> reason
    is DeviceRuntimeCommandOutcome.Success,
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated,
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice,
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.FirmwareError -> null
}
