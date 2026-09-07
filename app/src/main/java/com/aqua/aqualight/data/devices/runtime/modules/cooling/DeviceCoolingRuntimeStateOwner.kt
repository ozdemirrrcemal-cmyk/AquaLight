package com.aqua.aqualight.data.devices.runtime.modules.cooling

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ConfigSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1StatusDocument
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Telemetry
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.toConfigSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.toTelemetrySnapshot
import com.aqua.aqualight.data.devices.runtime.state.DeviceRuntimeGenerationAuthority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceCoolingRuntimeState(
    val connectionGeneration: DeviceRuntimeConnectionGeneration? = null,
    val authoritative: Boolean = false,
    val status: DeviceCoolingV1StatusDocument? = null,
    val config: DeviceCoolingV1ConfigSnapshot? = null,
    val telemetry: DeviceCoolingV1Telemetry? = null
)

/**
 * The only authoritative mutable Cooling runtime owner.
 *
 * The strict V1 repository remains a protocol boundary. Correlated replies, typed events and
 * reconciliation all publish through this owner after connection-generation and revision ordering
 * are proven. Invalidation retains the last validated projection for presentation stability.
 */
internal class DeviceCoolingRuntimeStateOwner {
    private val lock = Any()
    private val authority = DeviceRuntimeGenerationAuthority()
    private val _states = MutableStateFlow<Map<DeviceUid, DeviceCoolingRuntimeState>>(emptyMap())
    val states: StateFlow<Map<DeviceUid, DeviceCoolingRuntimeState>> = _states.asStateFlow()

    fun beginGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = synchronized(lock) {
        if (!authority.beginGeneration(deviceUid, generation)) return@synchronized false
        val current = _states.value[deviceUid]
        val next = (current ?: DeviceCoolingRuntimeState()).copy(
            connectionGeneration = generation,
            authoritative = authority.isAuthoritative(deviceUid, generation)
        )
        if (next != current) publish(deviceUid, next)
        true
    }

    fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) = synchronized(lock) {
        authority.invalidate(deviceUid, generation)
        val current = _states.value[deviceUid] ?: return@synchronized
        if (
            generation == null ||
            current.connectionGeneration == generation
        ) {
            publish(deviceUid, current.copy(authoritative = false))
        }
    }

    fun isAuthoritative(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = authority.isAuthoritative(deviceUid, generation)

    fun currentAuthoritativeState(deviceUid: DeviceUid): DeviceCoolingRuntimeState? =
        synchronized(lock) {
            _states.value[deviceUid]?.takeIf { state ->
                state.authoritative &&
                    state.connectionGeneration?.let { generation ->
                        authority.isAuthoritative(deviceUid, generation)
                    } == true
            }
        }

    fun recordStatus(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        status: DeviceCoolingV1StatusDocument
    ): Boolean {
        val config = status.toConfigSnapshot()
        val embeddedTelemetry = status.toTelemetrySnapshot()
        return synchronized(lock) {
            val wasAuthoritative = authority.isAuthoritative(deviceUid, generation)
            val current = _states.value[deviceUid]
            val staleStatus = wasAuthoritative && current?.status?.isNewerThan(status) == true
            when {
                staleStatus -> false
                !authority.acceptAuthoritativeSnapshot(deviceUid, generation) -> false
                else -> {
                    val selectedTelemetry = current
                        ?.telemetry
                        ?.takeIf { telemetry ->
                            wasAuthoritative &&
                                telemetry.isCoherentWith(status) &&
                                telemetry.isNewerThan(embeddedTelemetry)
                        }
                        ?: embeddedTelemetry
                    publish(
                        deviceUid,
                        DeviceCoolingRuntimeState(
                            connectionGeneration = generation,
                            authoritative = true,
                            status = status,
                            config = config,
                            telemetry = selectedTelemetry
                        )
                    )
                    true
                }
            }
        }
    }

    fun recordTelemetry(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        telemetry: DeviceCoolingV1Telemetry
    ): Boolean = synchronized(lock) {
        if (!authority.acceptsPatch(deviceUid, generation)) return@synchronized false
        val current = _states.value[deviceUid] ?: return@synchronized false
        if (!current.authoritative || current.connectionGeneration != generation) {
            return@synchronized false
        }
        val status = current.status ?: return@synchronized false
        if (!telemetry.isCoherentWith(status)) return@synchronized false
        val previous = current.telemetry
        if (previous != null && !telemetry.isNewerThan(previous)) {
            return@synchronized false
        }
        publish(deviceUid, current.copy(telemetry = telemetry))
        true
    }

    /** Permanent owner cleanup only. Socket lifecycle uses [invalidate]. */
    fun clear(deviceUid: DeviceUid) = synchronized(lock) {
        if (deviceUid in _states.value) {
            _states.value = _states.value.toMutableMap().apply { remove(deviceUid) }.toMap()
        }
        authority.clear(deviceUid)
    }

    private fun publish(deviceUid: DeviceUid, state: DeviceCoolingRuntimeState) {
        _states.value = _states.value + (deviceUid to state)
    }
}

private fun DeviceCoolingV1StatusDocument.isNewerThan(
    incoming: DeviceCoolingV1StatusDocument
): Boolean = when {
    configRevision > incoming.configRevision -> true
    programRevision > incoming.programRevision -> true
    configRevision == incoming.configRevision &&
        programRevision == incoming.programRevision &&
        uptimeMs > incoming.uptimeMs -> true
    else -> false
}

/** Telemetry is projected only against the exact status document revisions that produced it. */
private fun DeviceCoolingV1Telemetry.isCoherentWith(
    status: DeviceCoolingV1StatusDocument
): Boolean =
    configRevision == status.configRevision &&
        programRevision == status.program.evaluatedProgramRevision

private fun DeviceCoolingV1Telemetry.isNewerThan(
    previous: DeviceCoolingV1Telemetry
): Boolean = when {
    uptimeMs > previous.uptimeMs -> true
    uptimeMs < previous.uptimeMs -> false
    else -> decisionSequence > previous.decisionSequence
}
