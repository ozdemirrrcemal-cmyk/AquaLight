package com.aqua.aqualight.data.devices.runtime.modules.cooling

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ConfigApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ConfigApplyResult
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1History
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1HistoryGetPayload
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ManualApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ManualApplyResult
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ProgramApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ProgramApplyResult
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ProgramSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ResponseParser
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1RuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1StatusDocument
import kotlinx.coroutines.flow.StateFlow

/**
 * Central Cooling runtime facade above the strict V1 protocol repository.
 *
 * No UI or screen lifecycle owns freshness. Initial hydration, typed events and mutation readback
 * converge here and publish only through [DeviceCoolingRuntimeStateOwner].
 */
class DeviceCoolingRuntimeRepository internal constructor(
    gateway: DeviceRuntimeCommandGateway,
    internal val stateOwner: DeviceCoolingRuntimeStateOwner = DeviceCoolingRuntimeStateOwner()
) {
    private val protocol = DeviceCoolingV1RuntimeRepository(gateway)

    val states: StateFlow<Map<DeviceUid, DeviceCoolingRuntimeState>> = stateOwner.states

    internal fun beginGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = stateOwner.beginGeneration(deviceUid, generation)

    internal fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) = stateOwner.invalidate(deviceUid, generation)

    internal fun clear(deviceUid: DeviceUid) = stateOwner.clear(deviceUid)

    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceCoolingV1StatusDocument> {
        val outcome = protocol.requestStatus(deviceUid)
        if (outcome is DeviceRuntimeCommandOutcome.Success) {
            stateOwner.recordStatus(deviceUid, outcome.generation, outcome.value)
        }
        return outcome
    }

    suspend fun applyConfig(
        deviceUid: DeviceUid,
        payload: DeviceCoolingV1ConfigApplyPayload
    ): DeviceRuntimeCommandOutcome<DeviceCoolingV1ConfigApplyResult> =
        protocol.applyConfig(deviceUid, payload).reconcileAfterMutation(deviceUid, ::requestStatus)

    suspend fun applyManual(
        deviceUid: DeviceUid,
        payload: DeviceCoolingV1ManualApplyPayload
    ): DeviceRuntimeCommandOutcome<DeviceCoolingV1ManualApplyResult> =
        protocol.applyManual(deviceUid, payload).reconcileAfterMutation(deviceUid, ::requestStatus)

    suspend fun requestProgram(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceCoolingV1ProgramSnapshot> =
        protocol.requestProgram(deviceUid)

    suspend fun applyProgram(
        deviceUid: DeviceUid,
        payload: DeviceCoolingV1ProgramApplyPayload
    ): DeviceRuntimeCommandOutcome<DeviceCoolingV1ProgramApplyResult> =
        protocol.applyProgram(deviceUid, payload).reconcileAfterMutation(deviceUid, ::requestStatus)

    suspend fun requestHistory(
        deviceUid: DeviceUid,
        payload: DeviceCoolingV1HistoryGetPayload
    ): DeviceRuntimeCommandOutcome<DeviceCoolingV1History> =
        protocol.requestHistory(deviceUid, payload)

    /** Typed firmware events are consumed centrally; presentation never initiates state refreshes. */
    internal suspend fun consume(event: DeviceRuntimeTypedEvent) {
        when (event.type) {
            DeviceRuntimeTypedEvent.Type.COOLING_STATUS_CHANGED -> consumeStatusEvent(event)
            DeviceRuntimeTypedEvent.Type.COOLING_TELEMETRY_CHANGED -> consumeTelemetryEvent(event)
            else -> Unit
        }
    }
}

internal fun DeviceCoolingRuntimeRepository.currentState(
    deviceUid: DeviceUid
): DeviceCoolingRuntimeState? = states.value[deviceUid]

internal fun DeviceCoolingRuntimeRepository.currentAuthoritativeState(
    deviceUid: DeviceUid
): DeviceCoolingRuntimeState? = stateOwner.currentAuthoritativeState(deviceUid)

internal fun DeviceCoolingRuntimeRepository.isAuthoritative(
    deviceUid: DeviceUid,
    generation: DeviceRuntimeConnectionGeneration
): Boolean = stateOwner.isAuthoritative(deviceUid, generation)

private suspend fun DeviceCoolingRuntimeRepository.consumeStatusEvent(event: DeviceRuntimeTypedEvent) {
    when (val payload = event.payload) {
        is DeviceRuntimeEventPayload.Snapshot -> runCatching {
            DeviceCoolingV1ResponseParser.parseStatus(payload.data)
        }.onSuccess { status ->
            stateOwner.recordStatus(event.deviceUid, event.generation, status)
        }.onFailure {
            requestStatus(event.deviceUid)
        }
        is DeviceRuntimeEventPayload.CommandResult -> requestStatus(event.deviceUid)
    }
}

private suspend fun DeviceCoolingRuntimeRepository.consumeTelemetryEvent(
    event: DeviceRuntimeTypedEvent
) {
    val telemetry = when (val payload = event.payload) {
        is DeviceRuntimeEventPayload.Snapshot -> runCatching {
            DeviceCoolingV1ResponseParser.parseTelemetry(payload.data)
        }.getOrNull()
        is DeviceRuntimeEventPayload.CommandResult -> null
    }
    if (telemetry != null && stateOwner.recordTelemetry(event.deviceUid, event.generation, telemetry)) {
        return
    }

    // Missing/stale baseline is reconciled automatically. An event from an older generation is
    // never replayed onto the newly hydrated state.
    val status = requestStatus(event.deviceUid)
    if (
        telemetry != null &&
        status is DeviceRuntimeCommandOutcome.Success &&
        status.generation == event.generation
    ) {
        stateOwner.recordTelemetry(event.deviceUid, event.generation, telemetry)
    }
}

private suspend fun <T> DeviceRuntimeCommandOutcome<T>.reconcileAfterMutation(
    deviceUid: DeviceUid,
    requestStatus: suspend (DeviceUid) -> DeviceRuntimeCommandOutcome<*>
): DeviceRuntimeCommandOutcome<T> {
    if (this is DeviceRuntimeCommandOutcome.Success) {
        requestStatus(deviceUid)
    }
    return this
}
