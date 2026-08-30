package com.aqua.aqualight.ui.tabs.devices.detail.cooling.settings

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R

class DeviceCoolingAutomaticSettingsFragment : DeviceCoolingModeSettingsFragment(
    R.string.device_cooling_automatic_settings_title
) {

    private val args: DeviceCoolingAutomaticSettingsFragmentArgs by navArgs()

    override val destinationDeviceUid: String
        get() = args.deviceUid
}
