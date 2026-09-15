package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

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
import com.aqua.aqualight.databinding.FragmentDeviceLightRootBinding
import com.aqua.aqualight.ui.common.devicepresence.DeviceMenuUnavailableMessageMapper
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
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

        viewModel.bind(args.deviceUid)
        val initialState = viewModel.uiState.value
        setFragmentGlobalLoading(initialState.showBlockingPreparation)
        setupHeader(initialState)
        setupDashboardContent()
        observeViewModel()
    }

    private fun setupDashboardContent() {
        val actions = DeviceLightDashboardActions(
            onQuickSetupClick = ::openQuickSetup,
            onMenuClick = ::openDashboardMenu
        )
        binding.lightDashboardCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceLightDashboardScreen(
                    state = state,
                    actions = actions
                )
            }
        }
    }

    private fun setupHeader(state: DeviceLightRootUiState) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = state.title.ifBlank {
                    getString(R.string.device_family_light)
                },
                onBackClick = {
                    findNavController().navigateUp()
                },
                statusIcon = state.connectionVisualState.toWifiHeaderStatusIcon(requireContext()),
                actions = listOf(
                    AquaHeaderAction(
                        iconRes = R.drawable.ic_light_library,
                        contentDescription = getString(
                            R.string.device_light_open_library_description
                        ),
                        enabled = state.contentEnabled,
                        onClick = ::openLightLibrary
                    ),
                    AquaHeaderAction(
                        iconRes = R.drawable.ic_settings,
                        contentDescription = getString(
                            R.string.device_light_open_settings_description
                        ),
                        enabled = state.contentEnabled,
                        onClick = ::openSettings
                    )
                )
            )
        )
    }

    private fun openLightLibrary() {
        if (!viewModel.uiState.value.contentEnabled) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceLightRootFragment) return
        navController.navigate(
            DeviceLightRootFragmentDirections
                .actionDeviceLightRootFragmentToDeviceLightLibraryFragment(
                    deviceUid = args.deviceUid
                )
        )
    }

    private fun openSettings() {
        if (!viewModel.uiState.value.contentEnabled) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceLightRootFragment) return
        navController.navigate(
            DeviceLightRootFragmentDirections
                .actionDeviceLightRootFragmentToDeviceLightSettingsFragment(
                    deviceUid = args.deviceUid
                )
        )
    }

    private fun openQuickSetup() {
        if (!viewModel.uiState.value.contentEnabled) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceLightRootFragment) return
        navController.navigate(
            DeviceLightRootFragmentDirections
                .actionDeviceLightRootFragmentToDeviceLightQuickSetupFragment(
                    deviceUid = args.deviceUid
                )
        )
    }

    private fun openDashboardMenu(destination: DeviceLightMenuDestination) {
        if (!viewModel.uiState.value.contentEnabled) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceLightRootFragment) return
        when (destination) {
            DeviceLightMenuDestination.MANUAL_CONTROL -> navController.navigate(
                DeviceLightRootFragmentDirections
                    .actionDeviceLightRootFragmentToDeviceLightManualControlFragment(args.deviceUid)
            )
            DeviceLightMenuDestination.AUTOMATIC_PROGRAMS -> navController.navigate(
                DeviceLightRootFragmentDirections
                    .actionDeviceLightRootFragmentToDeviceLightAutomaticProgramsFragment(
                        args.deviceUid
                    )
            )
            DeviceLightMenuDestination.CUSTOM_LIGHT_CURVE -> navController.navigate(
                DeviceLightRootFragmentDirections
                    .actionDeviceLightRootFragmentToDeviceLightCustomCurveFragment(args.deviceUid)
            )
            DeviceLightMenuDestination.ADAPTATION -> if (
                viewModel.uiState.value.adaptation.supported
            ) {
                navController.navigate(
                    DeviceLightRootFragmentDirections
                        .actionDeviceLightRootFragmentToDeviceLightAdaptationFragment(args.deviceUid)
                )
            }
            DeviceLightMenuDestination.SYSTEM -> navController.navigate(
                DeviceLightRootFragmentDirections
                    .actionDeviceLightRootFragmentToDeviceLightSystemFragment(args.deviceUid)
            )
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state -> renderState(state) }
                }
                launch {
                    viewModel.surfaceUnavailableEvents.collect { reason ->
                        if (_binding == null) return@collect
                        setFragmentGlobalLoading(false)
                        val navController = findNavController()
                        if (navController.currentDestination?.id == R.id.deviceLightRootFragment) {
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

    private fun renderState(state: DeviceLightRootUiState) {
        if (_binding == null) return

        setupHeader(state)
        setFragmentGlobalLoading(state.showBlockingPreparation)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
