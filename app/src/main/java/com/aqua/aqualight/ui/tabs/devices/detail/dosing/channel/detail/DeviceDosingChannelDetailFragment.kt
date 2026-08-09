package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.DeviceDosingChannelDestinationFragment

/** Existing empty detail destination for one centrally identified Dosing channel. */
class DeviceDosingChannelDetailFragment : DeviceDosingChannelDestinationFragment() {

    private val args: DeviceDosingChannelDetailFragmentArgs by navArgs()

    override val channelTitle: String
        get() = args.channelTitle
}
