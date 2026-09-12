package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.state.DeviceRuntimeGenerationAuthority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns only complete firmware-authoritative Light V1 and protection snapshots. */
internal class DeviceLightRuntimeStateStore {
    private val lock = Any()
    private val statusAuthority = DeviceRuntimeGenerationAuthority()
    private val temperatureProtectionAuthority = DeviceRuntimeGenerationAuthority()
    private val _statuses = MutableStateFlow<Map<DeviceUid, DeviceLightStatus>>(emptyMap())
    private val _temperatureProtection = MutableStateFlow<
        Map<DeviceUid, DeviceLightTemperatureProtectionStatus>
        >(emptyMap())

    val statuses: StateFlow<Map<DeviceUid, DeviceLightStatus>> = _statuses.asStateFlow()
    val temperatureProtection: StateFlow<Map<DeviceUid, DeviceLightTemperatureProtectionStatus>> =
        _temperatureProtection.asStateFlow()

    fun beginGeneration(deviceUid: DeviceUid, generation: DeviceRuntimeConnectionGeneration) {
        statusAuthority.beginGeneration(deviceUid, generation)
        temperatureProtectionAuthority.beginGeneration(deviceUid, generation)
    }

    fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) {
        statusAuthority.invalidate(deviceUid, generation)
        temperatureProtectionAuthority.invalidate(deviceUid, generation)
    }

    fun isStatusAuthoritative(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = statusAuthority.isAuthoritative(deviceUid, generation)

    fun isTemperatureProtectionAuthoritative(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = temperatureProtectionAuthority.isAuthoritative(deviceUid, generation)

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

    fun clear(deviceUid: DeviceUid) {
        synchronized(lock) {
            _statuses.value = _statuses.value.without(deviceUid)
            _temperatureProtection.value = _temperatureProtection.value.without(deviceUid)
            statusAuthority.clear(deviceUid)
            temperatureProtectionAuthority.clear(deviceUid)
        }
    }
}

private fun <T> Map<DeviceUid, T>.without(deviceUid: DeviceUid): Map<DeviceUid, T> =
    if (deviceUid !in this) this else toMutableMap().apply { remove(deviceUid) }.toMap()
