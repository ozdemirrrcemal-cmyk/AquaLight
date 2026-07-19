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
import com.aqua.aqualight.ui.common.text.resolve
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

        setupHeader(title = args.deviceTitle.ifBlank { getString(R.string.device_family_dosing) })
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

        val context = requireContext()
        val unknown = getString(R.string.device_unknown)
        val title = state.title.ifBlank { getString(R.string.device_family_dosing) }
        setupHeader(title = title)

        binding.tvProductName.text = title
        binding.tvDeviceUid.text = state.deviceUid.ifBlank {
            getString(R.string.device_unknown_device)
        }
        binding.tvConnectionStatus.setText(state.connectionStatusRes)
        binding.tvIp.text = getString(R.string.device_ip_value, state.ipText.ifBlank { unknown })
        binding.tvFirmware.text = getString(
            R.string.device_firmware_value,
            state.firmwareText.ifBlank { unknown }
        )
        binding.tvModel.text = getString(
            R.string.device_model_value,
            state.modelText.ifBlank { unknown }
        )
        binding.tvPrimaryCount.text = getString(
            R.string.device_labeled_value,
            getString(state.primaryCountLabelRes),
            state.primaryCountText.ifBlank { unknown }
        )
        binding.tvFeatures.text = getString(
            R.string.device_features_value,
            context.resolve(state.featuresText)
        )
        binding.tvPrimarySectionTitle.setText(state.primarySectionTitleRes)
        binding.tvPrimarySectionPlaceholder.text = context.resolve(state.primarySectionPlaceholder)
        binding.tvSecondarySectionTitle.setText(state.secondarySectionTitleRes)
        binding.tvSecondarySectionPlaceholder.text = context.resolve(
            state.secondarySectionPlaceholder
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
