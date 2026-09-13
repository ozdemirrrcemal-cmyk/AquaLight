package com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.program

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
import com.aqua.aqualight.databinding.FragmentDeviceTimerProgramBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderPrimaryAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.common.toCommercialTimerError
import kotlinx.coroutines.launch

class DeviceTimerProgramFragment : Fragment(R.layout.fragment_device_timer_program) {

    private val args: DeviceTimerProgramFragmentArgs by navArgs()
    private val viewModel: DeviceTimerProgramViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var _binding: FragmentDeviceTimerProgramBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val sheetCoordinator by lazy {
        DeviceTimerProgramSheetCoordinator(this) { viewModel.uiState.value }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceTimerProgramBinding.bind(view)
        sheetCoordinator.registerResults(viewLifecycleOwner, viewModel)
        setupContent()
        observeState()
        viewModel.bind(args.deviceUid, args.slotId)
    }

    private fun setupContent() {
        binding.timerProgramCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceTimerProgramScreen(
                    state = state,
                    actions = DeviceTimerProgramActions(
                        onAdd = ::addProgram,
                        onDelete = { slotId ->
                            viewModel.edit(DeviceTimerProgramEdit.Delete(slotId))
                        },
                        onNameClick = sheetCoordinator::showName,
                        onEnabledToggle = { slotId ->
                            viewModel.edit(DeviceTimerProgramEdit.ToggleEnabled(slotId))
                        },
                        onWeekdayToggle = { slotId, weekday ->
                            viewModel.edit(DeviceTimerProgramEdit.ToggleWeekday(slotId, weekday))
                        },
                        onStartTimeClick = { slotId -> sheetCoordinator.showTime(slotId, true) },
                        onEndTimeClick = { slotId -> sheetCoordinator.showTime(slotId, false) }
                    )
                )
            }
        }
    }

    private fun setupHeader(state: DeviceTimerProgramUiState) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = state.channelTitle.takeIf(String::isNotBlank)?.let { channelTitle ->
                    getString(R.string.device_timer_program_title_for_channel, channelTitle)
                } ?: getString(R.string.device_timer_program_title),
                onBackClick = { findNavController().navigateUp() },
                primaryAction = AquaHeaderPrimaryAction(
                    text = getString(R.string.device_timer_program_save),
                    contentDescription = getString(R.string.device_timer_program_save),
                    enabled = state.canSave,
                    onClick = viewModel::save
                )
            )
        )
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        if (_binding == null) return@collect
                        setupHeader(state)
                        setFragmentGlobalLoading(
                            state.loadState == DeviceTimerProgramLoadState.LOADING || state.saving
                        )
                    }
                }
                launch {
                    viewModel.events.collect(::handleEvent)
                }
            }
        }
    }

    private fun handleEvent(event: DeviceTimerProgramEvent) {
        when (event) {
            DeviceTimerProgramEvent.Saved -> {
                (activity as? BaseActivity)?.showSnackBar(
                    getString(R.string.device_timer_program_saved),
                    BaseActivity.SnackType.SUCCESS
                )
                findNavController().navigateUp()
            }
            is DeviceTimerProgramEvent.Failed -> {
                val copy = event.failure.toCommercialTimerError()
                (activity as? BaseActivity)?.showSnackBar(
                    getString(copy.messageRes),
                    BaseActivity.SnackType.WARNING
                )
            }
        }
    }

    private fun addProgram() {
        val slotId = viewModel.uiState.value.nextScheduleSlotId ?: return
        viewModel.edit(
            DeviceTimerProgramEdit.Add(
                getString(R.string.device_timer_program_default_name, slotId)
            )
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
