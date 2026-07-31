package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

internal class DeviceRuntimePendingRequestRegistry {

    internal class Pending(
        val key: DeviceRuntimeCorrelationKey,
        val parseSuccess: (AqlWsIncomingMessage.Response) -> Any?
    ) {
        val deferred = CompletableDeferred<DeviceRuntimeCommandOutcome<Any?>>()
    }

    private val byMessageId = ConcurrentHashMap<String, Pending>()

    fun register(
        key: DeviceRuntimeCorrelationKey,
        parseSuccess: (AqlWsIncomingMessage.Response) -> Any?
    ): Pending {
        val pending = Pending(key, parseSuccess)
        check(byMessageId.putIfAbsent(key.messageId, pending) == null) {
            "A runtime request with the same message ID is already pending."
        }
        return pending
    }

    fun find(messageId: String): Pending? = byMessageId[messageId]

    fun remove(pending: Pending): Boolean =
        byMessageId.remove(pending.key.messageId, pending)

    fun cancelGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        reason: String
    ) {
        cancelMatching(reason) { key ->
            key.deviceUid == deviceUid && key.generation == generation
        }
    }

    fun cancelDevice(deviceUid: DeviceUid, reason: String) {
        cancelMatching(reason) { key -> key.deviceUid == deviceUid }
    }

    fun cancelAll(reason: String) {
        cancelMatching(reason) { true }
    }

    fun size(): Int = byMessageId.size

    private fun cancelMatching(
        reason: String,
        predicate: (DeviceRuntimeCorrelationKey) -> Boolean
    ) {
        byMessageId.values.toList().forEach { pending ->
            val key = pending.key
            if (predicate(key) && remove(pending)) {
                pending.deferred.complete(
                    DeviceRuntimeCommandOutcome.Cancelled(
                        deviceUid = key.deviceUid,
                        module = key.module,
                        action = key.action,
                        messageId = key.messageId,
                        generation = key.generation,
                        reason = reason
                    )
                )
            }
        }
    }
}
