package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import com.aqua.aqualight.debug.dosing.DosingDebugTrace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

internal suspend fun <T> executeCorrelatedRuntimeRequest(
    deviceUid: DeviceUid,
    command: DeviceRuntimeCommand<T>,
    timeoutMillis: Long,
    context: DeviceRuntimeExecutionContext
): DeviceRuntimeCommandOutcome<T> = when (
    val preparation = prepareRuntimeRequest(
        deviceUid = deviceUid,
        command = command,
        context = context
    )
) {
    is DeviceRuntimeRequestPreparation.Rejected -> preparation.outcome.also { outcome ->
        if (DosingDebugTrace.isDosingModule(command.module)) {
            DosingDebugTrace.log(
                "CMD",
                "REJECT device=${DosingDebugTrace.shortDevice(deviceUid.value)} " +
                    "${command.module}.${command.action} ${outcome.traceSummary()}"
            )
        }
    }
    is DeviceRuntimeRequestPreparation.Ready -> sendAndAwaitRuntimeRequest(
        preparation = preparation,
        timeoutMillis = timeoutMillis,
        pendingRequests = context.pendingRequests
    )
}

private sealed interface DeviceRuntimeRequestPreparation<out T> {
    data class Ready(
        val session: DeviceRuntimeCommandSession,
        val message: AqlWsOutgoingMessage.Command,
        val pending: DeviceRuntimePendingRequestRegistry.Pending
    ) : DeviceRuntimeRequestPreparation<Nothing>

    data class Rejected<T>(
        val outcome: DeviceRuntimeCommandOutcome<T>
    ) : DeviceRuntimeRequestPreparation<T>
}

private fun <T> prepareRuntimeRequest(
    deviceUid: DeviceUid,
    command: DeviceRuntimeCommand<T>,
    context: DeviceRuntimeExecutionContext
): DeviceRuntimeRequestPreparation<T> {
    val session = context.sessionProvider(deviceUid)
    return when {
        session == null -> DeviceRuntimeRequestPreparation.Rejected(
            DeviceRuntimeCommandOutcome.NotConnected(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action
            )
        )
        !session.authenticated -> DeviceRuntimeRequestPreparation.Rejected(
            DeviceRuntimeCommandOutcome.NotAuthenticated(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                generation = session.generation
            )
        )
        !context.supportChecker(deviceUid, command.module, command.action) ->
            DeviceRuntimeRequestPreparation.Rejected(
                DeviceRuntimeCommandOutcome.UnsupportedByDevice(
                    deviceUid = deviceUid,
                    module = command.module,
                    action = command.action
                )
            )
        else -> createReadyRuntimeRequest(
            deviceUid = deviceUid,
            session = session,
            command = command,
            pendingRequests = context.pendingRequests
        )
    }
}

private fun <T> createReadyRuntimeRequest(
    deviceUid: DeviceUid,
    session: DeviceRuntimeCommandSession,
    command: DeviceRuntimeCommand<T>,
    pendingRequests: DeviceRuntimePendingRequestRegistry
): DeviceRuntimeRequestPreparation<T> = try {
    val message = AqlWsOutgoingMessage.Command(
        module = command.module,
        action = command.action,
        data = JSONObject(command.encodeData().toString())
    )
    val key = DeviceRuntimeCorrelationKey(
        deviceUid = deviceUid,
        generation = session.generation,
        messageId = message.id,
        module = command.module,
        action = command.action
    )
    val pending = pendingRequests.register(key) { response ->
        command.parseSuccess(response)
    }
    DeviceRuntimeRequestPreparation.Ready(
        session = session,
        message = message,
        pending = pending
    )
} catch (_: Throwable) {
    DeviceRuntimeRequestPreparation.Rejected(
        DeviceRuntimeCommandOutcome.ProtocolError(
            deviceUid = deviceUid,
            module = command.module,
            action = command.action,
            messageId = "",
            generation = session.generation,
            reason = "Command request did not satisfy its typed serialization contract."
        )
    )
}

private suspend fun <T> sendAndAwaitRuntimeRequest(
    preparation: DeviceRuntimeRequestPreparation.Ready,
    timeoutMillis: Long,
    pendingRequests: DeviceRuntimePendingRequestRegistry
): DeviceRuntimeCommandOutcome<T> {
    val dosing = DosingDebugTrace.isDosingModule(preparation.message.module)
    if (dosing) {
        DosingDebugTrace.log(
            "CMD",
            "SEND device=${DosingDebugTrace.shortDevice(preparation.session.deviceUid.value)} " +
                "gen=${preparation.session.generation.value} id=${preparation.message.id} " +
                "${preparation.message.module}.${preparation.message.action} timeout=${timeoutMillis}ms " +
                "data=${DosingDebugTrace.compactJson(preparation.message.data)}"
        )
    }

    val sent = runCatching {
        preparation.session.send(preparation.message)
    }.getOrDefault(false)

    val outcome: DeviceRuntimeCommandOutcome<T> = if (sent) {
        awaitRuntimeRequest(preparation, timeoutMillis, pendingRequests)
    } else {
        pendingRequests.remove(preparation.pending, rememberTerminal = false)
        preparation.pending.deferred.cancel()
        DeviceRuntimeCommandOutcome.SendFailed(
            deviceUid = preparation.session.deviceUid,
            module = preparation.message.module,
            action = preparation.message.action,
            messageId = preparation.message.id,
            generation = preparation.session.generation
        )
    }

    if (dosing) {
        DosingDebugTrace.log(
            "CMD",
            "DONE device=${DosingDebugTrace.shortDevice(preparation.session.deviceUid.value)} " +
                "gen=${preparation.session.generation.value} id=${preparation.message.id} " +
                "${preparation.message.module}.${preparation.message.action} ${outcome.traceSummary()}"
        )
    }
    return outcome
}

private suspend fun <T> awaitRuntimeRequest(
    preparation: DeviceRuntimeRequestPreparation.Ready,
    timeoutMillis: Long,
    pendingRequests: DeviceRuntimePendingRequestRegistry
): DeviceRuntimeCommandOutcome<T> = try {
    val completed = withTimeoutOrNull(timeoutMillis) {
        preparation.pending.deferred.await()
    }
    when {
        completed != null -> completed.typedOutcome()
        pendingRequests.remove(preparation.pending) -> DeviceRuntimeCommandOutcome.Timeout(
            deviceUid = preparation.session.deviceUid,
            module = preparation.message.module,
            action = preparation.message.action,
            messageId = preparation.message.id,
            generation = preparation.session.generation,
            timeoutMillis = timeoutMillis
        )
        else -> preparation.pending.deferred.await().typedOutcome()
    }
} catch (cancelled: CancellationException) {
    pendingRequests.remove(preparation.pending)
    preparation.pending.deferred.cancel(cancelled)
    throw cancelled
}

private fun DeviceRuntimeCommandOutcome<*>.traceSummary(): String = when (this) {
    is DeviceRuntimeCommandOutcome.Success<*> ->
        "SUCCESS status=$statusCode id=$messageId gen=${generation.value}"
    is DeviceRuntimeCommandOutcome.Timeout ->
        "TIMEOUT ${timeoutMillis}ms id=$messageId gen=${generation.value}"
    is DeviceRuntimeCommandOutcome.FirmwareError ->
        "FW_ERROR status=$statusCode code=$code field=$field " +
            "msg=${DosingDebugTrace.compact(message, TRACE_SHORT_MESSAGE_CHARS)}"
    is DeviceRuntimeCommandOutcome.ProtocolError ->
        "PROTOCOL_ERROR id=$messageId gen=${generation.value} " +
            "reason=${DosingDebugTrace.compact(reason, TRACE_REASON_CHARS)}"
    is DeviceRuntimeCommandOutcome.SendFailed -> "SEND_FAILED id=$messageId gen=${generation.value}"
    is DeviceRuntimeCommandOutcome.NotConnected -> "NOT_CONNECTED"
    is DeviceRuntimeCommandOutcome.NotAuthenticated -> "NOT_AUTHENTICATED gen=${generation.value}"
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> "UNSUPPORTED"
    is DeviceRuntimeCommandOutcome.Cancelled ->
        "CANCELLED id=$messageId gen=${generation.value} " +
            "reason=${DosingDebugTrace.compact(reason, TRACE_SHORT_MESSAGE_CHARS)}"
}

@Suppress("UNCHECKED_CAST")
private fun <T> DeviceRuntimeCommandOutcome<Any?>.typedOutcome(): DeviceRuntimeCommandOutcome<T> =
    this as DeviceRuntimeCommandOutcome<T>

private const val TRACE_SHORT_MESSAGE_CHARS = 240
private const val TRACE_REASON_CHARS = 300
