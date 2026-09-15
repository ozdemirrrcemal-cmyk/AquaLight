package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.menu

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightEmptyMenuBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

abstract class DeviceLightEmptyMenuFragment(
    @StringRes private val titleRes: Int
) : Fragment(R.layout.fragment_device_light_empty_menu) {

    protected abstract val deviceUid: String
    private var _binding: FragmentDeviceLightEmptyMenuBinding? = null
    private val binding get() = _binding!!

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        require(deviceUid.isNotBlank())
        _binding = FragmentDeviceLightEmptyMenuBinding.bind(view)
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(titleRes),
                onBackClick = { findNavController().navigateUp() }
            )
        )
    }

    final override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
