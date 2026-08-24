package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.debug.dosing.DosingDebugTrace

internal fun completeCorrelatedRuntimeReply(
    deviceUid: DeviceUid,
    generation: DeviceRuntimeConnectionGeneration,
    message: AqlWsIncomingMessage,
    pendingRequests: DeviceRuntimePendingRequestRegistry
): DeviceRuntimeCompletionDisposition {
    val dosing = DosingDebugTrace.isDosingModule(message.module)
    if (dosing) {
        val envelope = when (message) {
            is AqlWsIncomingMessage.Response -> "response ok=${message.ok} status=${message.statusCode}"
            is AqlWsIncomingMessage.Error ->
                "error status=${message.statusCode} code=${message.code} field=${message.field}"
            is AqlWsIncomingMessage.Event -> "event"
        }
        DosingDebugTrace.log(
            "WIRE",
            "RECV device=${DosingDebugTrace.shortDevice(deviceUid.value)} gen=${generation.value} " +
                "id=${message.id} ${message.module}.${message.action} $envelope " +
                "data=${DosingDebugTrace.compactJson(message.data)}"
        )
    }

    val pending = pendingRequests.find(deviceUid, generation, message.id)
    val disposition = when {
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

    if (dosing) {
        DosingDebugTrace.log(
            "WIRE",
            "CORRELATE id=${message.id} ${message.module}.${message.action} disposition=$disposition"
        )
    }
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
} catch (error: Throwable) {
    if (DosingDebugTrace.isDosingModule(pending.key.module)) {
        DosingDebugTrace.log(
            "PARSE",
            "FAIL id=${pending.key.messageId} ${pending.key.module}.${pending.key.action} " +
                "${error::class.java.simpleName}: " +
                DosingDebugTrace.compact(error.message.orEmpty(), TRACE_PARSE_MESSAGE_CHARS)
        )
    }
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

private const val TRACE_PARSE_MESSAGE_CHARS = 300
