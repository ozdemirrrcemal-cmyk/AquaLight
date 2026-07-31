package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Owner-scoped, in-memory source of truth for all per-device runtime module values. */
class DeviceRuntimeStateStore {
    private val _states = MutableStateFlow<Map<DeviceUid, DeviceRuntimeState>>(emptyMap())

    val states: StateFlow<Map<DeviceUid, DeviceRuntimeState>> = _states.asStateFlow()

    fun observe(deviceUid: DeviceUid): Flow<DeviceRuntimeState?> = states
        .map { current -> current[deviceUid] }
        .distinctUntilChanged()

    fun current(deviceUid: DeviceUid): DeviceRuntimeState? = states.value[deviceUid]

    fun beginGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        authenticated: Boolean
    ) {
        _states.update { current ->
            val previous = current[deviceUid] ?: DeviceRuntimeState(deviceUid)
            current + (
                deviceUid to previous.copy(
                    generation = generation,
                    authenticated = authenticated,
                    support = DeviceRuntimeSupport(),
                    metadata = previous.metadata.staleForGenerationChange(),
                    device = previous.device.staleForGenerationChange(),
                    security = previous.security.staleForGenerationChange(),
                    network = previous.network.staleForGenerationChange(),
                    time = previous.time.staleForGenerationChange(),
                    light = previous.light.staleForGenerationChange(),
                    lightTemperatureProtection =
                        previous.lightTemperatureProtection.staleForGenerationChange(),
                    timer = previous.timer.staleForGenerationChange(),
                    dosing = previous.dosing.staleForGenerationChange(),
                    cooling = previous.cooling.staleForGenerationChange(),
                    firmware = previous.firmware.staleForGenerationChange(),
                    ota = previous.ota.staleForGenerationChange(),
                    protocolFault = null
                )
            )
        }
    }

    fun setAuthenticated(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        authenticated: Boolean
    ): Boolean = reduce(deviceUid, generation) { state ->
        state.copy(authenticated = authenticated)
    }

    fun markGenerationStale(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = reduce(deviceUid, generation) { state ->
        state.copy(
            authenticated = false,
            metadata = state.metadata.staleForGenerationChange(),
            device = state.device.staleForGenerationChange(),
            security = state.security.staleForGenerationChange(),
            network = state.network.staleForGenerationChange(),
            time = state.time.staleForGenerationChange(),
            light = state.light.staleForGenerationChange(),
            lightTemperatureProtection =
                state.lightTemperatureProtection.staleForGenerationChange(),
            timer = state.timer.staleForGenerationChange(),
            dosing = state.dosing.staleForGenerationChange(),
            cooling = state.cooling.staleForGenerationChange(),
            firmware = state.firmware.staleForGenerationChange(),
            ota = state.ota.staleForGenerationChange()
        )
    }

    internal fun reduce(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        transform: (DeviceRuntimeState) -> DeviceRuntimeState
    ): Boolean {
        var applied = false
        _states.update { current ->
            val state = current[deviceUid] ?: return@update current
            if (state.generation != generation) return@update current
            val updated = transform(state)
            require(updated.deviceUid == deviceUid) {
                "Runtime state reduction cannot change deviceUid."
            }
            require(updated.generation == generation) {
                "Runtime state reduction cannot change connection generation."
            }
            applied = true
            current + (deviceUid to updated)
        }
        return applied
    }

    fun remove(deviceUid: DeviceUid): Boolean {
        var removed = false
        _states.update { current ->
            removed = deviceUid in current
            current - deviceUid
        }
        return removed
    }

    fun clear() {
        _states.value = emptyMap()
    }
}

private fun <T> DeviceRuntimeValue<T>.staleForGenerationChange(): DeviceRuntimeValue<T> =
    copy(
        phase = if (value == null) {
            DeviceRuntimeFreshness.UNAVAILABLE
        } else {
            DeviceRuntimeFreshness.STALE
        },
        fault = null
    )
