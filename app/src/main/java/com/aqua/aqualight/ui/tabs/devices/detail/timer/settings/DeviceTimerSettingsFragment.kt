package com.aqua.aqualight.ui.tabs.devices.detail.timer.settings

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.ui.tabs.devices.detail.settings.DeviceFamilySettingsFragment

class DeviceTimerSettingsFragment : DeviceFamilySettingsFragment() {

    private val args: DeviceTimerSettingsFragmentArgs by navArgs()

    override val deviceUid: String
        get() = args.deviceUid
}
