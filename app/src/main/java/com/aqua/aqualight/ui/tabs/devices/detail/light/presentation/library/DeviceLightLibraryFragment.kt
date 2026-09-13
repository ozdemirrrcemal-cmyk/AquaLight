package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.library

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightLibraryBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

class DeviceLightLibraryFragment : Fragment(R.layout.fragment_device_light_library) {

    private var _binding: FragmentDeviceLightLibraryBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceLightLibraryBinding.bind(view)
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_light_library_title),
                onBackClick = { findNavController().navigateUp() }
            )
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
