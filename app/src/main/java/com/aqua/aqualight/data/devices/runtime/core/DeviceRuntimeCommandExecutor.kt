package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

internal enum class DeviceRuntimeCompletionDisposition {
    UNMATCHED,
    COMPLETED,
    DUPLICATE_OR_LATE
}

internal class DeviceRuntimeCommandExecutor(
    private val sessionProvider: (DeviceUid) -> DeviceRuntimeCommandSession?,
    private val supportChecker: (DeviceUid, String, String) -> Boolean,
    private val pendingRequests: DeviceRuntimePendingRequestRegistry =
        DeviceRuntimePendingRequestRegistry()
) {

    suspend fun <T> execute(
        deviceUid: DeviceUid,
        command: DeviceRuntimeCommand<T>,
        timeoutMillis: Long = DEVICE_RUNTIME_DEFAULT_TIMEOUT_MILLIS
    ): DeviceRuntimeCommandOutcome<T> {
        require(timeoutMillis in DEVICE_RUNTIME_MIN_TIMEOUT_MILLIS..DEVICE_RUNTIME_MAX_TIMEOUT_MILLIS) {
            "timeoutMillis is outside the supported runtime range."
        }
        require(AqlWsContract.isAuthenticatedCommand(command.module, command.action)) {
            "Unregistered firmware command: ${command.module}.${command.action}"
        }

        return when (
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
    }

    /** Routes only response/error frames from the exact device connection generation. */
    fun complete(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        message: AqlWsIncomingMessage
    ): DeviceRuntimeCompletionDisposition = when (message) {
        is AqlWsIncomingMessage.Response,
        is AqlWsIncomingMessage.Error -> completeRuntimeReply(
            deviceUid = deviceUid,
            generation = generation,
            message = message,
            pendingRequests = pendingRequests
        )
        is AqlWsIncomingMessage.Event -> DeviceRuntimeCompletionDisposition.UNMATCHED
    }

    fun cancelGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        reason: String
    ) {
        pendingRequests.cancelGeneration(deviceUid, generation, reason)
    }

    fun cancelDevice(deviceUid: DeviceUid, reason: String) {
        pendingRequests.cancelDevice(deviceUid, reason)
    }

    fun cancelAll(reason: String) {
        pendingRequests.cancelAll(reason)
    }

    internal fun pendingCount(): Int = pendingRequests.size

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = DEVICE_RUNTIME_DEFAULT_TIMEOUT_MILLIS
        const val MIN_TIMEOUT_MILLIS = DEVICE_RUNTIME_MIN_TIMEOUT_MILLIS
        const val MAX_TIMEOUT_MILLIS = DEVICE_RUNTIME_MAX_TIMEOUT_MILLIS
    }
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

private fun completeRuntimeReply(
    deviceUid: DeviceUid,
    generation: DeviceRuntimeConnectionGeneration,
    message: AqlWsIncomingMessage,
    pendingRequests: DeviceRuntimePendingRequestRegistry
): DeviceRuntimeCompletionDisposition {
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

@Suppress("UNCHECKED_CAST")
private fun <T> DeviceRuntimeCommandOutcome<Any?>.typedOutcome(): DeviceRuntimeCommandOutcome<T> =
    this as DeviceRuntimeCommandOutcome<T>
