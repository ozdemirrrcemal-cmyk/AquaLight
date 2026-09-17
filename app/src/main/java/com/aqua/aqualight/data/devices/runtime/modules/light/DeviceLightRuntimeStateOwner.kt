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

/**
 * The only mutable, firmware-authoritative Light state owner.
 *
 * Main Light status, temperature protection and thermal documents have independent generation
 * authorities because firmware hydrates them through separate commands. They still publish through
 * this one owner so no Light adapter can retain a parallel authoritative snapshot.
 */
internal class DeviceLightRuntimeStateOwner {
    private val lock = Any()
    private val statusAuthority = DeviceRuntimeGenerationAuthority()
    private val temperatureProtectionAuthority = DeviceRuntimeGenerationAuthority()
    private val thermalAuthority = DeviceRuntimeGenerationAuthority()
    private val _statuses = MutableStateFlow<Map<DeviceUid, DeviceLightStatus>>(emptyMap())
    private val _temperatureProtection = MutableStateFlow<
        Map<DeviceUid, DeviceLightTemperatureProtectionStatus>
        >(emptyMap())
    private val _thermalStates = MutableStateFlow<
        Map<DeviceUid, DeviceLightThermalRuntimeState>
        >(emptyMap())

    val statuses: StateFlow<Map<DeviceUid, DeviceLightStatus>> = _statuses.asStateFlow()
    val temperatureProtection: StateFlow<Map<DeviceUid, DeviceLightTemperatureProtectionStatus>> =
        _temperatureProtection.asStateFlow()
    val thermalStates: StateFlow<Map<DeviceUid, DeviceLightThermalRuntimeState>> =
        _thermalStates.asStateFlow()

    fun beginGeneration(deviceUid: DeviceUid, generation: DeviceRuntimeConnectionGeneration) {
        statusAuthority.beginGeneration(deviceUid, generation)
        temperatureProtectionAuthority.beginGeneration(deviceUid, generation)
        thermalAuthority.beginGeneration(deviceUid, generation)
    }

    fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) {
        statusAuthority.invalidate(deviceUid, generation)
        temperatureProtectionAuthority.invalidate(deviceUid, generation)
        thermalAuthority.invalidate(deviceUid, generation)
    }

    fun isStatusAuthoritative(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = statusAuthority.isAuthoritative(deviceUid, generation)

    fun isTemperatureProtectionAuthoritative(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = temperatureProtectionAuthority.isAuthoritative(deviceUid, generation)

    fun isThermalAuthoritative(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = thermalAuthority.isAuthoritative(deviceUid, generation)

    fun currentAuthoritativeStatus(deviceUid: DeviceUid): DeviceLightStatus? = synchronized(lock) {
        _statuses.value[deviceUid]?.takeIf { statusAuthority.isAuthoritative(deviceUid) }
    }

    fun currentAuthoritativeTemperatureProtection(
        deviceUid: DeviceUid
    ): DeviceLightTemperatureProtectionStatus? = synchronized(lock) {
        _temperatureProtection.value[deviceUid]
            ?.takeIf { temperatureProtectionAuthority.isAuthoritative(deviceUid) }
    }

    fun recordStatus(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        status: DeviceLightStatus
    ): Boolean = synchronized(lock) {
        if (!statusAuthority.acceptAuthoritativeSnapshot(deviceUid, generation)) {
            return@synchronized false
        }
        _statuses.value = _statuses.value + (deviceUid to status)
        true
    }

    fun recordTemperatureProtection(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        status: DeviceLightTemperatureProtectionStatus
    ): Boolean = synchronized(lock) {
        if (!temperatureProtectionAuthority.acceptAuthoritativeSnapshot(deviceUid, generation)) {
            return@synchronized false
        }
        _temperatureProtection.value = _temperatureProtection.value + (deviceUid to status)
        true
    }

    fun recordThermalStatus(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        status: DeviceLightThermalStatus
    ): Boolean = synchronized(lock) {
        val current = _thermalStates.value[deviceUid]
        if (
            thermalAuthority.isAuthoritative(deviceUid, generation) &&
            current?.status?.uptimeMs?.let { previous -> status.uptimeMs < previous } == true
        ) {
            return@synchronized false
        }
        if (!thermalAuthority.acceptAuthoritativeSnapshot(deviceUid, generation)) {
            return@synchronized false
        }
        _thermalStates.value = _thermalStates.value + (
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

    fun recordThermalTelemetry(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        telemetry: DeviceLightThermalTelemetry
    ): Boolean = synchronized(lock) {
        if (!thermalAuthority.acceptsPatch(deviceUid, generation)) return@synchronized false
        val current = _thermalStates.value[deviceUid] ?: return@synchronized false
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
        _thermalStates.value = _thermalStates.value + (deviceUid to current.copy(telemetry = telemetry))
        true
    }

    fun clear(deviceUid: DeviceUid) {
        synchronized(lock) {
            _statuses.value = _statuses.value.without(deviceUid)
            _temperatureProtection.value = _temperatureProtection.value.without(deviceUid)
            _thermalStates.value = _thermalStates.value.without(deviceUid)
            statusAuthority.clear(deviceUid)
            temperatureProtectionAuthority.clear(deviceUid)
            thermalAuthority.clear(deviceUid)
        }
    }
}

private fun <T> Map<DeviceUid, T>.without(deviceUid: DeviceUid): Map<DeviceUid, T> =
    if (deviceUid !in this) this else toMutableMap().apply { remove(deviceUid) }.toMap()
