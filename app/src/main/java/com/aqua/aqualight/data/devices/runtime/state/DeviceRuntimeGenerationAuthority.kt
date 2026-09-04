package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration

/**
 * Small lifecycle authority primitive shared by runtime domains.
 *
 * It deliberately owns no domain payload. Domain owners keep their last validated presentation
 * snapshot while this ledger decides whether a reply/event may mutate that snapshot and whether
 * the resulting state is authoritative for the current socket generation.
 */
internal class DeviceRuntimeGenerationAuthority {
    private val lock = Any()
    private val states = HashMap<DeviceUid, DeviceRuntimeGenerationAuthorityState>()

    fun beginGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = synchronized(lock) {
        val current = states[deviceUid]
        when {
            current == null -> {
                states[deviceUid] = DeviceRuntimeGenerationAuthorityState(
                    generation = generation,
                    authoritative = false
                )
                true
            }
            generation.value < current.generation.value -> false
            generation.value > current.generation.value -> {
                states[deviceUid] = DeviceRuntimeGenerationAuthorityState(
                    generation = generation,
                    authoritative = false
                )
                true
            }
            else -> true
        }
    }

    /** Accepts a complete domain snapshot and makes that generation authoritative. */
    fun acceptAuthoritativeSnapshot(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = synchronized(lock) {
        val current = states[deviceUid]
        if (current != null && generation.value < current.generation.value) {
            return@synchronized false
        }
        states[deviceUid] = DeviceRuntimeGenerationAuthorityState(
            generation = generation,
            authoritative = true
        )
        true
    }

    /** Partial mutations/events may only patch an already authoritative baseline. */
    fun acceptsPatch(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = synchronized(lock) {
        states[deviceUid]?.let { state ->
            state.generation == generation && state.authoritative
        } == true
    }

    fun isAuthoritative(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = acceptsPatch(deviceUid, generation)

    /** Revokes write/read authority without destroying the last presentation snapshot. */
    fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) = synchronized(lock) {
        val current = states[deviceUid] ?: return@synchronized
        if (generation != null && current.generation != generation) return@synchronized
        states[deviceUid] = current.copy(authoritative = false)
    }

    fun clear(deviceUid: DeviceUid) = synchronized(lock) {
        states.remove(deviceUid)
    }
}

private data class DeviceRuntimeGenerationAuthorityState(
    val generation: DeviceRuntimeConnectionGeneration,
    val authoritative: Boolean
)
