package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.DeviceDosingChannelDestinationFragment

/** Calibration destination for one centrally identified, uncalibrated Dosing channel. */
class DeviceDosingChannelCalibrationFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_calibration) {

    override val destinationTitle: String
        get() = getString(R.string.device_menu_calibration_title)
}
