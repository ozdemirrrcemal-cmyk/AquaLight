package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

internal fun DeviceRuntimeCommandOutcome<*>.dosingDiagnosticSummary(): String = when (this) {
    is DeviceRuntimeCommandOutcome.Success<*> ->
        "SUCCESS status=$statusCode gen=${generation.value} id=${messageId.shortDiagnosticId()}"
    is DeviceRuntimeCommandOutcome.NotConnected -> "NOT_CONNECTED"
    is DeviceRuntimeCommandOutcome.NotAuthenticated ->
        "NOT_AUTHENTICATED gen=${generation.value}"
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> "UNSUPPORTED_BY_DEVICE"
    is DeviceRuntimeCommandOutcome.SendFailed ->
        "SEND_FAILED gen=${generation.value} id=${messageId.shortDiagnosticId()}"
    is DeviceRuntimeCommandOutcome.Timeout ->
        "TIMEOUT ${timeoutMillis}ms gen=${generation.value} id=${messageId.shortDiagnosticId()}"
    is DeviceRuntimeCommandOutcome.FirmwareError ->
        "FIRMWARE_ERROR status=$statusCode code=$code field=$field gen=${generation.value} " +
            "id=${messageId.shortDiagnosticId()} msg=${message.take(DIAGNOSTIC_MESSAGE_LIMIT)}"
    is DeviceRuntimeCommandOutcome.ProtocolError ->
        "PROTOCOL_ERROR gen=${generation.value} id=${messageId.shortDiagnosticId()} " +
            "reason=${reason.take(DIAGNOSTIC_MESSAGE_LIMIT)}"
    is DeviceRuntimeCommandOutcome.Cancelled ->
        "CANCELLED gen=${generation.value} id=${messageId.shortDiagnosticId()} " +
            "reason=${reason.take(DIAGNOSTIC_MESSAGE_LIMIT)}"
}

private fun String.shortDiagnosticId(): String =
    if (length <= DIAGNOSTIC_ID_LIMIT) this else takeLast(DIAGNOSTIC_ID_LIMIT)

private const val DIAGNOSTIC_ID_LIMIT = 10
private const val DIAGNOSTIC_MESSAGE_LIMIT = 100
