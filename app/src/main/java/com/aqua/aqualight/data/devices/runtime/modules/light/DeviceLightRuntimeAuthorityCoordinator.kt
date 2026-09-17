package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.state.DeviceRuntimeGenerationAuthority

/** Independently tracks connection-generation authority for each Light runtime projection. */
internal class DeviceLightRuntimeAuthorityCoordinator {
    private val authorities = DeviceLightRuntimeProjection.entries.associateWith {
        DeviceRuntimeGenerationAuthority()
    }

    fun beginGeneration(deviceUid: DeviceUid, generation: DeviceRuntimeConnectionGeneration) {
        authorities.values.forEach { authority ->
            authority.beginGeneration(deviceUid, generation)
        }
    }

    fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) {
        authorities.values.forEach { authority ->
            authority.invalidate(deviceUid, generation)
        }
    }

    fun isAuthoritative(
        projection: DeviceLightRuntimeProjection,
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = authorityFor(projection).isAuthoritative(deviceUid, generation)

    fun isCurrentlyAuthoritative(
        projection: DeviceLightRuntimeProjection,
        deviceUid: DeviceUid
    ): Boolean = authorityFor(projection).isAuthoritative(deviceUid)

    fun acceptAuthoritativeSnapshot(
        projection: DeviceLightRuntimeProjection,
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = authorityFor(projection).acceptAuthoritativeSnapshot(deviceUid, generation)

    fun acceptsPatch(
        projection: DeviceLightRuntimeProjection,
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = authorityFor(projection).acceptsPatch(deviceUid, generation)

    fun clear(deviceUid: DeviceUid) {
        authorities.values.forEach { authority -> authority.clear(deviceUid) }
    }

    private fun authorityFor(
        projection: DeviceLightRuntimeProjection
    ): DeviceRuntimeGenerationAuthority = authorities.getValue(projection)
}

internal enum class DeviceLightRuntimeProjection {
    STATUS,
    TEMPERATURE_PROTECTION,
    THERMAL
}
