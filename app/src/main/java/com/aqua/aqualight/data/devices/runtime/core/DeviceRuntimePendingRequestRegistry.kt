package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.base.diagnostics.AppDiagnosticTrace
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

internal class DeviceRuntimePendingRequestRegistry {

    internal data class LookupKey(
        val deviceUid: DeviceUid,
        val generation: DeviceRuntimeConnectionGeneration,
        val messageId: String
    )

    internal class Pending(
        val key: DeviceRuntimeCorrelationKey,
        val parseSuccess: (AqlWsIncomingMessage.Response) -> Any?
    ) {
        val deferred = CompletableDeferred<DeviceRuntimeCommandOutcome<Any?>>()

        val lookupKey = LookupKey(
            deviceUid = key.deviceUid,
            generation = key.generation,
            messageId = key.messageId
        )
    }

    private val byCorrelation = ConcurrentHashMap<LookupKey, Pending>()
    private val terminalLock = Any()
    private val terminalKeys = HashSet<LookupKey>()
    private val terminalOrder = ArrayDeque<LookupKey>()

    val size: Int
        get() = byCorrelation.size

    fun register(
        key: DeviceRuntimeCorrelationKey,
        parseSuccess: (AqlWsIncomingMessage.Response) -> Any?
    ): Pending {
        require(key.messageId.isNotBlank()) { "Runtime request message ID must not be blank." }
        val pending = Pending(key, parseSuccess)
        check(!isTerminal(pending.lookupKey)) {
            "A completed runtime request ID cannot be reused in the same connection generation."
        }
        check(byCorrelation.putIfAbsent(pending.lookupKey, pending) == null) {
            "A runtime request with the same device, generation and message ID is already pending."
        }
        return pending
    }

    fun find(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        messageId: String
    ): Pending? = byCorrelation[LookupKey(deviceUid, generation, messageId)]

    fun isTerminal(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        messageId: String
    ): Boolean = isTerminal(LookupKey(deviceUid, generation, messageId))

    fun remove(
        pending: Pending,
        rememberTerminal: Boolean = true
    ): Boolean {
        val removed = byCorrelation.remove(pending.lookupKey, pending)
        if (removed && rememberTerminal) rememberTerminal(pending.lookupKey)
        return removed
    }

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

    private fun cancelMatching(
        reason: String,
        predicate: (DeviceRuntimeCorrelationKey) -> Boolean
    ) {
        byCorrelation.values.toList().forEach { pending ->
            val key = pending.key
            if (predicate(key) && remove(pending)) {
                val completed = pending.deferred.complete(
                    DeviceRuntimeCommandOutcome.Cancelled(
                        deviceUid = key.deviceUid,
                        module = key.module,
                        action = key.action,
                        messageId = key.messageId,
                        generation = key.generation,
                        reason = reason
                    )
                )
                AppDiagnosticTrace.event(
                    RUNTIME_PENDING_CATEGORY,
                    "cancelled",
                    "device" to AppDiagnosticTrace.deviceRef(key.deviceUid.value),
                    "generation" to key.generation.value,
                    "requestId" to key.messageId,
                    "module" to key.module,
                    "action" to key.action,
                    "completionAccepted" to completed
                )
            }
        }
    }

    private fun isTerminal(key: LookupKey): Boolean = synchronized(terminalLock) {
        key in terminalKeys
    }

    private fun rememberTerminal(key: LookupKey) {
        synchronized(terminalLock) {
            if (!terminalKeys.add(key)) return
            terminalOrder.addLast(key)
            while (terminalOrder.size > MAX_TERMINAL_KEYS) {
                terminalKeys.remove(terminalOrder.removeFirst())
            }
        }
    }

    private companion object {
        const val MAX_TERMINAL_KEYS = 512
        const val RUNTIME_PENDING_CATEGORY = "runtime_pending"
    }
}
