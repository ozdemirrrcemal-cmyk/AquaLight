package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

internal suspend fun <T> executeCorrelatedRuntimeRequest(
    deviceUid: DeviceUid,
    command: DeviceRuntimeCommand<T>,
    timeoutMillis: Long,
    sessionProvider: (DeviceUid) -> DeviceRuntimeCommandSession?,
    supportChecker: (DeviceUid, String, String) -> Boolean,
    pendingRequests: DeviceRuntimePendingRequestRegistry
): DeviceRuntimeCommandOutcome<T> = when (
    val preparation = prepareRuntimeRequest(
        deviceUid = deviceUid,
        command = command,
        sessionProvider = sessionProvider,
        supportChecker = supportChecker,
        pendingRequests = pendingRequests
    )
) {
    is DeviceRuntimeRequestPreparation.Rejected -> preparation.outcome
    is DeviceRuntimeRequestPreparation.Ready -> sendAndAwaitRuntimeRequest(
        preparation = preparation,
        timeoutMillis = timeoutMillis,
        pendingRequests = pendingRequests
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
    sessionProvider: (DeviceUid) -> DeviceRuntimeCommandSession?,
    supportChecker: (DeviceUid, String, String) -> Boolean,
    pendingRequests: DeviceRuntimePendingRequestRegistry
): DeviceRuntimeRequestPreparation<T> {
    val session = sessionProvider(deviceUid)
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
        !supportChecker(deviceUid, command.module, command.action) ->
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
            pendingRequests = pendingRequests
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
    val sent = runCatching {
        preparation.session.send(preparation.message)
    }.getOrDefault(false)

    return if (sent) {
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

@Suppress("UNCHECKED_CAST")
private fun <T> DeviceRuntimeCommandOutcome<Any?>.typedOutcome(): DeviceRuntimeCommandOutcome<T> =
    this as DeviceRuntimeCommandOutcome<T>
