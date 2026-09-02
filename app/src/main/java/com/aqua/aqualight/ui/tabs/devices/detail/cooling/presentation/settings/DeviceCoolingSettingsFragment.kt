package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.settings

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.ui.tabs.devices.detail.settings.DeviceFamilySettingsFragment

class DeviceCoolingSettingsFragment : DeviceFamilySettingsFragment() {

    private val args: DeviceCoolingSettingsFragmentArgs by navArgs()

    override val deviceUid: String
        get() = args.deviceUid
}
