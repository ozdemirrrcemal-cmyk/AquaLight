package com.aqua.aqualight.data.devices.runtime.events

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Routes authenticated firmware events without consuming or rewriting the legacy raw event flow.
 * State is isolated by device and connection generation.
 */
class DeviceRuntimeEventRouter(
    eventBufferCapacity: Int = DEFAULT_EVENT_BUFFER_CAPACITY
) {
    private val mutex = Mutex()
    private val activeGenerations = HashMap<DeviceUid, DeviceRuntimeConnectionGeneration>()

    private val _events = MutableSharedFlow<DeviceRuntimeTypedEvent>(
        extraBufferCapacity = eventBufferCapacity
    )
    val events: SharedFlow<DeviceRuntimeTypedEvent> = _events.asSharedFlow()

    private val _states = MutableStateFlow<
        Map<DeviceUid, Map<DeviceRuntimeTypedEvent.Type, DeviceRuntimeTypedEvent>>
        >(emptyMap())
    val states: StateFlow<
        Map<DeviceUid, Map<DeviceRuntimeTypedEvent.Type, DeviceRuntimeTypedEvent>>
        > = _states.asStateFlow()

    init {
        require(eventBufferCapacity > 0) { "Runtime event buffer capacity must be positive." }
    }

    suspend fun activate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ) {
        mutex.withLock {
            val previous = activeGenerations.put(deviceUid, generation)
            if (previous != generation) removeDeviceState(deviceUid)
        }
    }

    suspend fun deactivate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ) {
        mutex.withLock {
            if (activeGenerations[deviceUid] == generation) {
                activeGenerations.remove(deviceUid)
                removeDeviceState(deviceUid)
            }
        }
    }

    suspend fun clearAll() {
        mutex.withLock {
            activeGenerations.clear()
            _states.value = emptyMap()
        }
    }

    suspend fun route(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        message: AqlWsIncomingMessage
    ): DeviceRuntimeEventRoutingResult {
        val eventMessage = message as? AqlWsIncomingMessage.Event
            ?: return DeviceRuntimeEventRoutingResult.Unmatched(message.module, message.action)
        val type = DeviceRuntimeTypedEvent.Type.from(eventMessage.module, eventMessage.action)
            ?: return DeviceRuntimeEventRoutingResult.Unmatched(
                eventMessage.module,
                eventMessage.action
            )
        return routeKnownEvent(deviceUid, generation, eventMessage, type)
    }

    private suspend fun routeKnownEvent(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        message: AqlWsIncomingMessage.Event,
        type: DeviceRuntimeTypedEvent.Type
    ): DeviceRuntimeEventRoutingResult {
        mutex.lock()
        try {
            val activeGeneration = activeGenerations[deviceUid]
            if (activeGeneration != generation) {
                return DeviceRuntimeEventRoutingResult.Stale(activeGeneration, generation)
            }
            val payload = when (val parsing = parsePayload(message.data)) {
                is PayloadParsing.Parsed -> parsing.payload
                is PayloadParsing.Invalid ->
                    return DeviceRuntimeEventRoutingResult.Malformed(parsing.field)
            }
            val event = DeviceRuntimeTypedEvent(
                deviceUid = deviceUid,
                generation = generation,
                messageId = message.id,
                type = type,
                payload = payload
            )
            updateState(event)
            _events.emit(event)
            return DeviceRuntimeEventRoutingResult.Routed(event)
        } finally {
            mutex.unlock()
        }
    }

    private fun parsePayload(data: JSONObject): PayloadParsing {
        val keys = data.keys().asSequence().toSet()
        if (keys.none(COMMAND_EVENT_FIELDS::contains)) {
            return copyJson(data)?.let { copy ->
                PayloadParsing.Parsed(DeviceRuntimeEventPayload.Snapshot(copy))
            } ?: PayloadParsing.Invalid(FIELD_DATA)
        }
        if (keys != COMMAND_EVENT_FIELDS) return PayloadParsing.Invalid(FIELD_DATA)

        val commandId = requiredText(data, FIELD_COMMAND_ID)
            ?: return PayloadParsing.Invalid(FIELD_COMMAND_ID)
        val commandModule = requiredText(data, FIELD_MODULE)
            ?: return PayloadParsing.Invalid(FIELD_MODULE)
        val commandAction = requiredText(data, FIELD_ACTION)
            ?: return PayloadParsing.Invalid(FIELD_ACTION)
        val sessionId = requiredText(data, FIELD_SESSION_ID)
            ?: return PayloadParsing.Invalid(FIELD_SESSION_ID)
        val publishedAtMillis = runCatching { data.getLong(FIELD_PUBLISHED_AT_MS) }.getOrNull()
            ?.takeIf { value -> value >= 0L }
            ?: return PayloadParsing.Invalid(FIELD_PUBLISHED_AT_MS)
        val result = data.optJSONObject(FIELD_RESULT)?.let(::copyJson)
            ?: return PayloadParsing.Invalid(FIELD_RESULT)

        return PayloadParsing.Parsed(
            DeviceRuntimeEventPayload.CommandResult(
                commandId = commandId,
                commandModule = commandModule,
                commandAction = commandAction,
                sessionId = sessionId,
                publishedAtMillis = publishedAtMillis,
                result = result
            )
        )
    }

    private fun updateState(event: DeviceRuntimeTypedEvent) {
        val allStates = _states.value.toMutableMap()
        val deviceStates = allStates[event.deviceUid].orEmpty().toMutableMap()
        deviceStates[event.type] = event
        allStates[event.deviceUid] = deviceStates.toMap()
        _states.value = allStates.toMap()
    }

    private fun removeDeviceState(deviceUid: DeviceUid) {
        if (deviceUid !in _states.value) return
        _states.value = _states.value.toMutableMap().apply { remove(deviceUid) }.toMap()
    }

    private fun requiredText(data: JSONObject, field: String): String? =
        data.optString(field, "").trim().takeIf(String::isNotEmpty)

    private fun copyJson(source: JSONObject): JSONObject? =
        runCatching { JSONObject(source.toString()) }.getOrNull()

    private sealed interface PayloadParsing {
        data class Parsed(
            val payload: DeviceRuntimeEventPayload
        ) : PayloadParsing

        data class Invalid(
            val field: String
        ) : PayloadParsing
    }

    private companion object {
        const val DEFAULT_EVENT_BUFFER_CAPACITY = 256
        const val FIELD_DATA = "data"
        const val FIELD_COMMAND_ID = "commandId"
        const val FIELD_MODULE = "module"
        const val FIELD_ACTION = "action"
        const val FIELD_SESSION_ID = "sessionId"
        const val FIELD_PUBLISHED_AT_MS = "publishedAtMs"
        const val FIELD_RESULT = "result"

        val COMMAND_EVENT_FIELDS = setOf(
            FIELD_COMMAND_ID,
            FIELD_MODULE,
            FIELD_ACTION,
            FIELD_SESSION_ID,
            FIELD_PUBLISHED_AT_MS,
            FIELD_RESULT
        )
    }
}
