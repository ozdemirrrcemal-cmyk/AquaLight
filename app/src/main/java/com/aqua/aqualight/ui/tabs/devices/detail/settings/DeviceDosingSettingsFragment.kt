package com.aqua.aqualight.ui.tabs.devices.detail.settings

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R

class DeviceDosingSettingsFragment : DeviceFamilySettingsFragment(DOSING_SETTINGS_COPY) {

    private val args: DeviceDosingSettingsFragmentArgs by navArgs()

    override val deviceUid: String
        get() = args.deviceUid
}

private val DOSING_SETTINGS_COPY = DeviceFamilySettingsCopy(
    screenTitleRes = R.string.device_dosing_settings_title
)
