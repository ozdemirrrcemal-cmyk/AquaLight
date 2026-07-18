package com.aqua.aqualight.ui.tabs.devices.detail.light

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
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceLightRootBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.launch

class DeviceLightRootFragment : Fragment(R.layout.fragment_device_light_root) {

    private val args: DeviceLightRootFragmentArgs by navArgs()
    private val viewModel: DeviceLightRootViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentDeviceLightRootBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightRootBinding.bind(view)

        setupHeader(title = args.deviceTitle.ifBlank { getString(R.string.device_root_light_title) })
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
            (activity as? BaseActivity)?.showSnackBar(
                message = if (shouldShow) {
                    getString(R.string.device_ota_test_panel_opened)
                } else {
                    getString(R.string.device_ota_test_panel_hidden)
                },
                type = BaseActivity.SnackType.NORMAL
            )
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
        binding.tvDeviceUid.text = state.deviceUid.ifBlank { getString(R.string.device_runtime_unknown_device) }
        binding.tvConnectionStatus.text = state.connectionStatus
        binding.tvIp.text = getString(R.string.device_runtime_ip_format, state.ipText)
        binding.tvFirmware.text = getString(R.string.device_runtime_firmware_format, state.firmwareText)
        binding.tvModel.text = getString(R.string.device_runtime_model_format, state.modelText)
        binding.tvChannelCount.text = getString(R.string.device_runtime_light_channels_format, state.channelCountText)
        binding.tvFeatures.text = getString(R.string.device_runtime_features_format, state.featuresText)
        binding.tvManualPlaceholder.text = state.manualMenuText
        binding.tvProgramsPlaceholder.text = state.programsMenuText
        binding.tvOtaTestStatus.text = state.otaTestText
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

}
