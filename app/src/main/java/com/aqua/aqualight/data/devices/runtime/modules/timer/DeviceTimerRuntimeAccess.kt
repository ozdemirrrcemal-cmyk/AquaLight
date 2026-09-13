package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey
import com.aqua.aqualight.data.devices.model.DeviceCapabilitySet
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceLimitSet
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadata
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModules

/** Validated metadata gate separating standalone Timer products from the dosing timer engine. */
internal data class DeviceTimerRuntimeAccess(
    val supportsApi: Boolean,
    val channelCount: Int,
    val supportsSchedules: Boolean,
    val supportsChannelState: Boolean,
    val supportsChannelDisplayName: Boolean
) {
    companion object {
        val UNAVAILABLE = DeviceTimerRuntimeAccess(
            supportsApi = false,
            channelCount = 0,
            supportsSchedules = false,
            supportsChannelState = false,
            supportsChannelDisplayName = false
        )

        fun from(metadata: DeviceRuntimeMetadata?): DeviceTimerRuntimeAccess = metadata?.let {
            resolve(
                DeviceTimerRuntimeDescriptor(
                    family = it.identity.family,
                    capabilities = it.capabilities.capabilities,
                    limits = it.capabilities.limits,
                    features = it.capabilities.supportedFeatures,
                    screens = it.capabilities.supportedScreens,
                    modules = it.modules
                )
            )
        } ?: UNAVAILABLE

        fun resolve(descriptor: DeviceTimerRuntimeDescriptor): DeviceTimerRuntimeAccess {
            val supportsApi = supportsStandaloneTimerApi(descriptor)

            return DeviceTimerRuntimeAccess(
                supportsApi = supportsApi,
                channelCount = if (supportsApi) descriptor.limits.timerChannelCount else 0,
                supportsSchedules = supportsApi &&
                    AqlDeviceScreenKey.TIMER_SCHEDULES in descriptor.screens,
                supportsChannelState = supportsApi &&
                    AqlDeviceFeatureKey.TIMER_MANUAL_RUN in descriptor.features &&
                    AqlDeviceScreenKey.TIMER_MANUAL_RUN in descriptor.screens,
                supportsChannelDisplayName = supportsApi &&
                    AqlDeviceFeatureKey.TIMER_CHANNEL_DISPLAY_NAME in descriptor.features &&
                    AqlDeviceScreenKey.TIMER_CHANNELS in descriptor.screens
            )
        }

        private fun supportsStandaloneTimerApi(
            descriptor: DeviceTimerRuntimeDescriptor
        ): Boolean = isStandaloneTimerProduct(
            descriptor.family,
            descriptor.capabilities,
            descriptor.limits
        ) && hasStandaloneTimerModules(descriptor.modules) &&
            exposesTimerSurface(descriptor.features, descriptor.screens)

        private fun isStandaloneTimerProduct(
            family: DeviceFamily,
            capabilities: DeviceCapabilitySet,
            limits: DeviceLimitSet
        ): Boolean = family == DeviceFamily.TIMER &&
            capabilities.standaloneTimer &&
            !capabilities.dosing &&
            limits.timerChannelCount in 1..DeviceTimerRuntimeContract.Limit.MAX_CHANNELS &&
            limits.dosingChannelCount == 0

        private fun hasStandaloneTimerModules(
            modules: DeviceRuntimeModules
        ): Boolean = modules.timerApi &&
            modules.timerEngine &&
            !modules.dosing

        private fun exposesTimerSurface(
            features: Set<AqlDeviceFeatureKey>,
            screens: Set<AqlDeviceScreenKey>
        ): Boolean = AqlDeviceFeatureKey.TIMER_CONTROL in features &&
            AqlDeviceScreenKey.TIMER_CONTROL in screens
    }
}

internal data class DeviceTimerRuntimeDescriptor(
    val family: DeviceFamily,
    val capabilities: DeviceCapabilitySet,
    val limits: DeviceLimitSet,
    val features: Set<AqlDeviceFeatureKey>,
    val screens: Set<AqlDeviceScreenKey>,
    val modules: DeviceRuntimeModules
)
