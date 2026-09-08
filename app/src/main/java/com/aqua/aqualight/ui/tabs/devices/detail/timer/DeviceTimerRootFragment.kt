package com.aqua.aqualight.ui.tabs.devices.detail.timer

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
import com.aqua.aqualight.databinding.FragmentDeviceTimerRootBinding
import com.aqua.aqualight.ui.common.devicepresence.DeviceMenuUnavailableMessageMapper
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
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
        viewModel.bind(args.deviceUid)
        val initialState = viewModel.uiState.value
        setFragmentGlobalLoading(initialState.showBlockingPreparation)
        setupHeader(initialState)
        setupDashboardContent()
        observeViewModel()
    }

    private fun setupDashboardContent() {
        binding.timerDashboardCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceTimerDashboardScreen(
                    state = state,
                    onChannelClick = ::openChannel,
                    onPowerClick = viewModel::toggleManualPower
                )
            }
        }
    }

    private fun setupHeader(state: DeviceTimerRootUiState) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = state.title.ifBlank {
                    getString(R.string.device_family_timer)
                },
                onBackClick = { findNavController().navigateUp() },
                statusIcon = state.connectionVisualState.toWifiHeaderStatusIcon(requireContext()),
                actions = listOf(
                    AquaHeaderAction(
                        iconRes = R.drawable.ic_settings,
                        contentDescription = getString(
                            R.string.device_timer_open_settings_description
                        ),
                        enabled = state.contentEnabled,
                        onClick = ::openSettings
                    )
                )
            )
        )
    }

    private fun openSettings() {
        if (!viewModel.uiState.value.contentEnabled) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceTimerRootFragment) return
        navController.navigate(
            DeviceTimerRootFragmentDirections
                .actionDeviceTimerRootFragmentToDeviceTimerSettingsFragment(
                    deviceUid = args.deviceUid
                )
        )
    }

    private fun openChannel(slotId: String) {
        if (!viewModel.uiState.value.contentEnabled) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceTimerRootFragment) return
        navController.navigate(
            DeviceTimerRootFragmentDirections
                .actionDeviceTimerRootFragmentToDeviceTimerChannelFragment(
                    deviceUid = args.deviceUid,
                    slotId = slotId
                )
        )
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
                        if (navController.currentDestination?.id == R.id.deviceTimerRootFragment) {
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

    private fun renderState(state: DeviceTimerRootUiState) {
        if (_binding == null) return

        setupHeader(state)
        setFragmentGlobalLoading(state.showBlockingPreparation)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

}
