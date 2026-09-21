package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadata

/**
 * Authenticated Light wire-surface negotiation for V1.
 *
 * Base Light V1 and Managed AUTO Plan remain the same commercial major. Managed-plan parsing and
 * commands are enabled only when firmware advertises the exact executable capability token.
 */
internal data class DeviceLightRuntimeAccess(
    val supportsApi: Boolean,
    val supportsManagedAutoPlan: Boolean
) {
    companion object {
        /** Preserves the finalized V1 assumption for isolated repository/parser tests. */
        val FINAL_V1 = DeviceLightRuntimeAccess(
            supportsApi = true,
            supportsManagedAutoPlan = true
        )

        val UNAVAILABLE = DeviceLightRuntimeAccess(
            supportsApi = false,
            supportsManagedAutoPlan = false
        )

        fun from(metadata: DeviceRuntimeMetadata?): DeviceLightRuntimeAccess {
            if (metadata == null) return UNAVAILABLE
            val capabilities = metadata.capabilities
            val features = capabilities.supportedFeatures
            val screens = capabilities.supportedScreens
            val supportsApi = metadata.identity.family == DeviceFamily.LIGHT &&
                capabilities.capabilities.light &&
                metadata.modules.light &&
                AqlDeviceFeatureKey.LIGHT_CONTROL in features &&
                AqlDeviceScreenKey.LIGHT_CONTROL in screens

            return DeviceLightRuntimeAccess(
                supportsApi = supportsApi,
                supportsManagedAutoPlan = supportsApi &&
                    AqlDeviceFeatureKey.LIGHT_MANAGED_AUTO_PLAN in features
            )
        }
    }
}
