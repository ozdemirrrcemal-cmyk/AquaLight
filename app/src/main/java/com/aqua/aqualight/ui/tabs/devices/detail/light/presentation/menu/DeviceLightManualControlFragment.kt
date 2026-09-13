package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.menu

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R

class DeviceLightManualControlFragment : DeviceLightEmptyMenuFragment(
    R.string.device_menu_manual_control_title
) {
    private val args: DeviceLightManualControlFragmentArgs by navArgs()
    override val deviceUid get() = args.deviceUid
}
