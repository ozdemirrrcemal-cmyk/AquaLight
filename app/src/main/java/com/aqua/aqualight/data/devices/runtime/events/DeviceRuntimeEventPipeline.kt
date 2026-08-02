package com.aqua.aqualight.data.devices.runtime.events

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Typed event pipeline for module repositories.
 *
 * Wire events remain inside the data runtime. Only validated typed events are offered to module
 * reducers and higher-level runtime coordinators.
 */
class DeviceRuntimeEventPipeline(
    private val repository: DeviceRuntimeRepository,
    scope: CoroutineScope
) : AutoCloseable {
    private val router = DeviceRuntimeEventRouter()

    val events: SharedFlow<DeviceRuntimeTypedEvent> = router.events
    val states: StateFlow<
        Map<DeviceUid, Map<DeviceRuntimeTypedEvent.Type, DeviceRuntimeTypedEvent>>
        > = router.states

    private val _routingResults = MutableSharedFlow<DeviceRuntimeEventRoutingResult>(
        extraBufferCapacity = ROUTING_RESULT_BUFFER_CAPACITY
    )
    val routingResults: SharedFlow<DeviceRuntimeEventRoutingResult> =
        _routingResults.asSharedFlow()

    private val collectionJob: Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        repository.events.collect(::handleRepositoryEvent)
    }

    override fun close() {
        collectionJob.cancel()
    }

    suspend fun shutdown() {
        collectionJob.cancelAndJoin()
        router.clearAll()
    }

    private suspend fun handleRepositoryEvent(event: AqlWsEvent) {
        when (event) {
            is AqlWsEvent.Opened,
            is AqlWsEvent.Authenticated -> activateCurrent(event.deviceUid)
            is AqlWsEvent.Message -> routeMessage(event)
            is AqlWsEvent.Closed,
            is AqlWsEvent.Failure -> deactivateCurrent(event.deviceUid)
        }
    }

    private suspend fun activateCurrent(deviceUid: DeviceUid) {
        currentGeneration(deviceUid)?.let { generation ->
            repository.runtimeModules.clearRuntimeState(deviceUid)
            router.activate(deviceUid, generation)
        }
    }

    private suspend fun deactivateCurrent(deviceUid: DeviceUid) {
        currentGeneration(deviceUid)?.let { generation ->
            router.deactivate(deviceUid, generation)
        }
        repository.runtimeModules.clearRuntimeState(deviceUid)
    }

    private suspend fun routeMessage(event: AqlWsEvent.Message) {
        val message = event.parsed as? AqlWsIncomingMessage.Event ?: return
        val generation = currentGeneration(event.deviceUid) ?: return
        router.activate(event.deviceUid, generation)
        val result = router.route(
            deviceUid = event.deviceUid,
            generation = generation,
            message = message
        )
        if (result is DeviceRuntimeEventRoutingResult.Routed) {
            repository.runtimeModules.acceptTypedRuntimeEvent(result.event)
        }
        _routingResults.emit(result)
    }

    private fun currentGeneration(deviceUid: DeviceUid): DeviceRuntimeConnectionGeneration? =
        repository.currentConnectionGeneration(deviceUid)

    private companion object {
        const val ROUTING_RESULT_BUFFER_CAPACITY = 64
    }
}
