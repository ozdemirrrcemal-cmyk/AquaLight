package com.aqua.aqualight.ui.tabs.devices.detail.settings

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R

class DeviceLightSettingsFragment :
    DeviceFamilySettingsFragment(R.string.device_light_settings_title) {

    private val args: DeviceLightSettingsFragmentArgs by navArgs()

    override val deviceUid: String
        get() = args.deviceUid
}
