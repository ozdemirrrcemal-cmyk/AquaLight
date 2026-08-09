package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.DeviceDosingChannelDestinationFragment

/** Existing empty calibration destination for one centrally identified Dosing channel. */
class DeviceDosingChannelCalibrationFragment : DeviceDosingChannelDestinationFragment() {

    private val args: DeviceDosingChannelCalibrationFragmentArgs by navArgs()

    override val channelTitle: String
        get() = args.channelTitle
}
