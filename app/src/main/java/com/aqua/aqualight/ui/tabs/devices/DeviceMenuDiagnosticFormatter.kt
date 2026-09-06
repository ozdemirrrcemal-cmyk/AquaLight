package com.aqua.aqualight.ui.tabs.devices

import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceOperationDiagnostic

internal fun formatDeviceMenuDiagnostic(
    reason: DeviceMenuUnavailableReason,
    diagnostic: DeviceOperationDiagnostic?
): String = buildList {
    add("MENU OPEN DIAGNOSTIC")
    add("menuReason=${reason.name}")
    if (diagnostic == null) {
        add("stage=MENU_ACCESS_OR_PREPARATION")
        add("outcome=NO_LOWER_LEVEL_DIAGNOSTIC")
        return@buildList
    }

    add("stage=${diagnostic.stage}")
    add("outcome=${diagnostic.outcome}")
    diagnostic.command?.let { command ->
        add("deviceUid=${command.deviceUid.sanitizedDiagnosticValue()}")
        add("command=${command.module}.${command.action}")
        command.messageId?.let { add("messageId=${it.sanitizedDiagnosticValue()}") }
        command.connectionGeneration?.let { add("requestGeneration=$it") }
        command.timeoutMillis?.let { add("timeoutMs=$it") }
    }
    diagnostic.response?.let { response ->
        response.statusCode?.let { add("statusCode=$it") }
        response.firmwareCode?.let { add("firmwareCode=${it.sanitizedDiagnosticValue()}") }
        response.firmwareField?.let { add("firmwareField=${it.sanitizedDiagnosticValue()}") }
        response.firmwareMessage?.let {
            add("firmwareMessage=${it.sanitizedDiagnosticValue()}")
        }
    }
    diagnostic.runtimeState?.let { runtime ->
        runtime.connectionGeneration?.let { add("stateGeneration=$it") }
        runtime.authoritative?.let { add("stateAuthoritative=$it") }
    }
    diagnostic.detail?.let { add("detail=${it.sanitizedDiagnosticValue()}") }
}.joinToString(separator = "\n")

private fun String.sanitizedDiagnosticValue(): String =
    replace('\n', ' ').replace('\r', ' ').take(MAX_DIAGNOSTIC_VALUE_LENGTH)

private const val MAX_DIAGNOSTIC_VALUE_LENGTH = 180
