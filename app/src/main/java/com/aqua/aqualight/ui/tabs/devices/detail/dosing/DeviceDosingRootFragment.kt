package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceDosingRootBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
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

        setupHeader(title = args.deviceTitle.ifBlank { getString(R.string.device_family_dosing) })
        setupPumpContent()
        observeHeaderTitle()

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
                            R.string.device_dosing_open_settings_description
                        ),
                        onClick = ::openSettings
                    )
                )
            )
        )
    }

    private fun openSettings() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceDosingRootFragment) return
        navController.navigate(
            DeviceDosingRootFragmentDirections
                .actionDeviceDosingRootFragmentToDeviceDosingSettingsFragment(
                    deviceUid = args.deviceUid
                )
        )
    }

    private fun openCalibration(channelKey: String) {
        val normalizedChannelKey = channelKey.trim()
        if (normalizedChannelKey.isEmpty()) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceDosingRootFragment) return
        navController.navigate(
            R.id.action_deviceDosingRootFragment_to_deviceDosingCalibrationFragment,
            bundleOf(
                "deviceUid" to args.deviceUid,
                "channelKey" to normalizedChannelKey
            )
        )
    }

    private fun setupPumpContent() {
        binding.dosingPumpCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceDosingCatalogScreen(
                    pumpCount = state.pumpCount,
                    channels = state.channels,
                    onChannelClick = ::openCalibration
                )
            }
        }
    }

    private fun observeHeaderTitle() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (_binding == null) return@collect
                    setupHeader(
                        title = state.title.ifBlank {
                            args.deviceTitle.ifBlank { getString(R.string.device_family_dosing) }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
