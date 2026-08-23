package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.base.diagnostics.AppDiagnosticTrace
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage

internal fun completeCorrelatedRuntimeReply(
    deviceUid: DeviceUid,
    generation: DeviceRuntimeConnectionGeneration,
    message: AqlWsIncomingMessage,
    pendingRequests: DeviceRuntimePendingRequestRegistry
): DeviceRuntimeCompletionDisposition {
    traceResponse(
        name = "arrived",
        deviceUid = deviceUid,
        generation = generation,
        message = message
    )
    val pending = pendingRequests.find(deviceUid, generation, message.id)
    val disposition = when {
        pending == null -> terminalOrUnmatched(
            deviceUid = deviceUid,
            generation = generation,
            messageId = message.id,
            pendingRequests = pendingRequests
        )
        pending.key.module != message.module || pending.key.action != message.action -> {
            traceResponse(
                "protocol_mismatch",
                deviceUid,
                generation,
                message,
                "expectedModule" to pending.key.module,
                "expectedAction" to pending.key.action
            )
            completeProtocolMismatch(pending, pendingRequests)
            DeviceRuntimeCompletionDisposition.COMPLETED
        }
        !pendingRequests.remove(pending) ->
            DeviceRuntimeCompletionDisposition.DUPLICATE_OR_LATE
        else -> {
            pending.deferred.complete(runtimeReplyOutcome(pending, message))
            DeviceRuntimeCompletionDisposition.COMPLETED
        }
    }
    traceResponse(
        "completed",
        deviceUid,
        generation,
        message,
        "disposition" to disposition.name
    )
    return disposition
}

private fun terminalOrUnmatched(
    deviceUid: DeviceUid,
    generation: DeviceRuntimeConnectionGeneration,
    messageId: String,
    pendingRequests: DeviceRuntimePendingRequestRegistry
): DeviceRuntimeCompletionDisposition = if (
    pendingRequests.isTerminal(deviceUid, generation, messageId)
) {
    DeviceRuntimeCompletionDisposition.DUPLICATE_OR_LATE
} else {
    DeviceRuntimeCompletionDisposition.UNMATCHED
}

private fun completeProtocolMismatch(
    pending: DeviceRuntimePendingRequestRegistry.Pending,
    pendingRequests: DeviceRuntimePendingRequestRegistry
) {
    val key = pending.key
    if (pendingRequests.remove(pending)) {
        pending.deferred.complete(
            DeviceRuntimeCommandOutcome.ProtocolError(
                deviceUid = key.deviceUid,
                module = key.module,
                action = key.action,
                messageId = key.messageId,
                generation = key.generation,
                reason = "Firmware response ID matched a different module/action."
            )
        )
    }
}

private fun runtimeReplyOutcome(
    pending: DeviceRuntimePendingRequestRegistry.Pending,
    message: AqlWsIncomingMessage
): DeviceRuntimeCommandOutcome<Any?> = when (message) {
    is AqlWsIncomingMessage.Response -> if (message.ok) {
        parseRuntimeSuccess(pending, message)
    } else {
        protocolError(
            pending,
            "Firmware response used the success envelope with ok=false."
        )
    }
    is AqlWsIncomingMessage.Error -> {
        val key = pending.key
        DeviceRuntimeCommandOutcome.FirmwareError(
            deviceUid = key.deviceUid,
            module = key.module,
            action = key.action,
            messageId = key.messageId,
            generation = key.generation,
            statusCode = message.statusCode,
            code = message.code,
            field = message.field,
            message = message.message
        )
    }
    is AqlWsIncomingMessage.Event -> error("Events are not pending command completions.")
}

private fun parseRuntimeSuccess(
    pending: DeviceRuntimePendingRequestRegistry.Pending,
    response: AqlWsIncomingMessage.Response
): DeviceRuntimeCommandOutcome<Any?> = try {
    val key = pending.key
    DeviceRuntimeCommandOutcome.Success(
        deviceUid = key.deviceUid,
        module = key.module,
        action = key.action,
        messageId = key.messageId,
        generation = key.generation,
        statusCode = response.statusCode,
        value = pending.parseSuccess(response)
    )
} catch (_: Throwable) {
    protocolError(
        pending,
        "Successful firmware response did not match the typed command contract."
    )
}

private fun protocolError(
    pending: DeviceRuntimePendingRequestRegistry.Pending,
    reason: String
): DeviceRuntimeCommandOutcome.ProtocolError {
    val key = pending.key
    return DeviceRuntimeCommandOutcome.ProtocolError(
        deviceUid = key.deviceUid,
        module = key.module,
        action = key.action,
        messageId = key.messageId,
        generation = key.generation,
        reason = reason
    )
}

private fun traceResponse(
    name: String,
    deviceUid: DeviceUid,
    generation: DeviceRuntimeConnectionGeneration,
    message: AqlWsIncomingMessage,
    vararg fields: Pair<String, Any?>
) {
    AppDiagnosticTrace.event(
        RUNTIME_RESPONSE_CATEGORY,
        name,
        "device" to AppDiagnosticTrace.deviceRef(deviceUid.value),
        "generation" to generation.value,
        "requestId" to message.id,
        "envelope" to message.javaClass.simpleName,
        "module" to message.module,
        "action" to message.action,
        *message.diagnosticFields(),
        *fields
    )
}

private fun AqlWsIncomingMessage.diagnosticFields(): Array<Pair<String, Any?>> = when (this) {
    is AqlWsIncomingMessage.Response -> arrayOf(
        "accepted" to ok,
        "statusCode" to statusCode
    )
    is AqlWsIncomingMessage.Error -> arrayOf(
        "statusCode" to statusCode,
        "errorCode" to code,
        "errorField" to field
    )
    is AqlWsIncomingMessage.Event -> emptyArray()
}

private const val RUNTIME_RESPONSE_CATEGORY = "runtime_response"
