package com.aqua.aqualight.ui.tabs.devices.detail.cooling.settings

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R

class DeviceCoolingProgramSettingsFragment : DeviceCoolingModeSettingsFragment(
    R.string.device_cooling_program_settings_title
) {

    private val args: DeviceCoolingProgramSettingsFragmentArgs by navArgs()

    override val destinationDeviceUid: String
        get() = args.deviceUid
}
