package com.aqua.aqualight.ui.tabs.devices.detail.timer

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceTimerRootBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.launch

class DeviceTimerRootFragment : Fragment(R.layout.fragment_device_timer_root) {

    private val args: DeviceTimerRootFragmentArgs by navArgs()
    private val viewModel: DeviceTimerRootViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentDeviceTimerRootBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceTimerRootBinding.bind(view)
        setupHeader(title = args.deviceTitle.ifBlank { DEFAULT_TITLE })
        observeViewModel()
        viewModel.bind(
            deviceUidText = args.deviceUid,
            fallbackTitle = args.deviceTitle
        )
    }

    private fun setupHeader(title: String) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = title,
                onBackClick = { findNavController().navigateUp() }
            )
        )
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> renderState(state) }
            }
        }
    }

    private fun renderState(state: DeviceTimerRootUiState) {
        if (_binding == null) return
        setupHeader(title = state.title)
        binding.tvProductName.text = state.title
        binding.tvDeviceUid.text = state.deviceUid.ifBlank { "Unknown device" }
        binding.tvConnectionStatus.text = state.connectionStatus
        binding.tvIp.text = "IP: ${state.ipText}"
        binding.tvFirmware.text = "Firmware: ${state.firmwareText}"
        binding.tvModel.text = "Model: ${state.modelText}"
        binding.tvPrimaryCount.text = "${state.primaryCountLabel}: ${state.primaryCountText}"
        binding.tvFeatures.text = "Features: ${state.featuresText}"
        binding.tvPrimarySectionTitle.text = state.primarySectionTitle
        binding.tvPrimarySectionPlaceholder.text = state.primarySectionPlaceholder
        binding.tvSecondarySectionTitle.text = state.secondarySectionTitle
        binding.tvSecondarySectionPlaceholder.text = state.secondarySectionPlaceholder
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val DEFAULT_TITLE = "Timer"
    }
}
