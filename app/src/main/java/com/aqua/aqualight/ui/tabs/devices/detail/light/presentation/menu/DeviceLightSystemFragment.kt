package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.menu

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R

class DeviceLightSystemFragment : DeviceLightEmptyMenuFragment(
    R.string.device_light_system_title
) {
    private val args: DeviceLightSystemFragmentArgs by navArgs()
    override val deviceUid get() = args.deviceUid
}
