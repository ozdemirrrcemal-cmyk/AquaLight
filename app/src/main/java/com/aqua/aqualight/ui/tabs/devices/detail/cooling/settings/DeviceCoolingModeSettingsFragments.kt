package com.aqua.aqualight.ui.tabs.devices.detail.cooling.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceCoolingModeSettingsBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

/**
 * Shared empty destination shell for Cooling mode editors.
 *
 * The destinations intentionally contain only the application-wide header for now. Their feature
 * content will be added independently without introducing a second navigation or header system.
 */
abstract class DeviceCoolingModeSettingsFragment(
    @StringRes private val titleRes: Int
) : Fragment(R.layout.fragment_device_cooling_mode_settings) {

    private var _binding: FragmentDeviceCoolingModeSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceCoolingModeSettingsBinding.bind(view)
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(titleRes),
                onBackClick = { findNavController().navigateUp() }
            )
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

class DeviceCoolingAutomaticSettingsFragment : DeviceCoolingModeSettingsFragment(
    R.string.device_cooling_automatic_settings_title
)

class DeviceCoolingProgramSettingsFragment : DeviceCoolingModeSettingsFragment(
    R.string.device_cooling_program_settings_title
)
