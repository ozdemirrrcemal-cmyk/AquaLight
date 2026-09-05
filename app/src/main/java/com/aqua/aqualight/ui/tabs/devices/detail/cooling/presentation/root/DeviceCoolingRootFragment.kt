package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root

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
import com.aqua.aqualight.ui.common.devicepresence.DeviceMenuUnavailableMessageMapper
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.navigation.DeviceCoolingRouteNavigator
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard.DeviceCoolingDashboardActions
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard.DeviceCoolingDashboardScreen
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
        setFragmentGlobalLoading(viewModel.uiState.value.showGlobalLoading)
        setupHeader(viewModel.uiState.value)
        setupDashboardContent()
        observeViewModel()
    }

    private fun setupHeader(state: DeviceCoolingRootUiState) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = state.title.ifBlank {
                    getString(R.string.device_family_cooling)
                },
                onBackClick = {
                    findNavController().navigateUp()
                },
                statusIcon = state.connectionVisualState.toWifiHeaderStatusIcon(requireContext()),
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

    private fun setupDashboardContent() {
        binding.coolingDashboardCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceCoolingDashboardScreen(
                    state = state,
                    actions = DeviceCoolingDashboardActions(
                        onModeSelected = viewModel::selectMode,
                        onTemperatureHistoryClick = ::openTemperatureHistory,
                        onAutomaticSettingsClick = ::openAutomaticSettings,
                        onManualSettingsClick = ::openManualSettings,
                        onProgramSettingsClick = ::openProgramSettings
                    )
                )
            }
        }
    }

    private fun openTemperatureHistory() {
        if (!viewModel.uiState.value.contentEnabled) return
        DeviceCoolingRouteNavigator.openTemperatureHistory(
            navController = findNavController(),
            deviceUid = args.deviceUid
        )
    }

    private fun openAutomaticSettings() {
        if (!viewModel.uiState.value.contentEnabled) return
        DeviceCoolingRouteNavigator.openAutomaticSettings(
            navController = findNavController(),
            deviceUid = args.deviceUid
        )
    }

    private fun openProgramSettings() {
        if (!viewModel.uiState.value.contentEnabled) return
        DeviceCoolingRouteNavigator.openProgramSettings(
            navController = findNavController(),
            deviceUid = args.deviceUid
        )
    }

    private fun openManualSettings() {
        if (!viewModel.uiState.value.contentEnabled) return
        DeviceCoolingRouteNavigator.openManualSettings(
            navController = findNavController(),
            deviceUid = args.deviceUid
        )
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

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        if (_binding == null) return@collect
                        setupHeader(state)
                        setFragmentGlobalLoading(state.showGlobalLoading)
                    }
                }
                launch {
                    viewModel.surfaceUnavailableEvents.collect { reason ->
                        if (_binding == null) return@collect
                        setFragmentGlobalLoading(false)
                        val navController = findNavController()
                        if (navController.currentDestination?.id ==
                            R.id.deviceCoolingRootFragment
                        ) {
                            navController.navigateUp()
                        }
                        (activity as? BaseActivity)?.showSnackBar(
                            message = getString(
                                DeviceMenuUnavailableMessageMapper.messageRes(reason)
                            ),
                            type = BaseActivity.SnackType.ERROR
                        )
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
