package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.DeviceDosingChannelDestinationFragment

/** Empty channel-detail destination. Dosing controls are added here after calibration routing. */
class DeviceDosingChannelDetailFragment : DeviceDosingChannelDestinationFragment() {

    private val args: DeviceDosingChannelDetailFragmentArgs by navArgs()

    override val deviceUid: String
        get() = args.deviceUid

    override val slotId: String
        get() = args.slotId
}
