package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.state.DeviceRuntimeGenerationAuthority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns only complete firmware-authoritative Light V1 and protection snapshots. */
@Suppress("TooManyFunctions")
internal class DeviceLightRuntimeStateStore {
    private val lock = Any()
    private val statusAuthority = DeviceRuntimeGenerationAuthority()
    private val managedPlanAuthority = DeviceRuntimeGenerationAuthority()
    private val graphAuthority = DeviceRuntimeGenerationAuthority()
    private val temperatureProtectionAuthority = DeviceRuntimeGenerationAuthority()
    private val _statuses = MutableStateFlow<Map<DeviceUid, DeviceLightStatus>>(emptyMap())
    private val _managedPlans = MutableStateFlow<
        Map<DeviceUid, DeviceLightManagedPlanDocument>
        >(emptyMap())
    private val _graphs = MutableStateFlow<Map<DeviceUid, DeviceLightGraph>>(emptyMap())
    private val _temperatureProtection = MutableStateFlow<
        Map<DeviceUid, DeviceLightTemperatureProtectionStatus>
        >(emptyMap())

    val statuses: StateFlow<Map<DeviceUid, DeviceLightStatus>> = _statuses.asStateFlow()
    val managedPlans: StateFlow<Map<DeviceUid, DeviceLightManagedPlanDocument>> =
        _managedPlans.asStateFlow()
    val graphs: StateFlow<Map<DeviceUid, DeviceLightGraph>> = _graphs.asStateFlow()
    val temperatureProtection: StateFlow<Map<DeviceUid, DeviceLightTemperatureProtectionStatus>> =
        _temperatureProtection.asStateFlow()

    fun beginGeneration(deviceUid: DeviceUid, generation: DeviceRuntimeConnectionGeneration) {
        statusAuthority.beginGeneration(deviceUid, generation)
        managedPlanAuthority.beginGeneration(deviceUid, generation)
        graphAuthority.beginGeneration(deviceUid, generation)
        temperatureProtectionAuthority.beginGeneration(deviceUid, generation)
    }

    fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) {
        invalidateAuthoritySet(deviceUid, generation)
        temperatureProtectionAuthority.invalidate(deviceUid, generation)
    }

    /** Revokes only the status/plan/graph set that must be read as one Light authority unit. */
    fun invalidateAuthoritySet(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) {
        statusAuthority.invalidate(deviceUid, generation)
        managedPlanAuthority.invalidate(deviceUid, generation)
        graphAuthority.invalidate(deviceUid, generation)
    }

    fun isStatusAuthoritative(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = statusAuthority.isAuthoritative(deviceUid, generation)

    fun isTemperatureProtectionAuthoritative(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = temperatureProtectionAuthority.isAuthoritative(deviceUid, generation)

    fun isManagedPlanAuthoritative(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = managedPlanAuthority.isAuthoritative(deviceUid, generation)

    fun isGraphAuthoritative(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = graphAuthority.isAuthoritative(deviceUid, generation)

    fun currentAuthoritativeStatus(deviceUid: DeviceUid): DeviceLightStatus? = synchronized(lock) {
        _statuses.value[deviceUid]?.takeIf { statusAuthority.isAuthoritative(deviceUid) }
    }

    fun currentAuthoritativeManagedPlan(
        deviceUid: DeviceUid
    ): DeviceLightManagedPlanDocument? = synchronized(lock) {
        _managedPlans.value[deviceUid]?.takeIf {
            managedPlanAuthority.isAuthoritative(deviceUid)
        }
    }

    fun currentAuthoritativeGraph(deviceUid: DeviceUid): DeviceLightGraph? = synchronized(lock) {
        _graphs.value[deviceUid]?.takeIf { graphAuthority.isAuthoritative(deviceUid) }
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

    fun recordManagedPlan(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        document: DeviceLightManagedPlanDocument
    ): Boolean = synchronized(lock) {
        if (!managedPlanAuthority.acceptAuthoritativeSnapshot(deviceUid, generation)) {
            return@synchronized false
        }
        _managedPlans.value = _managedPlans.value + (deviceUid to document)
        true
    }

    fun recordGraph(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        graph: DeviceLightGraph
    ): Boolean = synchronized(lock) {
        if (!graphAuthority.acceptAuthoritativeSnapshot(deviceUid, generation)) {
            return@synchronized false
        }
        _graphs.value = _graphs.value + (deviceUid to graph)
        true
    }

    fun clear(deviceUid: DeviceUid) {
        synchronized(lock) {
            _statuses.value = _statuses.value.without(deviceUid)
            _managedPlans.value = _managedPlans.value.without(deviceUid)
            _graphs.value = _graphs.value.without(deviceUid)
            _temperatureProtection.value = _temperatureProtection.value.without(deviceUid)
            statusAuthority.clear(deviceUid)
            managedPlanAuthority.clear(deviceUid)
            graphAuthority.clear(deviceUid)
            temperatureProtectionAuthority.clear(deviceUid)
        }
    }
}

private fun <T> Map<DeviceUid, T>.without(deviceUid: DeviceUid): Map<DeviceUid, T> =
    if (deviceUid !in this) this else toMutableMap().apply { remove(deviceUid) }.toMap()
