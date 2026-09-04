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
    ): Boolean = authority.beginGeneration(deviceUid, generation)

    fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) = authority.invalidate(deviceUid, generation)

    fun isAuthoritative(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = authority.isAuthoritative(deviceUid, generation)

    fun currentAuthoritativeState(deviceUid: DeviceUid): DeviceCoolingRuntimeState? =
        synchronized(lock) {
            _states.value[deviceUid]?.takeIf { authority.isAuthoritative(deviceUid) }
        }

    fun recordStatus(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        status: DeviceCoolingV1StatusDocument
    ): Boolean {
        val config = runCatching(status::toConfigSnapshot).getOrNull() ?: return false
        val embeddedTelemetry = runCatching(status::toTelemetrySnapshot).getOrNull() ?: return false
        return synchronized(lock) {
            val wasAuthoritative = authority.isAuthoritative(deviceUid, generation)
            val current = _states.value[deviceUid]
            if (wasAuthoritative && current?.status?.isNewerThan(status) == true) {
                return@synchronized false
            }
            if (!authority.acceptAuthoritativeSnapshot(deviceUid, generation)) {
                return@synchronized false
            }
            val selectedTelemetry = current
                ?.telemetry
                ?.takeIf { telemetry ->
                    wasAuthoritative &&
                        telemetry.isCoherentWith(status) &&
                        telemetry.isNewerThan(embeddedTelemetry)
                }
                ?: embeddedTelemetry
            _states.value = _states.value + (
                deviceUid to DeviceCoolingRuntimeState(
                    status = status,
                    config = config,
                    telemetry = selectedTelemetry
                )
            )
            true
        }
    }

    fun recordTelemetry(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        telemetry: DeviceCoolingV1Telemetry
    ): Boolean = synchronized(lock) {
        if (!authority.acceptsPatch(deviceUid, generation)) return@synchronized false
        val current = _states.value[deviceUid] ?: return@synchronized false
        val status = current.status ?: return@synchronized false
        if (!telemetry.isCoherentWith(status)) return@synchronized false
        val previous = current.telemetry
        if (previous != null && !telemetry.isNewerThan(previous)) {
            return@synchronized false
        }
        _states.value = _states.value + (deviceUid to current.copy(telemetry = telemetry))
        true
    }

    /** Permanent owner cleanup only. Socket lifecycle uses [invalidate]. */
    fun clear(deviceUid: DeviceUid) = synchronized(lock) {
        if (deviceUid in _states.value) {
            _states.value = _states.value.toMutableMap().apply { remove(deviceUid) }.toMap()
        }
        authority.clear(deviceUid)
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
        programRevision == status.programRevision

private fun DeviceCoolingV1Telemetry.isNewerThan(
    previous: DeviceCoolingV1Telemetry
): Boolean = when {
    uptimeMs > previous.uptimeMs -> true
    uptimeMs < previous.uptimeMs -> false
    else -> decisionSequence > previous.decisionSequence
}
