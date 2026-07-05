package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightRootBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.launch

class DeviceLightRootFragment : Fragment(R.layout.fragment_device_light_root) {

    private val args: DeviceLightRootFragmentArgs by navArgs()
    private val viewModel: DeviceLightRootViewModel by viewModels()

    private var _binding: FragmentDeviceLightRootBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightRootBinding.bind(view)

        setupHeader(title = args.deviceTitle.ifBlank { "Light" })
        setupOtaTestPanel()
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

    private fun setupOtaTestPanel() {
        binding.otaTestPanel.visibility = View.GONE

        binding.tvFirmware.setOnLongClickListener {
            val shouldShow = binding.otaTestPanel.visibility != View.VISIBLE
            binding.otaTestPanel.visibility = if (shouldShow) View.VISIBLE else View.GONE
            Toast.makeText(
                requireContext(),
                if (shouldShow) "OTA test panel opened" else "OTA test panel hidden",
                Toast.LENGTH_SHORT
            ).show()
            true
        }

        binding.btnOtaTestCheck.setOnClickListener {
            viewModel.checkBetaOtaManifest()
        }

        binding.btnOtaTestStart.setOnClickListener {
            viewModel.startOtaTestUpdate()
        }

        binding.btnOtaTestStatus.setOnClickListener {
            viewModel.requestOtaTestStatus()
        }

        binding.btnOtaTestClear.setOnClickListener {
            viewModel.clearOtaTestStatus()
        }
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

    private fun renderState(state: DeviceLightRootUiState) {
        if (_binding == null) return

        setupHeader(title = state.title)

        binding.tvProductName.text = state.title
        binding.tvDeviceUid.text = state.deviceUid.ifBlank { "Unknown device" }
        binding.tvConnectionStatus.text = state.connectionStatus
        binding.tvIp.text = "IP: ${state.ipText}"
        binding.tvFirmware.text = "Firmware: ${state.firmwareText}"
        binding.tvModel.text = "Model: ${state.modelText}"
        binding.tvChannelCount.text = "Light channels: ${state.channelCountText}"
        binding.tvFeatures.text = "Features: ${state.featuresText}"
        binding.tvManualPlaceholder.text = state.manualMenuText
        binding.tvProgramsPlaceholder.text = state.programsMenuText
        binding.tvOtaTestStatus.text = state.otaTestText
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

}
