package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey
import com.aqua.aqualight.data.devices.contract.toAqlDeviceFeatureKeys
import com.aqua.aqualight.data.devices.contract.toAqlDeviceScreenKeys
import com.aqua.aqualight.data.devices.model.DeviceSnapshot

internal object DeviceRootMenuFeatureResolver {

    fun resolve(snapshot: DeviceSnapshot): Set<DeviceRootMenuFeature> =
        MenuSupport(snapshot).resolve()

    private class MenuSupport(snapshot: DeviceSnapshot) {
        private val capabilities = snapshot.capabilities
        private val features = snapshot.supportedFeatures.toAqlDeviceFeatureKeys()
        private val screens = snapshot.supportedScreens.toAqlDeviceScreenKeys()

        private val lightManual = capabilities.manualLight ||
            AqlDeviceFeatureKey.LIGHT_CONTROL in features ||
            AqlDeviceScreenKey.LIGHT_CONTROL in screens

        private val lightQuickSetup =
            AqlDeviceFeatureKey.LIGHT_QUICK_SETUP in features ||
                AqlDeviceScreenKey.LIGHT_QUICK_SETUP in screens

        private val lightPrograms = capabilities.lightProgram ||
            AqlDeviceScreenKey.LIGHT_SCHEDULE in screens

        private val lightPresets = capabilities.lightPresets ||
            AqlDeviceFeatureKey.LIGHT_PRESETS in features ||
            AqlDeviceScreenKey.LIGHT_PRESETS in screens

        private val dosingChannels = capabilities.dosing ||
            AqlDeviceFeatureKey.DOSING_CONTROL in features ||
            screens.containsAny(
                AqlDeviceScreenKey.DOSING_CONTROL,
                AqlDeviceScreenKey.DOSING_CHANNELS
            )

        private val dosingCalibration =
            AqlDeviceFeatureKey.DOSING_CALIBRATION in features ||
                AqlDeviceScreenKey.DOSING_CALIBRATION in screens

        private val timerChannels = capabilities.standaloneTimer ||
            AqlDeviceFeatureKey.TIMER_CONTROL in features ||
            screens.containsAny(
                AqlDeviceScreenKey.TIMER_CONTROL,
                AqlDeviceScreenKey.TIMER_CHANNELS
            )

        private val hasCoolingCapability = capabilities.cooling || capabilities.fan

        private val coolingFans = hasCoolingCapability ||
            AqlDeviceFeatureKey.COOLING_CONTROL in features ||
            screens.containsAny(
                AqlDeviceScreenKey.COOLING_CONTROL,
                AqlDeviceScreenKey.COOLING_FANS
            )

        private val coolingTemperature = capabilities.temperature ||
            AqlDeviceFeatureKey.TEMPERATURE_READ in features ||
            screens.containsAny(
                AqlDeviceScreenKey.COOLING_RULES,
                AqlDeviceScreenKey.COOLING_SENSOR_STATUS
            )

        private val deviceSettings = capabilities.ota ||
            AqlDeviceFeatureKey.OTA_UPDATE in features ||
            AqlDeviceScreenKey.ADVANCED in screens

        fun resolve(): Set<DeviceRootMenuFeature> = buildSet {
            addIf(DeviceRootMenuFeature.LIGHT_MANUAL, lightManual)
            addIf(DeviceRootMenuFeature.LIGHT_QUICK_SETUP, lightQuickSetup)
            addIf(DeviceRootMenuFeature.LIGHT_PROGRAMS, lightPrograms)
            addIf(DeviceRootMenuFeature.LIGHT_PRESETS, lightPresets)
            addIf(DeviceRootMenuFeature.LIGHT_SIMULATION, capabilities.lightSimulation)
            addIf(DeviceRootMenuFeature.DOSING_CHANNELS, dosingChannels)
            addIf(DeviceRootMenuFeature.DOSING_CALIBRATION, dosingCalibration)
            addIf(
                DeviceRootMenuFeature.DOSING_SCHEDULES,
                AqlDeviceScreenKey.DOSING_SCHEDULES in screens
            )
            addIf(DeviceRootMenuFeature.TIMER_CHANNELS, timerChannels)
            addIf(
                DeviceRootMenuFeature.TIMER_SCHEDULES,
                AqlDeviceScreenKey.TIMER_SCHEDULES in screens
            )
            addIf(DeviceRootMenuFeature.COOLING_FANS, coolingFans)
            addIf(DeviceRootMenuFeature.COOLING_TEMPERATURE, coolingTemperature)
            addIf(DeviceRootMenuFeature.DEVICE_SETTINGS, deviceSettings)
        }

        private fun MutableSet<DeviceRootMenuFeature>.addIf(
            feature: DeviceRootMenuFeature,
            supported: Boolean
        ) {
            if (supported) add(feature)
        }

        private fun Set<AqlDeviceScreenKey>.containsAny(
            vararg candidates: AqlDeviceScreenKey
        ): Boolean = candidates.any(::contains)
    }
}
