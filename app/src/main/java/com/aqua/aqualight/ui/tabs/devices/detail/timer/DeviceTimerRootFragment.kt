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
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.text.resolve
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
        setupHeader(title = args.deviceTitle.ifBlank { getString(R.string.device_family_timer) })
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
                title = title,
                onBackClick = { findNavController().navigateUp() },
                actions = listOf(
                    AquaHeaderAction(
                        iconRes = R.drawable.ic_settings,
                        contentDescription = getString(
                            R.string.device_timer_open_settings_description
                        ),
                        onClick = ::openSettings
                    )
                )
            )
        )
    }

    private fun openSettings() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceTimerRootFragment) return
        navController.navigate(
            DeviceTimerRootFragmentDirections
                .actionDeviceTimerRootFragmentToDeviceTimerSettingsFragment(
                    deviceUid = args.deviceUid
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
        val context = requireContext()
        val unknown = getString(R.string.device_unknown)
        val title = state.title.ifBlank { getString(R.string.device_family_timer) }
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
