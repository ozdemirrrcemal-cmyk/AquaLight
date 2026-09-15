package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.menu

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.preset.DeviceLightPresetId
import com.aqua.aqualight.databinding.FragmentDeviceLightAutomaticPresetBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticPresetActions
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticPresetNavigation
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticPresetScreen
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticPresetUiState

class DeviceLightAutomaticPresetFragment :
    Fragment(R.layout.fragment_device_light_automatic_preset) {

    private val args: DeviceLightAutomaticPresetFragmentArgs by navArgs()
    private var _binding: FragmentDeviceLightAutomaticPresetBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        require(args.deviceUid.isNotBlank()) { "Automatic preset destination deviceUid is required." }
        _binding = FragmentDeviceLightAutomaticPresetBinding.bind(view)
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_light_auto_preset_title),
                onBackClick = ::close
            )
        )
        setupContent()
    }

    private fun setupContent() {
        val initialPresetId = DeviceLightPresetId.fromStorageName(args.selectedPresetId)
            ?: DeviceLightPresetId.NATURAL_AQUARIUM
        binding.automaticPresetCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                var selectedPresetName by rememberSaveable {
                    mutableStateOf(initialPresetId.name)
                }
                val selectedPresetId = DeviceLightPresetId.fromStorageName(selectedPresetName)
                    ?: DeviceLightPresetId.NATURAL_AQUARIUM
                DeviceLightAutomaticPresetScreen(
                    state = DeviceLightAutomaticPresetUiState(
                        selectedPresetId = selectedPresetId
                    ),
                    actions = DeviceLightAutomaticPresetActions(
                        onPresetClick = { presetId -> selectedPresetName = presetId.name },
                        onCancelClick = ::close,
                        onUseClick = ::usePreset
                    )
                )
            }
        }
    }

    private fun usePreset(presetId: DeviceLightPresetId) {
        val navController = findNavController()
        navController.previousBackStackEntry?.savedStateHandle?.set(
            DeviceLightAutomaticPresetNavigation.RESULT_PRESET_ID,
            presetId.name
        )
        navController.navigateUp()
    }

    private fun close() {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.deviceLightAutomaticPresetFragment) {
            navController.navigateUp()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
