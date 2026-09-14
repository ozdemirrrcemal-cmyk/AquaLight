package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.menu

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.LayoutAquaHeaderBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

class DeviceLightAutomaticPresetFragment : Fragment(R.layout.fragment_device_light_empty_menu) {

    private val args: DeviceLightAutomaticPresetFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        require(args.deviceUid.isNotBlank()) { "Automatic preset destination deviceUid is required." }
        LayoutAquaHeaderBinding.bind(view.findViewById(R.id.appHeader)).setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_light_auto_preset_title),
                onBackClick = { findNavController().navigateUp() }
            )
        )
    }
}
