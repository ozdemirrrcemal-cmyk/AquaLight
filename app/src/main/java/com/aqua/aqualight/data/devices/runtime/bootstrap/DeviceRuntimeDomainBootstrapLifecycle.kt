package com.aqua.aqualight.data.devices.runtime.bootstrap

import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGenerationState
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns connection-scoped domain bootstrap jobs and delegates domain ordering to the coordinator.
 *
 * Session transport remains owned by DeviceRuntimeRepository; this collaborator only manages the
 * lifecycle of domain hydration work for one device/generation pair.
 */
internal class DeviceRuntimeDomainBootstrapLifecycle(
    private val scope: CoroutineScope,
    ports: List<DeviceRuntimeDomainBootstrapPort>,
    currentConnectionGeneration: (DeviceUid) -> DeviceRuntimeConnectionGeneration?,
    private val beginRuntimeGeneration: (DeviceUid, DeviceRuntimeConnectionGeneration) -> Unit,
    private val syncAfterHydration: suspend (DeviceUid, DeviceRuntimeConnectionGeneration) -> Unit
) {
    private data class Registration(
        val generation: DeviceRuntimeConnectionGeneration,
        val job: Job
    )

    private val coordinator = DeviceRuntimeDomainBootstrapCoordinator(
        ports = ports,
        currentConnectionGeneration = currentConnectionGeneration
    )
    private val jobs = ConcurrentHashMap<DeviceUid, Registration>()

    val readiness: StateFlow<Map<DeviceUid, DeviceRuntimeReadiness>> = coordinator.readiness

    fun start(
        state: DeviceRuntimeMetadataGenerationState.Ready,
        connectionGeneration: DeviceRuntimeConnectionGeneration
    ) {
        cancel(state.deviceUid)
        beginRuntimeGeneration(state.deviceUid, connectionGeneration)
        val context = DeviceRuntimeBootstrapContext(
            deviceUid = state.deviceUid,
            connectionGeneration = connectionGeneration,
            metadataGeneration = state.generation,
            metadata = state.metadata
        )
        val plan = DeviceRuntimeBootstrapPlanFactory.create(state.metadata)
        lateinit var registration: Registration
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val readiness = coordinator.hydrate(context, plan)
            val hydratedCurrentGeneration = readiness is DeviceRuntimeReadiness.Ready &&
                readiness.generation == connectionGeneration
            if (hydratedCurrentGeneration) {
                syncAfterHydration(state.deviceUid, connectionGeneration)
            }
        }
        registration = Registration(connectionGeneration, job)
        jobs.put(state.deviceUid, registration)?.job?.cancel()
        job.invokeOnCompletion {
            jobs.remove(state.deviceUid, registration)
        }
        job.start()
    }

    fun cancel(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) {
        val registration = jobs[deviceUid]
        val generationMatches = when {
            registration == null -> false
            generation == null -> true
            else -> registration.generation == generation
        }
        if (registration != null && generationMatches) {
            if (jobs.remove(deviceUid, registration)) {
                registration.job.cancel()
            }
        }
        coordinator.invalidate(deviceUid, generation)
    }

    fun cancelAll() {
        jobs.values.forEach { registration -> registration.job.cancel() }
        jobs.clear()
    }

    fun clear(deviceUid: DeviceUid) = coordinator.clear(deviceUid)

    fun clearAll() = coordinator.clearAll()
}
