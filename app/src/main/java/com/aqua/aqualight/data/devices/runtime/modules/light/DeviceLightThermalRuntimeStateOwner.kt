package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.state.DeviceRuntimeGenerationAuthority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceLightThermalRuntimeState(
    val status: DeviceLightThermalStatus? = null,
    val telemetry: DeviceLightThermalTelemetry? = null
)

/** Single authoritative Light thermal owner above the exact V1 protocol boundary. */
internal class DeviceLightThermalRuntimeStateOwner {
    private val lock = Any()
    private val authority = DeviceRuntimeGenerationAuthority()
    private val _states = MutableStateFlow<Map<DeviceUid, DeviceLightThermalRuntimeState>>(emptyMap())
    val states: StateFlow<Map<DeviceUid, DeviceLightThermalRuntimeState>> = _states.asStateFlow()

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

    fun recordStatus(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        status: DeviceLightThermalStatus
    ): Boolean = synchronized(lock) {
        val current = _states.value[deviceUid]
        if (
            authority.isAuthoritative(deviceUid, generation) &&
            current?.status?.uptimeMs?.let { previous -> status.uptimeMs < previous } == true
        ) {
            return@synchronized false
        }
        if (!authority.acceptAuthoritativeSnapshot(deviceUid, generation)) {
            return@synchronized false
        }
        _states.value = _states.value + (
            deviceUid to DeviceLightThermalRuntimeState(
                status = status,
                telemetry = current?.telemetry?.takeIf { telemetry ->
                    telemetry.productKey == status.productKey &&
                        telemetry.uptimeMs >= status.uptimeMs
                }
            )
        )
        true
    }

    fun recordTelemetry(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        telemetry: DeviceLightThermalTelemetry
    ): Boolean = synchronized(lock) {
        if (!authority.acceptsPatch(deviceUid, generation)) return@synchronized false
        val current = _states.value[deviceUid] ?: return@synchronized false
        val status = current.status ?: return@synchronized false
        if (
            telemetry.productKey != status.productKey ||
            telemetry.uptimeMs < status.uptimeMs
        ) {
            return@synchronized false
        }
        val previous = current.telemetry
        if (
            previous != null &&
            (
                telemetry.uptimeMs < previous.uptimeMs ||
                    telemetry.temperature.sampledAtMs < previous.temperature.sampledAtMs
                )
        ) {
            return@synchronized false
        }
        _states.value = _states.value + (deviceUid to current.copy(telemetry = telemetry))
        true
    }

    fun clear(deviceUid: DeviceUid) = synchronized(lock) {
        if (deviceUid in _states.value) {
            _states.value = _states.value.toMutableMap().apply { remove(deviceUid) }.toMap()
        }
        authority.clear(deviceUid)
    }
}
