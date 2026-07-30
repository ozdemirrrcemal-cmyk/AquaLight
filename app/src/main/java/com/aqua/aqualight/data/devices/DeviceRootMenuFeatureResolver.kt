package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey
import com.aqua.aqualight.data.devices.model.DeviceFamily

internal object DeviceRootMenuFeatureResolver {

    fun resolve(product: AqlCommercialCatalogProduct): Set<DeviceRootMenuFeature> {
        val support = MenuSupport(product)
        return when (product.family) {
            DeviceFamily.LIGHT -> support.resolveLight()
            DeviceFamily.TIMER -> support.resolveTimer()
            DeviceFamily.DOSING -> support.resolveDosing()
            DeviceFamily.COOLING -> support.resolveCooling()
            DeviceFamily.UNKNOWN -> emptySet()
        }
    }

    private class MenuSupport(
        product: AqlCommercialCatalogProduct
    ) {
        private val capabilities = product.profile.capabilities
        private val features = product.profile.supportedFeatures
        private val screens = product.profile.supportedScreens
        private val limits = product.limits

        private val hasLightHardware = capabilities.light && limits.lightChannelCount > 0
        private val hasTimerHardware = capabilities.standaloneTimer && limits.timerChannelCount > 0
        private val hasDosingHardware = capabilities.dosing && limits.dosingChannelCount > 0
        private val hasFanHardware = capabilities.cooling && capabilities.fan && limits.fanOutputCount > 0
        private val hasTemperatureHardware = capabilities.temperature &&
            limits.temperatureSensorCount > 0

        private val hasLightControlContract = AqlDeviceFeatureKey.LIGHT_CONTROL in features &&
            AqlDeviceScreenKey.LIGHT_CONTROL in screens
        private val hasLightQuickSetupContract =
            AqlDeviceFeatureKey.LIGHT_QUICK_SETUP in features &&
                AqlDeviceScreenKey.LIGHT_QUICK_SETUP in screens
        private val hasLightPresetContract = AqlDeviceFeatureKey.LIGHT_PRESETS in features &&
            AqlDeviceScreenKey.LIGHT_PRESETS in screens
        private val hasLightFanContract = AqlDeviceFeatureKey.LIGHT_FAN_CONTROL in features &&
            AqlDeviceScreenKey.LIGHT_FAN_CONTROL in screens
        private val hasLightTemperatureContract =
            AqlDeviceFeatureKey.LIGHT_TEMPERATURE_PROTECTION in features &&
                AqlDeviceScreenKey.LIGHT_TEMPERATURE_PROTECTION in screens
        private val hasTimerChannelContract = AqlDeviceFeatureKey.TIMER_CONTROL in features &&
            AqlDeviceScreenKey.TIMER_CONTROL in screens &&
            AqlDeviceScreenKey.TIMER_CHANNELS in screens
        private val hasDosingChannelContract = AqlDeviceFeatureKey.DOSING_CONTROL in features &&
            AqlDeviceScreenKey.DOSING_CONTROL in screens &&
            AqlDeviceScreenKey.DOSING_CHANNELS in screens
        private val hasDosingCalibrationContract =
            AqlDeviceFeatureKey.DOSING_CALIBRATION in features &&
                AqlDeviceScreenKey.DOSING_CALIBRATION in screens
        private val hasCoolingFanContract = AqlDeviceFeatureKey.COOLING_CONTROL in features &&
            AqlDeviceScreenKey.COOLING_CONTROL in screens &&
            AqlDeviceScreenKey.COOLING_FANS in screens
        private val hasCoolingTemperatureContract =
            AqlDeviceFeatureKey.TEMPERATURE_READ in features &&
                AqlDeviceScreenKey.COOLING_RULES in screens &&
                AqlDeviceScreenKey.COOLING_SENSOR_STATUS in screens

        fun resolveLight(): Set<DeviceRootMenuFeature> = buildSet {
            addIf(
                DeviceRootMenuFeature.LIGHT_MANUAL,
                hasLightHardware && capabilities.manualLight && hasLightControlContract
            )
            addIf(
                DeviceRootMenuFeature.LIGHT_QUICK_SETUP,
                capabilities.light && hasLightQuickSetupContract
            )
            addIf(
                DeviceRootMenuFeature.LIGHT_PROGRAMS,
                hasLightHardware &&
                    capabilities.lightProgram &&
                    AqlDeviceScreenKey.LIGHT_SCHEDULE in screens
            )
            addIf(
                DeviceRootMenuFeature.LIGHT_PRESETS,
                capabilities.light && capabilities.lightPresets && hasLightPresetContract
            )
            addIf(
                DeviceRootMenuFeature.COOLING_FANS,
                capabilities.light && hasFanHardware && hasLightFanContract
            )
            addIf(
                DeviceRootMenuFeature.COOLING_TEMPERATURE,
                capabilities.light && hasTemperatureHardware && hasLightTemperatureContract
            )
            addSettingsIfSupported()
        }

        fun resolveTimer(): Set<DeviceRootMenuFeature> = buildSet {
            addIf(
                DeviceRootMenuFeature.TIMER_CHANNELS,
                hasTimerHardware && hasTimerChannelContract
            )
            addIf(
                DeviceRootMenuFeature.TIMER_SCHEDULES,
                hasTimerHardware && AqlDeviceScreenKey.TIMER_SCHEDULES in screens
            )
            addSettingsIfSupported()
        }

        fun resolveDosing(): Set<DeviceRootMenuFeature> = buildSet {
            addIf(
                DeviceRootMenuFeature.DOSING_CHANNELS,
                hasDosingHardware && hasDosingChannelContract
            )
            addIf(
                DeviceRootMenuFeature.DOSING_CALIBRATION,
                capabilities.dosing && hasDosingCalibrationContract
            )
            addIf(
                DeviceRootMenuFeature.DOSING_SCHEDULES,
                hasDosingHardware && AqlDeviceScreenKey.DOSING_SCHEDULES in screens
            )
            addSettingsIfSupported()
        }

        fun resolveCooling(): Set<DeviceRootMenuFeature> = buildSet {
            addIf(
                DeviceRootMenuFeature.COOLING_FANS,
                hasFanHardware && hasCoolingFanContract
            )
            addIf(
                DeviceRootMenuFeature.COOLING_TEMPERATURE,
                capabilities.cooling && hasTemperatureHardware && hasCoolingTemperatureContract
            )
            addSettingsIfSupported()
        }

        private fun MutableSet<DeviceRootMenuFeature>.addSettingsIfSupported() {
            addIf(
                DeviceRootMenuFeature.DEVICE_SETTINGS,
                AqlDeviceScreenKey.ADVANCED in screens
            )
        }

        private fun MutableSet<DeviceRootMenuFeature>.addIf(
            feature: DeviceRootMenuFeature,
            supported: Boolean
        ) {
            if (supported) add(feature)
        }
    }
}
