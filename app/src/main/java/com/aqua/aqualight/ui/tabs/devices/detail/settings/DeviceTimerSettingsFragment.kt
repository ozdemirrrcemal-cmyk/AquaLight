package com.aqua.aqualight.ui.tabs.devices.detail.settings

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R

class DeviceTimerSettingsFragment :
    DeviceFamilySettingsFragment(R.string.device_timer_settings_title) {

    private val args: DeviceTimerSettingsFragmentArgs by navArgs()

    override val deviceUid: String
        get() = args.deviceUid
}
