package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceDosingChannelDestinationBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

/** Shared empty shell for the centrally resolved Dosing channel destinations. */
abstract class DeviceDosingChannelDestinationFragment :
    Fragment(R.layout.fragment_device_dosing_channel_destination) {

    protected abstract val channelTitle: String

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDeviceDosingChannelDestinationBinding.bind(view)
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = channelTitle.ifBlank {
                    getString(R.string.device_family_dosing)
                },
                onBackClick = {
                    findNavController().navigateUp()
                }
            )
        )
    }
}
