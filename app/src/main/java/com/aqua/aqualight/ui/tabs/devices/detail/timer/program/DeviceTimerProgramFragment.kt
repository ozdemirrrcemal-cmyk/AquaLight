package com.aqua.aqualight.ui.tabs.devices.detail.timer.program

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
import com.aqua.aqualight.ui.common.bottomsheet.AquaTimePickerBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderPrimaryAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.tabs.devices.detail.timer.toCommercialTimerError
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions")
class DeviceTimerProgramFragment : Fragment(R.layout.fragment_device_timer_program) {

    private val args: DeviceTimerProgramFragmentArgs by navArgs()
    private val viewModel: DeviceTimerProgramViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var _binding: FragmentDeviceTimerProgramBinding? = null
    private val binding get() = checkNotNull(_binding)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceTimerProgramBinding.bind(view)
        registerResults()
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
                        onDelete = viewModel::deleteSchedule,
                        onNameClick = ::showNameSheet,
                        onEnabledToggle = viewModel::toggleEnabled,
                        onWeekdayToggle = viewModel::toggleWeekday,
                        onStartTimeClick = { slotId -> showTimeSheet(slotId, true) },
                        onEndTimeClick = { slotId -> showTimeSheet(slotId, false) }
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
        viewModel.addSchedule(getString(R.string.device_timer_program_default_name, slotId))
    }

    private fun showNameSheet(slotId: Int) {
        val schedule = viewModel.uiState.value.schedules.singleOrNull { it.slotId == slotId }
            ?: return
        TextInputBottomSheet.show(
            fragmentManager = parentFragmentManager,
            title = getString(R.string.device_timer_program_name_sheet_title),
            label = getString(R.string.device_timer_program_name_sheet_label),
            hint = getString(R.string.device_timer_program_name_sheet_hint),
            initialValue = schedule.name,
            supportingText = getString(R.string.device_timer_program_name_sheet_helper),
            saveText = getString(R.string.device_timer_apply),
            cancelText = getString(R.string.device_timer_cancel),
            required = true,
            requiredMessage = getString(R.string.device_timer_program_name_required),
            requestKey = REQUEST_NAME,
            payloadId = slotId.toString(),
            maxLength = MAX_NAME_CHARACTERS,
            disableSaveWhenUnchanged = true,
            requestFocus = true
        )
    }

    private fun showTimeSheet(slotId: Int, start: Boolean) {
        val schedule = viewModel.uiState.value.schedules.singleOrNull { it.slotId == slotId }
            ?: return
        val minutes = if (start) schedule.startMinutesOfDay else schedule.endMinutesOfDay
        AquaTimePickerBottomSheet.show(
            fragmentManager = parentFragmentManager,
            request = AquaTimePickerBottomSheet.Request(
                title = getString(
                    if (start) R.string.device_timer_program_start_sheet_title
                    else R.string.device_timer_program_end_sheet_title
                ),
                message = getString(R.string.device_timer_program_time_sheet_message),
                initialHour = minutes / MINUTES_PER_HOUR,
                initialMinute = minutes % MINUTES_PER_HOUR,
                confirmText = getString(R.string.device_timer_apply),
                cancelText = getString(R.string.device_timer_cancel),
                resultTarget = AquaTimePickerBottomSheet.ResultTarget(
                    requestKey = if (start) REQUEST_START else REQUEST_END,
                    payloadId = slotId.toString()
                )
            )
        )
    }

    private fun registerResults() {
        parentFragmentManager.setFragmentResultListener(
            REQUEST_NAME,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(TextInputBottomSheet.RESULT_KEY) !=
                TextInputBottomSheet.RESULT_SAVED
            ) return@setFragmentResultListener
            val slotId = result.getString(TextInputBottomSheet.RESULT_PAYLOAD_ID)?.toIntOrNull()
                ?: return@setFragmentResultListener
            viewModel.updateName(slotId, result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty())
        }
        registerTimeResult(REQUEST_START, viewModel::updateStartTime)
        registerTimeResult(REQUEST_END, viewModel::updateEndTime)
    }

    private fun registerTimeResult(requestKey: String, update: (Int, Int) -> Unit) {
        parentFragmentManager.setFragmentResultListener(requestKey, viewLifecycleOwner) { _, result ->
            if (result.getString(AquaTimePickerBottomSheet.RESULT_KEY) !=
                AquaTimePickerBottomSheet.RESULT_SELECTED
            ) return@setFragmentResultListener
            val slotId = result.getString(AquaTimePickerBottomSheet.RESULT_PAYLOAD_ID)?.toIntOrNull()
                ?: return@setFragmentResultListener
            update(slotId, result.getInt(AquaTimePickerBottomSheet.RESULT_MINUTES_OF_DAY))
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val REQUEST_NAME = "timer_program_name"
        const val REQUEST_START = "timer_program_start"
        const val REQUEST_END = "timer_program_end"
        const val MINUTES_PER_HOUR = 60
        const val MAX_NAME_CHARACTERS = 48
    }
}
