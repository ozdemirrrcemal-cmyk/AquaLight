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
                family = it.identity.family,
                capabilities = it.capabilities.capabilities,
                limits = it.capabilities.limits,
                features = it.capabilities.supportedFeatures,
                screens = it.capabilities.supportedScreens,
                modules = it.modules
            )
        } ?: UNAVAILABLE

        @Suppress("LongParameterList")
        fun resolve(
            family: DeviceFamily,
            capabilities: DeviceCapabilitySet,
            limits: DeviceLimitSet,
            features: Set<AqlDeviceFeatureKey>,
            screens: Set<AqlDeviceScreenKey>,
            modules: DeviceRuntimeModules
        ): DeviceTimerRuntimeAccess {
            val supportsApi = supportsStandaloneTimerApi(
                family,
                capabilities,
                limits,
                features,
                screens,
                modules
            )

            return DeviceTimerRuntimeAccess(
                supportsApi = supportsApi,
                channelCount = if (supportsApi) limits.timerChannelCount else 0,
                supportsSchedules = supportsApi &&
                    AqlDeviceScreenKey.TIMER_SCHEDULES in screens,
                supportsChannelState = supportsApi &&
                    AqlDeviceFeatureKey.TIMER_MANUAL_RUN in features &&
                    AqlDeviceScreenKey.TIMER_MANUAL_RUN in screens,
                supportsChannelDisplayName = supportsApi &&
                    AqlDeviceFeatureKey.TIMER_CHANNEL_DISPLAY_NAME in features &&
                    AqlDeviceScreenKey.TIMER_CHANNELS in screens
            )
        }

        @Suppress("LongParameterList")
        private fun supportsStandaloneTimerApi(
            family: DeviceFamily,
            capabilities: DeviceCapabilitySet,
            limits: DeviceLimitSet,
            features: Set<AqlDeviceFeatureKey>,
            screens: Set<AqlDeviceScreenKey>,
            modules: DeviceRuntimeModules
        ): Boolean = isStandaloneTimerProduct(family, capabilities, limits) &&
            hasStandaloneTimerModules(modules) &&
            exposesTimerSurface(features, screens)

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
