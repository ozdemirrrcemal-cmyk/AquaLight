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
import com.aqua.aqualight.databinding.FragmentDeviceDosingRootBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.launch

class DeviceDosingRootFragment : Fragment(R.layout.fragment_device_dosing_root) {

    private val args: DeviceDosingRootFragmentArgs by navArgs()
    private val viewModel: DeviceDosingRootViewModel by viewModels()

    private var _binding: FragmentDeviceDosingRootBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceDosingRootBinding.bind(view)

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
        val online = state.connectionStatus.isReachablePresenceLabel()

        binding.tvProductName.text = state.title
        binding.tvDeviceUid.text = state.deviceUid.ifBlank { "Unknown device" }
        binding.tvConnectionStatus.text = if (online) "Online" else "Offline"
        binding.tvAuthStatus.text = if (online) "Ready" else "Unavailable"
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

    private fun String.isReachablePresenceLabel(): Boolean {
        return trim() in setOf(
            "Online",
            "Online LAN",
            "Connecting WebSocket",
            "Authenticated",
            "Provisioning",
            "OTA updating",
            "Ready"
        )
    }

    private companion object {
        const val DEFAULT_TITLE = "Dosing"
    }
}
