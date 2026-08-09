package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.DeviceDosingChannelDestinationFragment

/** Empty calibration destination for one centrally identified Dosing channel slot. */
class DeviceDosingChannelCalibrationFragment : DeviceDosingChannelDestinationFragment() {

    private val args: DeviceDosingChannelCalibrationFragmentArgs by navArgs()

    override val deviceUid: String
        get() = args.deviceUid

    override val slotId: String
        get() = args.slotId
}
