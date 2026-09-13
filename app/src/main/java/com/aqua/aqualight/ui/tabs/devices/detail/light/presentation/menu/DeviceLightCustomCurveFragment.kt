package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.menu

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R

class DeviceLightCustomCurveFragment : DeviceLightEmptyMenuFragment(
    R.string.device_light_custom_curve_title
) {
    private val args: DeviceLightCustomCurveFragmentArgs by navArgs()
    override val deviceUid get() = args.deviceUid
}
