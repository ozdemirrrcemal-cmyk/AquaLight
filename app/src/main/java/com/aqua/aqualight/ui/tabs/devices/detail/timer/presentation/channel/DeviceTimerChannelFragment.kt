package com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.channel

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
import com.aqua.aqualight.databinding.FragmentDeviceTimerChannelBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.dashboard.effectiveName
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.common.toCommercialTimerError
import kotlinx.coroutines.launch

class DeviceTimerChannelFragment : Fragment(R.layout.fragment_device_timer_channel) {

    private val args: DeviceTimerChannelFragmentArgs by navArgs()
    private val viewModel: DeviceTimerChannelViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var _binding: FragmentDeviceTimerChannelBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val sheetCoordinator by lazy {
        DeviceTimerChannelSheetCoordinator(this) { viewModel.uiState.value }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceTimerChannelBinding.bind(view)
        sheetCoordinator.registerResults(viewLifecycleOwner, viewModel)
        setupContent()
        observeState()
        viewModel.bind(args.deviceUid, args.slotId)
    }

    private fun setupContent() {
        binding.timerChannelCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceTimerChannelScreen(
                    state = state,
                    actions = DeviceTimerChannelActions(
                        onPowerClick = viewModel::togglePower,
                        onTimedControlClick = sheetCoordinator::showTimedControl,
                        onProgramsClick = ::openPrograms,
                        onWorkModeClick = sheetCoordinator::showWorkMode,
                        onChannelNameClick = sheetCoordinator::showChannelName
                    )
                )
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        if (_binding == null) return@collect
                        setupHeader(state)
                        setFragmentGlobalLoading(
                            state.loadState == DeviceTimerChannelLoadState.LOADING ||
                                state.mutationPending
                        )
                    }
                }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun setupHeader(state: DeviceTimerChannelDetailUiState) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = state.channel?.effectiveName
                    ?.takeIf(String::isNotBlank)
                    ?: getString(R.string.device_family_timer),
                onBackClick = { findNavController().navigateUp() }
            )
        )
    }

    private fun openPrograms() {
        val state = viewModel.uiState.value
        if (!state.scheduleReadEnabled || state.mutationPending) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceTimerChannelFragment) return
        navController.navigate(
            DeviceTimerChannelFragmentDirections
                .actionDeviceTimerChannelFragmentToDeviceTimerProgramFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId
                )
        )
    }

    private fun handleEvent(event: DeviceTimerChannelEvent) {
        when (event) {
            is DeviceTimerChannelEvent.Failed -> {
                val copy = event.failure.toCommercialTimerError()
                (activity as? BaseActivity)?.showSnackBar(
                    getString(copy.messageRes),
                    BaseActivity.SnackType.WARNING
                )
            }
            DeviceTimerChannelEvent.InvalidDisplayName ->
                (activity as? BaseActivity)?.showSnackBar(
                    getString(R.string.device_timer_channel_name_invalid),
                    BaseActivity.SnackType.WARNING
                )
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
