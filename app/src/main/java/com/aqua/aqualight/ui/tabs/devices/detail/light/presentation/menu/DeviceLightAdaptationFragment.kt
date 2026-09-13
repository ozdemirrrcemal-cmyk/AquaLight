package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.menu

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R

class DeviceLightAdaptationFragment : DeviceLightEmptyMenuFragment(
    R.string.device_light_adaptation_title
) {
    private val args: DeviceLightAdaptationFragmentArgs by navArgs()
    override val deviceUid get() = args.deviceUid
}
