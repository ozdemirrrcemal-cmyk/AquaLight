package com.aqua.aqualight.ui.tabs.devices.detail.dosing

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
import com.aqua.aqualight.databinding.FragmentDeviceDosingRootBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.launch

class DeviceDosingRootFragment : Fragment(R.layout.fragment_device_dosing_root) {

    private val args: DeviceDosingRootFragmentArgs by navArgs()
    private val viewModel: DeviceDosingRootViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentDeviceDosingRootBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceDosingRootBinding.bind(view)

        setupHeader(title = args.deviceTitle.ifBlank { getString(R.string.device_root_dosing_title) })
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
                onBackClick = {
                    findNavController().navigateUp()
                }
            )
        )
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: DeviceDosingRootUiState) {
        if (_binding == null) return

        setupHeader(title = state.title)

        binding.tvProductName.text = state.title
        binding.tvDeviceUid.text = state.deviceUid.ifBlank { getString(R.string.device_runtime_unknown_device) }
        binding.tvConnectionStatus.text = state.connectionStatus
        binding.tvIp.text = getString(R.string.device_runtime_ip_format, state.ipText)
        binding.tvFirmware.text = getString(R.string.device_runtime_firmware_format, state.firmwareText)
        binding.tvModel.text = getString(R.string.device_runtime_model_format, state.modelText)
        binding.tvPrimaryCount.text = getString(R.string.device_runtime_labeled_value_format, state.primaryCountLabel, state.primaryCountText)
        binding.tvFeatures.text = getString(R.string.device_runtime_features_format, state.featuresText)
        binding.tvPrimarySectionTitle.text = state.primarySectionTitle
        binding.tvPrimarySectionPlaceholder.text = state.primarySectionPlaceholder
        binding.tvSecondarySectionTitle.text = state.secondarySectionTitle
        binding.tvSecondarySectionPlaceholder.text = state.secondarySectionPlaceholder
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
