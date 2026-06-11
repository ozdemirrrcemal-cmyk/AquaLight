package com.aqua.aqualight.ui.tabs.devices.detail.light.automation

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightAutomationBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.common.feedback.DeviceFeedbackType
import com.aqua.aqualight.ui.tabs.devices.common.feedback.showDeviceSnack
import com.aqua.aqualight.ui.tabs.devices.detail.light.automation.model.DeviceLightAutomationUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.automation.sheet.LightCloudSimulationSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.automation.sheet.LightMoonlightSheet
import kotlinx.coroutines.launch

class DeviceLightAutomationFragment : Fragment(R.layout.fragment_device_light_automation) {
    private val args: DeviceLightAutomationFragmentArgs by navArgs()
    private var _binding: FragmentDeviceLightAutomationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DeviceLightAutomationViewModel by viewModels()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceLightAutomationBinding.bind(view)
        viewModel.initialize(args.deviceId)
        setupHeader(); setupClicks(); observeUiState()
    }
    private fun setupHeader() { binding.appHeader.setupAquaHeader(fragment = this, config = AquaHeaderConfig(titleOverride = "Light Automation")) }
    private fun setupClicks() {
        binding.cardMoonlight.setOnClickListener { LightMoonlightSheet.create(requireContext()).show(viewModel.uiState.value.moonlight) { settings -> viewModel.updateMoonlight(settings); showDeviceSnack(message = "Moonlight settings saved", type = DeviceFeedbackType.SUCCESS) } }
        binding.cardCloudSimulation.setOnClickListener { LightCloudSimulationSheet.create(requireContext()).show(viewModel.uiState.value.cloudSimulation) { settings -> viewModel.updateCloudSimulation(settings); showDeviceSnack(message = "Cloud Simulation settings saved locally", type = DeviceFeedbackType.NORMAL) } }
    }
    private fun observeUiState() { viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.uiState.collect(::renderState) } } }
    private fun renderState(state: DeviceLightAutomationUiState) { binding.tvMoonStatus.text = state.moonlightStatusText; binding.tvMoonSubtitle.text = state.moonlightSummaryText; binding.tvCloudStatus.text = state.cloudStatusText; binding.tvCloudSubtitle.text = state.cloudSummaryText }
    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
