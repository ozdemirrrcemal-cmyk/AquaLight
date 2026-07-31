package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

internal class DeviceRuntimeCommandExecutor(
    private val devicesRepository: DevicesRepository,
    private val stateStore: DeviceRuntimeStateStore,
    private val isActive: () -> Boolean,
    private val pendingRequests: DeviceRuntimePendingRequestRegistry =
        DeviceRuntimePendingRequestRegistry()
) {

    suspend fun execute(request: DeviceRuntimeCommandRequest): DeviceRuntimeCommandOutcome {
        validate(request)
        return if (isActive()) {
            executeActive(request)
        } else {
            DeviceRuntimeCommandOutcome.Cancelled(
                deviceUid = request.deviceUid,
                module = request.module,
                action = request.action,
                messageId = "",
                reason = "Runtime data repository is not active."
            )
        }
    }

    fun completeCorrelated(
        deviceUid: DeviceUid,
        message: AqlWsIncomingMessage
    ) {
        val canComplete = message is AqlWsIncomingMessage.Response ||
            message is AqlWsIncomingMessage.Error
        if (canComplete) {
            pendingRequests.find(message.id)?.let { pending ->
                val outcome = correlationOutcome(deviceUid, message, pending)
                if (pendingRequests.remove(message.id, pending)) {
                    pending.deferred.complete(outcome)
                }
            }
        }
    }

    fun cancelDevice(deviceUid: DeviceUid, reason: String) {
        pendingRequests.cancelDevice(deviceUid, reason)
    }

    fun cancelAll(reason: String) {
        pendingRequests.cancelAll(reason)
    }

    private fun validate(request: DeviceRuntimeCommandRequest) {
        require(request.timeoutMillis in MIN_COMMAND_TIMEOUT_MS..MAX_COMMAND_TIMEOUT_MS) {
            "timeoutMillis is outside the commercial runtime range."
        }
        require(AqlWsContract.isAuthenticatedCommand(request.module, request.action)) {
            "Unregistered firmware command: ${request.module}.${request.action}"
        }
    }

    private suspend fun executeActive(
        request: DeviceRuntimeCommandRequest
    ): DeviceRuntimeCommandOutcome {
        val client = devicesRepository.commandClient(request.deviceUid)
        return if (client == null) {
            DeviceRuntimeCommandOutcome.NotConnected(
                deviceUid = request.deviceUid,
                module = request.module,
                action = request.action
            )
        } else {
            executeConnected(request, client)
        }
    }

    private suspend fun executeConnected(
        request: DeviceRuntimeCommandRequest,
        client: AqlWsCommandClient
    ): DeviceRuntimeCommandOutcome {
        val command = AqlWsOutgoingMessage.Command(
            module = request.module,
            action = request.action,
            data = JSONObject(request.data.toString())
        )
        val pending = pendingRequests.register(command.id, request)
        return if (client.send(command)) {
            awaitOutcome(command.id, pending)
        } else {
            sendFailure(command.id, pending)
        }
    }

    private suspend fun awaitOutcome(
        messageId: String,
        pending: DeviceRuntimePendingRequest
    ): DeviceRuntimeCommandOutcome = try {
        withTimeoutOrNull(pending.request.timeoutMillis) {
            pending.deferred.await()
        } ?: DeviceRuntimeCommandOutcome.Timeout(
            deviceUid = pending.request.deviceUid,
            module = pending.request.module,
            action = pending.request.action,
            messageId = messageId,
            timeoutMillis = pending.request.timeoutMillis
        ).also {
            stateStore.applyCommandFault(
                pending.request.deviceUid,
                DeviceRuntimeCommandFaultReport(
                    code = "command_timeout",
                    message = "Firmware did not answer within ${pending.request.timeoutMillis}ms.",
                    module = pending.request.module,
                    action = pending.request.action,
                    messageId = messageId
                )
            )
        }
    } finally {
        pendingRequests.remove(messageId, pending)
        pending.deferred.cancel()
    }

    private fun sendFailure(
        messageId: String,
        pending: DeviceRuntimePendingRequest
    ): DeviceRuntimeCommandOutcome.SendFailed {
        pendingRequests.remove(messageId, pending)
        pending.deferred.cancel()
        stateStore.applyCommandFault(
            pending.request.deviceUid,
            DeviceRuntimeCommandFaultReport(
                code = "send_failed",
                message = "WebSocket command could not be queued.",
                module = pending.request.module,
                action = pending.request.action,
                messageId = messageId
            )
        )
        return DeviceRuntimeCommandOutcome.SendFailed(
            deviceUid = pending.request.deviceUid,
            module = pending.request.module,
            action = pending.request.action,
            messageId = messageId
        )
    }

    private fun correlationOutcome(
        deviceUid: DeviceUid,
        message: AqlWsIncomingMessage,
        pending: DeviceRuntimePendingRequest
    ): DeviceRuntimeCommandOutcome {
        val request = pending.request
        val exactMatch = request.deviceUid == deviceUid &&
            request.module == message.module &&
            request.action == message.action
        return if (exactMatch) {
            DeviceRuntimeCommandOutcomeMapper.fromIncoming(deviceUid, message)
        } else {
            stateStore.applyCommandFault(
                request.deviceUid,
                DeviceRuntimeCommandFaultReport(
                    code = DeviceRuntimeCommandOutcomeMapper.CORRELATION_MISMATCH_CODE,
                    message = "Firmware response id matched a different device/module/action.",
                    module = request.module,
                    action = request.action,
                    messageId = message.id
                )
            )
            DeviceRuntimeCommandOutcomeMapper.correlationMismatch(pending, message.id)
        }
    }

    companion object {
        const val MIN_COMMAND_TIMEOUT_MS = 1_000L
        const val MAX_COMMAND_TIMEOUT_MS = 30_000L
    }
}
