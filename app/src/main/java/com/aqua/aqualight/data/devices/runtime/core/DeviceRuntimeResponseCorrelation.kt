package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage

internal fun completeCorrelatedRuntimeReply(
    deviceUid: DeviceUid,
    generation: DeviceRuntimeConnectionGeneration,
    message: AqlWsIncomingMessage,
    pendingRequests: DeviceRuntimePendingRequestRegistry
): DeviceRuntimeCompletionDisposition {
    DeviceRuntimeOtaDiagnostics.recordIncoming(deviceUid, generation, message)
    val pending = pendingRequests.find(deviceUid, generation, message.id)
    return when {
        pending == null -> terminalOrUnmatched(
            deviceUid = deviceUid,
            generation = generation,
            messageId = message.id,
            pendingRequests = pendingRequests
        )
        pending.key.module != message.module || pending.key.action != message.action -> {
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
} catch (error: Throwable) {
    val key = pending.key
    DeviceRuntimeOtaDiagnostics.recordParserFailure(
        deviceUid = key.deviceUid,
        generation = key.generation,
        messageId = key.messageId,
        error = error
    )
    protocolError(
        pending,
        buildString {
            append("Successful firmware response did not match the typed command contract")
            error.message?.takeIf(String::isNotBlank)?.let { message ->
                append(": ")
                append(message.take(MAX_PROTOCOL_DIAGNOSTIC_CHARS))
            }
            append('.')
        }
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

private const val MAX_PROTOCOL_DIAGNOSTIC_CHARS = 480
