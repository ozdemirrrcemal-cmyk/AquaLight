package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage

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
        return executeCorrelatedRuntimeRequest(
            deviceUid = deviceUid,
            command = command,
            timeoutMillis = timeoutMillis,
            sessionProvider = sessionProvider,
            supportChecker = supportChecker,
            pendingRequests = pendingRequests
        )
    }

    fun complete(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        message: AqlWsIncomingMessage
    ): DeviceRuntimeCompletionDisposition = when (message) {
        is AqlWsIncomingMessage.Response,
        is AqlWsIncomingMessage.Error -> completeCorrelatedRuntimeReply(
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
