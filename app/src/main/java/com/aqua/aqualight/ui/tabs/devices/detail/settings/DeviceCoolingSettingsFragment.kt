package com.aqua.aqualight.ui.tabs.devices.detail.settings

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R

class DeviceCoolingSettingsFragment :
    DeviceFamilySettingsFragment(R.string.device_cooling_settings_title) {

    private val args: DeviceCoolingSettingsFragmentArgs by navArgs()

    override val deviceUid: String
        get() = args.deviceUid
}
