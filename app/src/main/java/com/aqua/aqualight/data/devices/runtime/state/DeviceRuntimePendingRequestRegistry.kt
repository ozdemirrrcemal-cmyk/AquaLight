package com.aqua.aqualight.data.devices.runtime.state

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

internal data class DeviceRuntimePendingRequest(
    val request: DeviceRuntimeCommandRequest,
    val deferred: CompletableDeferred<DeviceRuntimeCommandOutcome>
)

internal class DeviceRuntimePendingRequestRegistry {
    private val requests = ConcurrentHashMap<String, DeviceRuntimePendingRequest>()

    fun register(
        messageId: String,
        request: DeviceRuntimeCommandRequest
    ): DeviceRuntimePendingRequest {
        val pending = DeviceRuntimePendingRequest(
            request = request,
            deferred = CompletableDeferred()
        )
        check(requests.putIfAbsent(messageId, pending) == null) {
            "Duplicate WebSocket command id: $messageId"
        }
        return pending
    }

    fun find(messageId: String): DeviceRuntimePendingRequest? = requests[messageId]

    fun remove(
        messageId: String,
        pending: DeviceRuntimePendingRequest
    ): Boolean = requests.remove(messageId, pending)

    fun cancelDevice(deviceUid: String, reason: String) {
        cancelMatching(
            predicate = { pending -> pending.request.deviceUid == deviceUid },
            reason = reason
        )
    }

    fun cancelAll(reason: String) {
        cancelMatching(predicate = { true }, reason = reason)
    }

    private fun cancelMatching(
        predicate: (DeviceRuntimePendingRequest) -> Boolean,
        reason: String
    ) {
        requests.entries
            .filter { (_, pending) -> predicate(pending) }
            .forEach { (messageId, pending) ->
                if (requests.remove(messageId, pending)) {
                    pending.deferred.complete(
                        DeviceRuntimeCommandOutcome.Cancelled(
                            deviceUid = pending.request.deviceUid,
                            module = pending.request.module,
                            action = pending.request.action,
                            messageId = messageId,
                            reason = reason
                        )
                    )
                }
            }
    }
}
