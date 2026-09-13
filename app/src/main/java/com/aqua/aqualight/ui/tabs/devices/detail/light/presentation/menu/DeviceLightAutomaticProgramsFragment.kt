package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.menu

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R

class DeviceLightAutomaticProgramsFragment : DeviceLightEmptyMenuFragment(
    R.string.device_light_automatic_programs_title
) {
    private val args: DeviceLightAutomaticProgramsFragmentArgs by navArgs()
    override val deviceUid get() = args.deviceUid
}
