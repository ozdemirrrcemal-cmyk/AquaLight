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
    ): DeviceRuntimeEventRoutingResult = when (message) {
        is AqlWsIncomingMessage.Event -> routeEvent(deviceUid, generation, message)
        else -> DeviceRuntimeEventRoutingResult.Unmatched(message.module, message.action)
    }

    private suspend fun routeEvent(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        message: AqlWsIncomingMessage.Event
    ): DeviceRuntimeEventRoutingResult = DeviceRuntimeTypedEvent.Type
        .from(message.module, message.action)
        ?.let { type -> routeKnownEvent(deviceUid, generation, message, type) }
        ?: DeviceRuntimeEventRoutingResult.Unmatched(message.module, message.action)

    private suspend fun routeKnownEvent(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        message: AqlWsIncomingMessage.Event,
        type: DeviceRuntimeTypedEvent.Type
    ): DeviceRuntimeEventRoutingResult {
        mutex.lock()
        return try {
            val activeGeneration = activeGenerations[deviceUid]
            val result = if (activeGeneration == generation) {
                createRoutingResult(deviceUid, generation, message, type)
            } else {
                DeviceRuntimeEventRoutingResult.Stale(activeGeneration, generation)
            }
            if (result is DeviceRuntimeEventRoutingResult.Routed) {
                _events.emit(result.event)
            }
            result
        } finally {
            mutex.unlock()
        }
    }

    private fun createRoutingResult(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        message: AqlWsIncomingMessage.Event,
        type: DeviceRuntimeTypedEvent.Type
    ): DeviceRuntimeEventRoutingResult = when (
        val parsing = DeviceRuntimeEventPayloadParser.parse(message.data)
    ) {
        is DeviceRuntimeEventPayloadParser.Result.Invalid ->
            DeviceRuntimeEventRoutingResult.Malformed(parsing.field)
        is DeviceRuntimeEventPayloadParser.Result.Parsed -> DeviceRuntimeTypedEvent(
            deviceUid = deviceUid,
            generation = generation,
            messageId = message.id,
            type = type,
            payload = parsing.payload
        ).also(::updateState).let(DeviceRuntimeEventRoutingResult::Routed)
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

    private companion object {
        const val DEFAULT_EVENT_BUFFER_CAPACITY = 256
    }
}
