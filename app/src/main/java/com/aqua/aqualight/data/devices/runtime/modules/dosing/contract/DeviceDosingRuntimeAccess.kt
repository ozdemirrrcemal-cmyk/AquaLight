package com.aqua.aqualight.data.devices.runtime.modules.dosing.contract

import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey
import com.aqua.aqualight.data.devices.model.DeviceCapabilitySet
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceLimitSet
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadata
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModules

/** Validated commercial metadata gate for the Dosing API backed by the internal timer engine. */
internal data class DeviceDosingRuntimeAccess(
    val supportsApi: Boolean,
    val channelCount: Int,
    val supportsSchedules: Boolean,
    val supportsPrime: Boolean,
    val supportsManualDose: Boolean,
    val supportsCalibrationWorkflow: Boolean,
    val supportsReservoirRefill: Boolean,
    val supportsChannelDisplayName: Boolean
) {
    companion object {
        val UNAVAILABLE = DeviceDosingRuntimeAccess(
            supportsApi = false,
            channelCount = 0,
            supportsSchedules = false,
            supportsPrime = false,
            supportsManualDose = false,
            supportsCalibrationWorkflow = false,
            supportsReservoirRefill = false,
            supportsChannelDisplayName = false
        )

        fun from(metadata: DeviceRuntimeMetadata?): DeviceDosingRuntimeAccess = metadata?.let {
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
        ): DeviceDosingRuntimeAccess {
            val supportsApi = supportsDosingApi(
                family,
                capabilities,
                limits,
                features,
                screens,
                modules
            )
            val supportsManualSurface = supportsApi &&
                AqlDeviceScreenKey.DOSING_MANUAL_RUN in screens

            return DeviceDosingRuntimeAccess(
                supportsApi = supportsApi,
                channelCount = if (supportsApi) limits.dosingChannelCount else 0,
                supportsSchedules = supportsApi &&
                    AqlDeviceScreenKey.DOSING_SCHEDULES in screens,
                supportsPrime = supportsManualSurface,
                supportsManualDose = supportsManualSurface,
                supportsCalibrationWorkflow = supportsApi &&
                    AqlDeviceFeatureKey.DOSING_CALIBRATION in features &&
                    AqlDeviceScreenKey.DOSING_CALIBRATION in screens,
                supportsReservoirRefill = supportsApi &&
                    AqlDeviceFeatureKey.DOSING_RESERVOIR_TRACKING in features &&
                    AqlDeviceScreenKey.DOSING_RESERVOIR in screens,
                supportsChannelDisplayName = supportsApi &&
                    AqlDeviceFeatureKey.DOSING_CHANNEL_DISPLAY_NAME in features &&
                    AqlDeviceScreenKey.DOSING_CHANNELS in screens
            )
        }

        @Suppress("LongParameterList")
        private fun supportsDosingApi(
            family: DeviceFamily,
            capabilities: DeviceCapabilitySet,
            limits: DeviceLimitSet,
            features: Set<AqlDeviceFeatureKey>,
            screens: Set<AqlDeviceScreenKey>,
            modules: DeviceRuntimeModules
        ): Boolean = family == DeviceFamily.DOSING &&
            capabilities.dosing &&
            !capabilities.standaloneTimer &&
            limits.dosingChannelCount in 1..DeviceDosingRuntimeContract.Limit.MAX_CHANNELS &&
            limits.timerChannelCount == 0 &&
            modules.dosing &&
            modules.timerEngine &&
            !modules.timerApi &&
            AqlDeviceFeatureKey.DOSING_CONTROL in features &&
            AqlDeviceScreenKey.DOSING_CONTROL in screens
    }
}
