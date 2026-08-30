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

/** Shared destination shell that keeps every Cooling mode editor on the central AquaHeader host. */
abstract class DeviceCoolingModeSettingsFragment(
    @StringRes private val titleRes: Int
) : Fragment(R.layout.fragment_device_cooling_mode_settings) {

    protected abstract val destinationDeviceUid: String

    private var _binding: FragmentDeviceCoolingModeSettingsBinding? = null
    protected val modeSettingsBinding: FragmentDeviceCoolingModeSettingsBinding
        get() = checkNotNull(_binding)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        check(destinationDeviceUid.isNotBlank()) { "Cooling destination deviceUid must not be blank." }
        _binding = FragmentDeviceCoolingModeSettingsBinding.bind(view)
        modeSettingsBinding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(titleRes),
                onBackClick = { findNavController().navigateUp() }
            )
        )
        onModeSettingsViewCreated(savedInstanceState)
    }

    protected open fun onModeSettingsViewCreated(savedInstanceState: Bundle?) = Unit

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
