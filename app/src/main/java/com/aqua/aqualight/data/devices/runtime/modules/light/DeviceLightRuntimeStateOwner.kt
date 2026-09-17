package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
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
 * Main Light status, temperature protection and thermal documents publish through this aggregate
 * so no Light adapter can retain a parallel authoritative snapshot. Their independent connection
 * generation lifecycles are delegated to [DeviceLightRuntimeAuthorityCoordinator].
 */
internal class DeviceLightRuntimeStateOwner {
    private val lock = Any()
    private val authorityCoordinator = DeviceLightRuntimeAuthorityCoordinator()
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
        authorityCoordinator.beginGeneration(deviceUid, generation)
    }

    fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) {
        authorityCoordinator.invalidate(deviceUid, generation)
    }

    fun isAuthoritative(
        projection: DeviceLightRuntimeProjection,
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = authorityCoordinator.isAuthoritative(projection, deviceUid, generation)

    fun currentAuthoritativeStatus(deviceUid: DeviceUid): DeviceLightStatus? = synchronized(lock) {
        _statuses.value[deviceUid]?.takeIf {
            authorityCoordinator.isCurrentlyAuthoritative(
                DeviceLightRuntimeProjection.STATUS,
                deviceUid
            )
        }
    }

    fun currentAuthoritativeTemperatureProtection(
        deviceUid: DeviceUid
    ): DeviceLightTemperatureProtectionStatus? = synchronized(lock) {
        _temperatureProtection.value[deviceUid]
            ?.takeIf {
                authorityCoordinator.isCurrentlyAuthoritative(
                    DeviceLightRuntimeProjection.TEMPERATURE_PROTECTION,
                    deviceUid
                )
            }
    }

    fun recordStatus(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        status: DeviceLightStatus
    ): Boolean = synchronized(lock) {
        if (
            !authorityCoordinator.acceptAuthoritativeSnapshot(
                DeviceLightRuntimeProjection.STATUS,
                deviceUid,
                generation
            )
        ) {
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
        if (
            !authorityCoordinator.acceptAuthoritativeSnapshot(
                DeviceLightRuntimeProjection.TEMPERATURE_PROTECTION,
                deviceUid,
                generation
            )
        ) {
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
            authorityCoordinator.isAuthoritative(
                DeviceLightRuntimeProjection.THERMAL,
                deviceUid,
                generation
            ) &&
            current?.status?.uptimeMs?.let { previous -> status.uptimeMs < previous } == true
        ) {
            return@synchronized false
        }
        if (
            !authorityCoordinator.acceptAuthoritativeSnapshot(
                DeviceLightRuntimeProjection.THERMAL,
                deviceUid,
                generation
            )
        ) {
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
        if (
            !authorityCoordinator.acceptsPatch(
                DeviceLightRuntimeProjection.THERMAL,
                deviceUid,
                generation
            )
        ) {
            return@synchronized false
        }
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
            authorityCoordinator.clear(deviceUid)
        }
    }
}

private fun <T> Map<DeviceUid, T>.without(deviceUid: DeviceUid): Map<DeviceUid, T> =
    if (deviceUid !in this) this else toMutableMap().apply { remove(deviceUid) }.toMap()
