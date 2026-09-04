package com.aqua.aqualight.data.devices.runtime.bootstrap

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface DeviceRuntimeReadiness {
    val generation: DeviceRuntimeConnectionGeneration

    data class Hydrating(
        override val generation: DeviceRuntimeConnectionGeneration,
        val requiredDomains: List<DeviceRuntimeDomain>,
        val completedDomains: List<DeviceRuntimeDomain>
    ) : DeviceRuntimeReadiness

    data class Ready(
        override val generation: DeviceRuntimeConnectionGeneration,
        val domains: List<DeviceRuntimeDomain>
    ) : DeviceRuntimeReadiness

    data class Failed(
        override val generation: DeviceRuntimeConnectionGeneration,
        val domain: DeviceRuntimeDomain
    ) : DeviceRuntimeReadiness

    data class Stale(
        override val generation: DeviceRuntimeConnectionGeneration
    ) : DeviceRuntimeReadiness
}

/**
 * Runs one catalog-derived domain hydration sequence for one authenticated connection generation.
 *
 * It owns orchestration only. Protocol parsing and domain state remain behind each bootstrap port.
 * RuntimeReady is published only after every required domain proves authoritative state for the
 * exact generation that started the sequence and that generation is still current at the end.
 */
internal class DeviceRuntimeDomainBootstrapCoordinator(
    ports: List<DeviceRuntimeDomainBootstrapPort>,
    private val currentConnectionGeneration: (DeviceUid) -> DeviceRuntimeConnectionGeneration?
) {
    private val lock = Any()
    private val portsByDomain = ports.associateBy(DeviceRuntimeDomainBootstrapPort::domain)
    private val _readiness = MutableStateFlow<Map<DeviceUid, DeviceRuntimeReadiness>>(emptyMap())
    val readiness: StateFlow<Map<DeviceUid, DeviceRuntimeReadiness>> = _readiness.asStateFlow()

    init {
        require(portsByDomain.size == ports.size) { "Runtime bootstrap domains must be unique." }
    }

    suspend fun hydrate(
        context: DeviceRuntimeBootstrapContext,
        plan: DeviceRuntimeBootstrapPlan
    ): DeviceRuntimeReadiness {
        val generation = context.connectionGeneration
        if (!isCurrent(context.deviceUid, generation)) {
            return publish(context.deviceUid, DeviceRuntimeReadiness.Stale(generation))
        }
        publish(
            context.deviceUid,
            DeviceRuntimeReadiness.Hydrating(
                generation = generation,
                requiredDomains = plan.domains,
                completedDomains = emptyList()
            )
        )
        val completed = ArrayList<DeviceRuntimeDomain>(plan.domains.size)
        try {
            for (domain in plan.domains) {
                if (!isCurrent(context.deviceUid, generation)) {
                    return publish(context.deviceUid, DeviceRuntimeReadiness.Stale(generation))
                }
                val port = checkNotNull(portsByDomain[domain]) {
                    "Missing runtime bootstrap port for $domain."
                }
                when (val result = port.hydrate(context)) {
                    is DeviceRuntimeDomainHydrationResult.Hydrated -> {
                        if (
                            result.generation != generation ||
                            !isCurrent(context.deviceUid, generation)
                        ) {
                            return publish(
                                context.deviceUid,
                                DeviceRuntimeReadiness.Stale(generation)
                            )
                        }
                        completed += domain
                        publish(
                            context.deviceUid,
                            DeviceRuntimeReadiness.Hydrating(
                                generation = generation,
                                requiredDomains = plan.domains,
                                completedDomains = completed.toList()
                            )
                        )
                    }
                    is DeviceRuntimeDomainHydrationResult.Failed -> return publish(
                        context.deviceUid,
                        DeviceRuntimeReadiness.Failed(generation, result.domain)
                    )
                    is DeviceRuntimeDomainHydrationResult.RejectedStale -> return publish(
                        context.deviceUid,
                        DeviceRuntimeReadiness.Stale(generation)
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            if (!isCurrent(context.deviceUid, generation)) {
                publish(context.deviceUid, DeviceRuntimeReadiness.Stale(generation))
            }
            throw cancellation
        }
        return if (isCurrent(context.deviceUid, generation)) {
            publish(
                context.deviceUid,
                DeviceRuntimeReadiness.Ready(generation, completed.toList())
            )
        } else {
            publish(context.deviceUid, DeviceRuntimeReadiness.Stale(generation))
        }
    }

    fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) {
        synchronized(lock) {
            val current = _readiness.value[deviceUid] ?: return@synchronized
            if (generation != null && current.generation != generation) return@synchronized
            _readiness.value = _readiness.value + (
                deviceUid to DeviceRuntimeReadiness.Stale(current.generation)
            )
        }
    }

    fun clear(deviceUid: DeviceUid) = synchronized(lock) {
        if (deviceUid in _readiness.value) {
            _readiness.value = _readiness.value.toMutableMap().apply { remove(deviceUid) }.toMap()
        }
    }

    fun clearAll() = synchronized(lock) {
        _readiness.value = emptyMap()
    }

    private fun isCurrent(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = currentConnectionGeneration(deviceUid) == generation

    private fun publish(
        deviceUid: DeviceUid,
        readiness: DeviceRuntimeReadiness
    ): DeviceRuntimeReadiness = synchronized(lock) {
        val previous = _readiness.value[deviceUid]
        if (
            previous != null &&
            previous.generation.value > readiness.generation.value
        ) {
            return@synchronized previous
        }
        _readiness.value = _readiness.value + (deviceUid to readiness)
        readiness
    }
}
