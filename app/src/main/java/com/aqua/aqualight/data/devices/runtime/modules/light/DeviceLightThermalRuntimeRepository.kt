package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import kotlinx.coroutines.flow.StateFlow

/**
 * Central Light thermal runtime facade above the exact WRGB V1 protocol boundary.
 *
 * The raw V1 repository owns transport only. This facade owns automatic hydration, event
 * reconciliation and the one authoritative thermal state projection consumed by higher layers.
 */
class DeviceLightThermalRuntimeRepository internal constructor(
    gateway: DeviceRuntimeCommandGateway,
    private val stateOwner: DeviceLightThermalRuntimeStateOwner = DeviceLightThermalRuntimeStateOwner()
) {
    private val protocol = DeviceLightThermalV1RuntimeRepository(gateway)

    val states: StateFlow<Map<DeviceUid, DeviceLightThermalRuntimeState>> = stateOwner.states

    internal fun beginGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = stateOwner.beginGeneration(deviceUid, generation)

    internal fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) = stateOwner.invalidate(deviceUid, generation)

    internal fun clear(deviceUid: DeviceUid) = stateOwner.clear(deviceUid)

    internal fun isAuthoritative(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = stateOwner.isAuthoritative(deviceUid, generation)

    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceLightThermalStatus> {
        val outcome = protocol.requestStatus(deviceUid)
        if (outcome is DeviceRuntimeCommandOutcome.Success) {
            stateOwner.recordStatus(deviceUid, outcome.generation, outcome.value)
        }
        return outcome
    }

    suspend fun applyConfig(
        deviceUid: DeviceUid,
        payload: DeviceLightThermalConfigApplyPayload
    ): DeviceRuntimeCommandOutcome<DeviceLightThermalConfigApplyResult> {
        val outcome = protocol.applyConfig(deviceUid, payload)
        if (outcome is DeviceRuntimeCommandOutcome.Success) {
            if (!stateOwner.recordStatus(deviceUid, outcome.generation, outcome.value.status)) {
                requestStatus(deviceUid)
            }
        }
        return outcome
    }

    internal suspend fun consume(event: DeviceRuntimeTypedEvent) {
        when (event.type) {
            DeviceRuntimeTypedEvent.Type.LIGHT_THERMAL_STATUS_CHANGED -> consumeStatusEvent(event)
            DeviceRuntimeTypedEvent.Type.LIGHT_THERMAL_TELEMETRY_CHANGED -> consumeTelemetryEvent(event)
            else -> Unit
        }
    }

    private suspend fun consumeStatusEvent(event: DeviceRuntimeTypedEvent) {
        val status = when (val payload = event.payload) {
            is DeviceRuntimeEventPayload.Snapshot -> runCatching {
                DeviceLightThermalV1ResponseParser.parseStatus(payload.data)
            }.getOrNull()
            is DeviceRuntimeEventPayload.CommandResult -> when (payload.commandAction) {
                DeviceLightThermalV1Contract.Action.CONFIG_APPLY -> runCatching {
                    DeviceLightThermalV1ResponseParser.parseConfigApply(payload.result).status
                }.getOrNull()
                else -> null
            }
        }
        if (status == null || !stateOwner.recordStatus(event.deviceUid, event.generation, status)) {
            requestStatus(event.deviceUid)
        }
    }

    private suspend fun consumeTelemetryEvent(event: DeviceRuntimeTypedEvent) {
        val telemetry = when (val payload = event.payload) {
            is DeviceRuntimeEventPayload.Snapshot -> runCatching {
                DeviceLightThermalV1ResponseParser.parseTelemetry(payload.data)
            }.getOrNull()
            is DeviceRuntimeEventPayload.CommandResult -> null
        }
        if (telemetry != null && stateOwner.recordTelemetry(event.deviceUid, event.generation, telemetry)) {
            return
        }
        val status = requestStatus(event.deviceUid)
        if (
            telemetry != null &&
            status is DeviceRuntimeCommandOutcome.Success &&
            status.generation == event.generation
        ) {
            stateOwner.recordTelemetry(event.deviceUid, event.generation, telemetry)
        }
    }
}
