package com.aqua.aqualight.ui.tabs.devices.detail.settings

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R

class DeviceLightSettingsFragment : DeviceFamilySettingsFragment(LIGHT_SETTINGS_COPY) {

    private val args: DeviceLightSettingsFragmentArgs by navArgs()

    override val deviceUid: String
        get() = args.deviceUid
}

private val LIGHT_SETTINGS_COPY = DeviceFamilySettingsCopy(
    screenTitleRes = R.string.device_light_settings_title,
    lightCopy = DeviceLightSettingsCopy(
        sectionTitleRes = R.string.device_light_settings_protection_section,
        coolingAutoOffLabelRes = R.string.device_light_settings_cooling_auto_off_label,
        overTemperatureProtectionLabelRes =
            R.string.device_light_settings_over_temperature_protection_label,
        threshold = DeviceThresholdSettingsCopy(
            labelRes = R.string.device_light_settings_temperature_threshold_label,
            editDescriptionRes =
                R.string.device_light_settings_edit_temperature_threshold_description,
            pendingValueRes = R.string.device_light_settings_temperature_threshold_pending_value
        )
    )
)
