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
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.text.resolve
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

        setupHeader(title = args.deviceTitle.ifBlank { getString(R.string.device_family_light) })
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
                },
                actions = listOf(
                    AquaHeaderAction(
                        iconRes = R.drawable.ic_settings,
                        contentDescription = getString(
                            R.string.device_light_open_settings_description
                        ),
                        onClick = ::openSettings
                    )
                )
            )
        )
    }

    private fun openSettings() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceLightRootFragment) return
        navController.navigate(
            DeviceLightRootFragmentDirections
                .actionDeviceLightRootFragmentToDeviceLightSettingsFragment(
                    deviceUid = args.deviceUid
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

        val context = requireContext()
        val unknown = getString(R.string.device_unknown)
        val title = state.title.ifBlank { getString(R.string.device_family_light) }
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
        binding.tvChannelCount.text = getString(
            R.string.device_labeled_value,
            getString(R.string.device_light_channels_label),
            state.channelCountText.ifBlank { unknown }
        )
        binding.tvFeatures.text = getString(
            R.string.device_features_value,
            context.resolve(state.featuresText)
        )
        binding.tvManualPlaceholder.text = context.resolve(state.manualMenuText)
        binding.tvProgramsPlaceholder.text = context.resolve(state.programsMenuText)
        binding.tvOtaTestStatus.text = context.resolve(state.otaTestText)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
