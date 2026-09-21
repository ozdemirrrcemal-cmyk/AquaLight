package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadata

/**
 * Negotiated Light V1 runtime surface derived only from authenticated device metadata.
 *
 * The base Light V1 surface and its managed-AUTO-plan extension share the V1 namespace because the
 * application has not shipped a public legacy yet. Exact payload parsing is still selected by this
 * capability: a device that does not advertise LIGHT_MANAGED_AUTO_PLAN must send the exact base V1
 * shape; a device that advertises it must send the exact finalized managed-plan V1 shape.
 */
internal data class DeviceLightRuntimeAccess(
    val supportsApi: Boolean,
    val supportsManagedAutoPlan: Boolean
) {
    companion object {
        val UNAVAILABLE = DeviceLightRuntimeAccess(
            supportsApi = false,
            supportsManagedAutoPlan = false
        )

        fun from(metadata: DeviceRuntimeMetadata?): DeviceLightRuntimeAccess = metadata?.let {
            val baseSupported =
                it.identity.family == DeviceFamily.LIGHT &&
                    it.capabilities.capabilities.light &&
                    it.capabilities.limits.lightChannelCount > 0 &&
                    it.modules.light &&
                    AqlDeviceFeatureKey.LIGHT_CONTROL in it.capabilities.supportedFeatures &&
                    AqlDeviceScreenKey.LIGHT_CONTROL in it.capabilities.supportedScreens
            DeviceLightRuntimeAccess(
                supportsApi = baseSupported,
                supportsManagedAutoPlan = baseSupported &&
                    AqlDeviceFeatureKey.LIGHT_MANAGED_AUTO_PLAN in
                    it.capabilities.supportedFeatures
            )
        } ?: UNAVAILABLE
    }
}
