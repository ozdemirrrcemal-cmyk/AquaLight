package com.aqua.aqualight.ui.tabs.devices.detail.light.settings

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.ui.tabs.devices.detail.settings.DeviceFamilySettingsFragment

class DeviceLightSettingsFragment : DeviceFamilySettingsFragment() {

    private val args: DeviceLightSettingsFragmentArgs by navArgs()

    override val deviceUid: String
        get() = args.deviceUid
}
