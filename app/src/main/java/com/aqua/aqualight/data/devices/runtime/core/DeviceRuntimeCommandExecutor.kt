package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

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

        val session = sessionProvider(deviceUid)
            ?: return DeviceRuntimeCommandOutcome.NotConnected(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action
            )
        if (!session.authenticated) {
            return DeviceRuntimeCommandOutcome.NotAuthenticated(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                generation = session.generation
            )
        }
        if (!supportChecker(deviceUid, command.module, command.action)) {
            return DeviceRuntimeCommandOutcome.UnsupportedByDevice(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action
            )
        }

        val message = try {
            AqlWsOutgoingMessage.Command(
                module = command.module,
                action = command.action,
                data = JSONObject(command.encodeData().toString())
            )
        } catch (_: Throwable) {
            return DeviceRuntimeCommandOutcome.ProtocolError(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = "",
                generation = session.generation,
                reason = "Command request did not satisfy its typed serialization contract."
            )
        }
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

        val sent = try {
            session.send(message)
        } catch (_: Throwable) {
            false
        }
        if (!sent) {
            pendingRequests.remove(pending)
            pending.deferred.cancel()
            return DeviceRuntimeCommandOutcome.SendFailed(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = message.id,
                generation = session.generation
            )
        }

        return try {
            val completed = withTimeoutOrNull(timeoutMillis) {
                pending.deferred.await()
            }
            when {
                completed != null -> completed.typed()
                pendingRequests.remove(pending) -> DeviceRuntimeCommandOutcome.Timeout(
                    deviceUid = deviceUid,
                    module = command.module,
                    action = command.action,
                    messageId = message.id,
                    generation = session.generation,
                    timeoutMillis = timeoutMillis
                )
                else -> pending.deferred.await().typed()
            }
        } catch (cancelled: CancellationException) {
            pendingRequests.remove(pending)
            pending.deferred.cancel(cancelled)
            throw cancelled
        }
    }

    /**
     * Routes only a response/error from the exact collector generation. Old-generation messages
     * are ignored and cannot cancel or complete a current request that happens to reuse an ID.
     */
    fun complete(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        message: AqlWsIncomingMessage
    ): Boolean {
        if (message !is AqlWsIncomingMessage.Response &&
            message !is AqlWsIncomingMessage.Error
        ) {
            return false
        }
        val pending = pendingRequests.find(message.id) ?: return false
        val key = pending.key
        if (key.deviceUid != deviceUid || key.generation != generation) {
            return false
        }
        if (key.module != message.module || key.action != message.action) {
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
            return true
        }
        if (!pendingRequests.remove(pending)) {
            return true
        }

        val outcome: DeviceRuntimeCommandOutcome<Any?> = when (message) {
            is AqlWsIncomingMessage.Response -> parseSuccess(pending, message)
            is AqlWsIncomingMessage.Error -> DeviceRuntimeCommandOutcome.FirmwareError(
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
            is AqlWsIncomingMessage.Event -> error("Events are not pending command completions.")
        }
        pending.deferred.complete(outcome)
        return true
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

    internal fun pendingCount(): Int = pendingRequests.size()

    private fun parseSuccess(
        pending: DeviceRuntimePendingRequestRegistry.Pending,
        response: AqlWsIncomingMessage.Response
    ): DeviceRuntimeCommandOutcome<Any?> {
        val key = pending.key
        return try {
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
            DeviceRuntimeCommandOutcome.ProtocolError(
                deviceUid = key.deviceUid,
                module = key.module,
                action = key.action,
                messageId = key.messageId,
                generation = key.generation,
                reason = "Successful firmware response did not match the typed command contract."
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> DeviceRuntimeCommandOutcome<Any?>.typed(): DeviceRuntimeCommandOutcome<T> =
        this as DeviceRuntimeCommandOutcome<T>
}
