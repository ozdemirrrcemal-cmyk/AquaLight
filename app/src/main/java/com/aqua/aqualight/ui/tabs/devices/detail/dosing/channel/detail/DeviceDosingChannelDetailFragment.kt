package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.DeviceDosingChannelDestinationFragment

/** Detail destination for one centrally identified, calibrated Dosing channel. */
class DeviceDosingChannelDetailFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingChannelDetailFragmentArgs by navArgs()

    override val destinationTitle: String
        get() = args.channelTitle
            .ifBlank { getString(R.string.device_family_dosing) }
}
