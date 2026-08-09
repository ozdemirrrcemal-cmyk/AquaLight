package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.DeviceDosingChannelDestinationFragment

/** Calibration destination for one centrally identified, uncalibrated Dosing channel. */
class DeviceDosingChannelCalibrationFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_calibration) {

    private val args: DeviceDosingChannelCalibrationFragmentArgs by navArgs()

    override val destinationTitle: String
        get() = getString(R.string.device_menu_calibration_title)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSelectedPump(
            view = view,
            deviceUid = args.deviceUid,
            slotId = args.slotId,
            pumpCount = args.pumpCount,
            channelNumber = args.channelNumber
        )
    }
}
