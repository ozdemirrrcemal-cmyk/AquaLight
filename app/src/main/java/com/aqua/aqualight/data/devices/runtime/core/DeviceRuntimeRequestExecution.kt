package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.base.diagnostics.AppDiagnosticTrace
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
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
        AppDiagnosticTrace.event(
            RUNTIME_REQUEST_CATEGORY,
            "preparation_rejected",
            "device" to AppDiagnosticTrace.deviceRef(deviceUid.value),
            "module" to command.module,
            "action" to command.action,
            "outcome" to outcome.traceName()
        )
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
    preparation.trace("send_started")
    val sent = runCatching {
        preparation.session.send(preparation.message)
    }.getOrDefault(false)

    return if (sent) {
        preparation.trace("send_succeeded")
        awaitRuntimeRequest(preparation, timeoutMillis, pendingRequests)
    } else {
        preparation.trace("send_failed")
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
}

private suspend fun <T> awaitRuntimeRequest(
    preparation: DeviceRuntimeRequestPreparation.Ready,
    timeoutMillis: Long,
    pendingRequests: DeviceRuntimePendingRequestRegistry
): DeviceRuntimeCommandOutcome<T> = try {
    preparation.trace("await_started", "timeoutMillis" to timeoutMillis)
    val completed = withTimeoutOrNull(timeoutMillis) {
        preparation.pending.deferred.await()
    }
    when {
        completed != null -> completed.typedOutcome<T>().also { outcome ->
            preparation.trace("await_completed", "outcome" to outcome.traceName())
        }
        pendingRequests.remove(preparation.pending) -> {
            preparation.trace("timed_out", "timeoutMillis" to timeoutMillis)
            DeviceRuntimeCommandOutcome.Timeout(
                deviceUid = preparation.session.deviceUid,
                module = preparation.message.module,
                action = preparation.message.action,
                messageId = preparation.message.id,
                generation = preparation.session.generation,
                timeoutMillis = timeoutMillis
            )
        }
        else -> {
            preparation.trace("timeout_completion_race")
            preparation.pending.deferred.await().typedOutcome<T>().also { outcome ->
                preparation.trace("await_completed", "outcome" to outcome.traceName())
            }
        }
    }
} catch (cancelled: CancellationException) {
    preparation.trace("await_cancelled")
    pendingRequests.remove(preparation.pending)
    preparation.pending.deferred.cancel(cancelled)
    throw cancelled
}

@Suppress("UNCHECKED_CAST")
private fun <T> DeviceRuntimeCommandOutcome<Any?>.typedOutcome(): DeviceRuntimeCommandOutcome<T> =
    this as DeviceRuntimeCommandOutcome<T>

private fun DeviceRuntimeRequestPreparation.Ready.trace(
    name: String,
    vararg fields: Pair<String, Any?>
) {
    AppDiagnosticTrace.event(
        RUNTIME_REQUEST_CATEGORY,
        name,
        "device" to AppDiagnosticTrace.deviceRef(session.deviceUid.value),
        "generation" to session.generation.value,
        "requestId" to message.id,
        "module" to message.module,
        "action" to message.action,
        *fields
    )
}

private fun DeviceRuntimeCommandOutcome<*>.traceName(): String = javaClass.simpleName

private const val RUNTIME_REQUEST_CATEGORY = "runtime_request"
