package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.data.devices.contract.AqlCatalogKeySet
import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey
import com.aqua.aqualight.data.devices.contract.parseAqlDeviceFeatureKeysExact
import com.aqua.aqualight.data.devices.contract.parseAqlDeviceScreenKeysExact
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceSnapshot

internal object DeviceRootMenuFeatureResolver {

    fun resolve(snapshot: DeviceSnapshot): Set<DeviceRootMenuFeature> {
        val featureResult = snapshot.supportedFeatures.parseAqlDeviceFeatureKeysExact()
        val screenResult = snapshot.supportedScreens.parseAqlDeviceScreenKeysExact()
        if (featureResult !is AqlCatalogKeySet.Valid || screenResult !is AqlCatalogKeySet.Valid) {
            return emptySet()
        }

        val support = MenuSupport(
            snapshot = snapshot,
            features = featureResult.values,
            screens = screenResult.values
        )
        return when (snapshot.product.family) {
            DeviceFamily.LIGHT -> support.resolveLight()
            DeviceFamily.TIMER -> support.resolveTimer()
            DeviceFamily.DOSING -> support.resolveDosing()
            DeviceFamily.COOLING -> support.resolveCooling()
            DeviceFamily.UNKNOWN -> emptySet()
        }
    }

    private class MenuSupport(
        snapshot: DeviceSnapshot,
        private val features: Set<AqlDeviceFeatureKey>,
        private val screens: Set<AqlDeviceScreenKey>
    ) {
        private val capabilities = snapshot.capabilities

        fun resolveLight(): Set<DeviceRootMenuFeature> = buildSet {
            addIf(
                DeviceRootMenuFeature.LIGHT_MANUAL,
                capabilities.manualLight ||
                    AqlDeviceFeatureKey.LIGHT_CONTROL in features ||
                    AqlDeviceScreenKey.LIGHT_CONTROL in screens
            )
            addIf(
                DeviceRootMenuFeature.LIGHT_QUICK_SETUP,
                AqlDeviceFeatureKey.LIGHT_QUICK_SETUP in features ||
                    AqlDeviceScreenKey.LIGHT_QUICK_SETUP in screens
            )
            addIf(
                DeviceRootMenuFeature.LIGHT_PROGRAMS,
                capabilities.lightProgram || AqlDeviceScreenKey.LIGHT_SCHEDULE in screens
            )
            addIf(
                DeviceRootMenuFeature.LIGHT_PRESETS,
                capabilities.lightPresets ||
                    AqlDeviceFeatureKey.LIGHT_PRESETS in features ||
                    AqlDeviceScreenKey.LIGHT_PRESETS in screens
            )
            addIf(DeviceRootMenuFeature.LIGHT_SIMULATION, capabilities.lightSimulation)
            addIf(
                DeviceRootMenuFeature.COOLING_FANS,
                (capabilities.cooling || capabilities.fan) &&
                    (AqlDeviceFeatureKey.COOLING_CONTROL in features ||
                        AqlDeviceScreenKey.COOLING_CONTROL in screens ||
                        AqlDeviceScreenKey.COOLING_FANS in screens)
            )
            addIf(
                DeviceRootMenuFeature.COOLING_TEMPERATURE,
                capabilities.temperature &&
                    (AqlDeviceFeatureKey.TEMPERATURE_READ in features ||
                        AqlDeviceScreenKey.COOLING_RULES in screens ||
                        AqlDeviceScreenKey.COOLING_SENSOR_STATUS in screens)
            )
            addSettingsIfSupported()
        }

        fun resolveTimer(): Set<DeviceRootMenuFeature> = buildSet {
            addIf(
                DeviceRootMenuFeature.TIMER_CHANNELS,
                capabilities.standaloneTimer &&
                    (AqlDeviceFeatureKey.TIMER_CONTROL in features ||
                        AqlDeviceScreenKey.TIMER_CONTROL in screens ||
                        AqlDeviceScreenKey.TIMER_CHANNELS in screens)
            )
            addIf(
                DeviceRootMenuFeature.TIMER_SCHEDULES,
                capabilities.standaloneTimer && AqlDeviceScreenKey.TIMER_SCHEDULES in screens
            )
            addSettingsIfSupported()
        }

        fun resolveDosing(): Set<DeviceRootMenuFeature> = buildSet {
            addIf(
                DeviceRootMenuFeature.DOSING_CHANNELS,
                capabilities.dosing &&
                    (AqlDeviceFeatureKey.DOSING_CONTROL in features ||
                        AqlDeviceScreenKey.DOSING_CONTROL in screens ||
                        AqlDeviceScreenKey.DOSING_CHANNELS in screens)
            )
            addIf(
                DeviceRootMenuFeature.DOSING_CALIBRATION,
                capabilities.dosing &&
                    (AqlDeviceFeatureKey.DOSING_CALIBRATION in features ||
                        AqlDeviceScreenKey.DOSING_CALIBRATION in screens)
            )
            addIf(
                DeviceRootMenuFeature.DOSING_SCHEDULES,
                capabilities.dosing && AqlDeviceScreenKey.DOSING_SCHEDULES in screens
            )
            addSettingsIfSupported()
        }

        fun resolveCooling(): Set<DeviceRootMenuFeature> = buildSet {
            addIf(
                DeviceRootMenuFeature.COOLING_FANS,
                (capabilities.cooling || capabilities.fan) &&
                    (AqlDeviceFeatureKey.COOLING_CONTROL in features ||
                        AqlDeviceScreenKey.COOLING_CONTROL in screens ||
                        AqlDeviceScreenKey.COOLING_FANS in screens)
            )
            addIf(
                DeviceRootMenuFeature.COOLING_TEMPERATURE,
                capabilities.temperature &&
                    (AqlDeviceFeatureKey.TEMPERATURE_READ in features ||
                        AqlDeviceScreenKey.COOLING_RULES in screens ||
                        AqlDeviceScreenKey.COOLING_SENSOR_STATUS in screens)
            )
            addSettingsIfSupported()
        }

        private fun MutableSet<DeviceRootMenuFeature>.addSettingsIfSupported() {
            addIf(DeviceRootMenuFeature.DEVICE_SETTINGS, AqlDeviceScreenKey.ADVANCED in screens)
        }

        private fun MutableSet<DeviceRootMenuFeature>.addIf(
            feature: DeviceRootMenuFeature,
            supported: Boolean
        ) {
            if (supported) add(feature)
        }
    }
}
