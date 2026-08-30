package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceCoolingRootBinding
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.common.devicepresence.DeviceMenuUnavailableMessageMapper
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderStatusIcon
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import kotlinx.coroutines.launch

class DeviceCoolingRootFragment : Fragment(R.layout.fragment_device_cooling_root) {

    private val args: DeviceCoolingRootFragmentArgs by navArgs()
    private val viewModel: DeviceCoolingRootViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentDeviceCoolingRootBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceCoolingRootBinding.bind(view)
        viewModel.bind(args.deviceUid)
        setFragmentGlobalLoading(viewModel.uiState.value.showBlockingPreparation)
        setupHeader(viewModel.uiState.value)
        binding.coolingContent.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceCoolingCatalogScreen(state = state)
            }
        }
        observeRootState()
        observeSurfaceUnavailable()
    }

    private fun setupHeader(state: DeviceCoolingRootUiState) {
        val connectionDescriptionRes = if (
            state.connectionVisualState == DeviceConnectionVisualState.ONLINE
        ) {
            R.string.device_cooling_connection_online_description
        } else {
            R.string.device_cooling_connection_offline_description
        }
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = state.title.ifBlank {
                    getString(R.string.device_cooling_unknown_device)
                },
                onBackClick = { findNavController().navigateUp() },
                statusIcon = AquaHeaderStatusIcon(
                    iconRes = R.drawable.ic_status_wifi,
                    tintColorRes = state.connectionVisualState.tintColorRes,
                    contentDescription = getString(connectionDescriptionRes)
                ),
                actions = listOf(
                    AquaHeaderAction(
                        iconRes = R.drawable.ic_settings,
                        contentDescription = getString(
                            R.string.device_cooling_open_settings_description
                        ),
                        enabled = state.contentEnabled,
                        onClick = ::openSettings
                    )
                )
            )
        )
    }

    private fun observeRootState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (_binding == null) return@collect
                    setupHeader(state)
                    setFragmentGlobalLoading(state.showBlockingPreparation)
                }
            }
        }
    }

    private fun observeSurfaceUnavailable() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.surfaceUnavailableEvents.collect { reason ->
                    if (_binding == null) return@collect
                    setFragmentGlobalLoading(false)
                    val navController = findNavController()
                    if (navController.currentDestination?.id == R.id.deviceCoolingRootFragment) {
                        navController.navigateUp()
                    }
                    (activity as? BaseActivity)?.showSnackBar(
                        message = getString(DeviceMenuUnavailableMessageMapper.messageRes(reason)),
                        type = BaseActivity.SnackType.ERROR
                    )
                }
            }
        }
    }

    private fun openSettings() {
        if (!viewModel.uiState.value.contentEnabled) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceCoolingRootFragment) return
        navController.navigate(
            DeviceCoolingRootFragmentDirections
                .actionDeviceCoolingRootFragmentToDeviceCoolingSettingsFragment(
                    deviceUid = args.deviceUid
                )
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
