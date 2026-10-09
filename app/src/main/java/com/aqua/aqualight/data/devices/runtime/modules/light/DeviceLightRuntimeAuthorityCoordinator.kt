package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.state.DeviceRuntimeGenerationAuthority

/** Read-only authority queries do not expose lifecycle mutation to owner extensions. */
internal interface DeviceLightRuntimeAuthorityReadAccess {
    fun currentGeneration(
        projection: DeviceLightRuntimeProjection,
        deviceUid: DeviceUid
    ): DeviceRuntimeConnectionGeneration?

    fun isAuthoritative(
        projection: DeviceLightRuntimeProjection,
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean
}

/** Independently tracks connection-generation authority for each Light runtime projection. */
internal class DeviceLightRuntimeAuthorityCoordinator : DeviceLightRuntimeAuthorityReadAccess {
    private val authorities = DeviceLightRuntimeProjection.entries.associateWith {
        DeviceRuntimeGenerationAuthority()
    }

    fun beginGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = authorities.values
        .map { authority -> authority.beginGeneration(deviceUid, generation) }
        .also { accepted -> check(accepted.distinct().size == 1) }
        .first()

    fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) {
        authorities.values.forEach { authority ->
            authority.invalidate(deviceUid, generation)
        }
    }

    override fun isAuthoritative(
        projection: DeviceLightRuntimeProjection,
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = authorities.getValue(projection).isAuthoritative(deviceUid, generation)

    override fun currentGeneration(
        projection: DeviceLightRuntimeProjection,
        deviceUid: DeviceUid
    ): DeviceRuntimeConnectionGeneration? = authorities.getValue(projection).currentGeneration(deviceUid)

    fun isCurrentlyAuthoritative(
        projection: DeviceLightRuntimeProjection,
        deviceUid: DeviceUid
    ): Boolean = authorities.getValue(projection).isAuthoritative(deviceUid)

    fun isCurrentGeneration(
        projection: DeviceLightRuntimeProjection,
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = authorities.getValue(projection).isCurrentGeneration(deviceUid, generation)

    fun acceptAuthoritativeSnapshot(
        projection: DeviceLightRuntimeProjection,
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = authorities.getValue(projection).acceptAuthoritativeSnapshot(deviceUid, generation)

    fun acceptsPatch(
        projection: DeviceLightRuntimeProjection,
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = authorities.getValue(projection).acceptsPatch(deviceUid, generation)

    fun invalidateProjection(
        projection: DeviceLightRuntimeProjection,
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) {
        authorities.getValue(projection).invalidate(deviceUid, generation)
    }

    fun clear(deviceUid: DeviceUid) {
        authorities.values.forEach { authority -> authority.clear(deviceUid) }
    }
}

internal enum class DeviceLightRuntimeProjection {
    STATUS,
    GRAPH,
    AUTO_PROGRAMS,
    AUTO_PLAN,
    CUSTOM,
    TEMPERATURE_PROTECTION,
    THERMAL
}

/** Read-only owner queries share the same projection authority ledger. */
internal fun DeviceLightRuntimeStateOwner.currentGeneration(
    deviceUid: DeviceUid,
    projection: DeviceLightRuntimeProjection = DeviceLightRuntimeProjection.STATUS
): DeviceRuntimeConnectionGeneration? = authorityQueries.currentGeneration(projection, deviceUid)

internal fun DeviceLightRuntimeStateOwner.isAuthoritative(
    projection: DeviceLightRuntimeProjection,
    deviceUid: DeviceUid,
    generation: DeviceRuntimeConnectionGeneration
): Boolean = authorityQueries.isAuthoritative(projection, deviceUid, generation)
