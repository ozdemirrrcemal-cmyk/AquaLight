package com.aqua.aqualight.ui.tabs.devices.detail.dosing.settings

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.ui.tabs.devices.detail.settings.DeviceFamilySettingsFragment

class DeviceDosingSettingsFragment : DeviceFamilySettingsFragment() {

    private val args: DeviceDosingSettingsFragmentArgs by navArgs()

    override val deviceUid: String
        get() = args.deviceUid
}
