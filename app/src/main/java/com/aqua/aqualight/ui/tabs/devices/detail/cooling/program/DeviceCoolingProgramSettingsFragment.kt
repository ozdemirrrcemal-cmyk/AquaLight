package com.aqua.aqualight.ui.tabs.devices.detail.cooling.program

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.ui.common.bottomsheet.AquaTimePickerBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.IntegerStepperBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderPrimaryAction
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.DeviceCoolingModeSettingsFragment
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DeviceCoolingProgramSettingsFragment : DeviceCoolingModeSettingsFragment(
    R.string.device_cooling_program_settings_title
) {

    private val args: DeviceCoolingProgramSettingsFragmentArgs by navArgs()
    private val viewModel: DeviceCoolingProgramSettingsViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    override val destinationDeviceUid: String
        get() = args.deviceUid

    override fun modeSettingsPrimaryAction(): AquaHeaderPrimaryAction = AquaHeaderPrimaryAction(
        text = getString(R.string.device_cooling_program_save),
        contentDescription = getString(R.string.device_cooling_program_save),
        enabled = viewModel.uiState.value.canSave,
        onClick = viewModel::saveDraft
    )

    override fun onModeSettingsViewCreated(savedInstanceState: Bundle?) {
        super.onModeSettingsViewCreated(savedInstanceState)
        registerPickerResults()
        bindHeaderSaveState()
        bindSaveFeedback()
        viewModel.bind(destinationDeviceUid)
        modeSettingsBinding.coolingModeSettingsCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                if (state.loadState == DeviceCoolingProgramLoadState.CONTENT) {
                    DeviceCoolingProgramSettingsScreen(
                        state = state,
                        actions = DeviceCoolingProgramSettingsActions(
                            onSlotClick = viewModel::selectSlot,
                            onAddSlot = viewModel::addTimeSlot,
                            onDeleteSlot = viewModel::deleteTimeSlot,
                            onStartTimeClick = ::showStartTimeSheet,
                            onEndTimeClick = ::showEndTimeSheet,
                            onFanOnTemperatureClick = ::showFanOnTemperatureSheet,
                            onTargetFanPercentChange = viewModel::updateTargetFanPercent
                        )
                    )
                } else {
                    DeviceCoolingProgramAvailabilityScreen(
                        loadState = state.loadState,
                        onRetry = { viewModel.bind(destinationDeviceUid) }
                    )
                }
            }
        }
    }

    private fun bindHeaderSaveState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { state -> state.canSave }
                    .distinctUntilChanged()
                    .collect { refreshModeSettingsHeader() }
            }
        }
    }

    private fun bindSaveFeedback() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { state -> state.saveState }
                    .distinctUntilChanged()
                    .collect { saveState ->
                        if (saveState.isFailure) {
                            (activity as? BaseActivity)?.showSnackBar(
                                getString(R.string.device_cooling_program_save_failed),
                                BaseActivity.SnackType.WARNING
                            )
                        }
                    }
            }
        }
    }

    private fun registerPickerResults() {
        registerTimeResult(REQUEST_START_TIME) { slotIndex, minutesOfDay ->
            if (!viewModel.updateStartTime(slotIndex, minutesOfDay)) {
                showScheduleValidationWarning()
            }
        }
        registerTimeResult(REQUEST_END_TIME) { slotIndex, minutesOfDay ->
            if (!viewModel.updateEndTime(slotIndex, minutesOfDay)) {
                showScheduleValidationWarning()
            }
        }
        parentFragmentManager.setFragmentResultListener(
            REQUEST_FAN_ON_TEMPERATURE,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(IntegerStepperBottomSheet.RESULT_KEY) !=
                IntegerStepperBottomSheet.RESULT_SAVED
            ) {
                return@setFragmentResultListener
            }
            val slotIndex = result.getString(IntegerStepperBottomSheet.RESULT_PAYLOAD_ID)
                ?.toIntOrNull()
                ?: return@setFragmentResultListener
            val temperatureC = result.getInt(IntegerStepperBottomSheet.RESULT_VALUE) *
                PROGRAM_TEMPERATURE_DISPLAY_SCALE
            if (!viewModel.updateFanOnTemperature(slotIndex, temperatureC)) {
                showScheduleValidationWarning()
            }
        }
    }

    private fun registerTimeResult(
        requestKey: String,
        onSelected: (Int, Int) -> Unit
    ) {
        parentFragmentManager.setFragmentResultListener(requestKey, viewLifecycleOwner) { _, result ->
            if (result.getString(AquaTimePickerBottomSheet.RESULT_KEY) !=
                AquaTimePickerBottomSheet.RESULT_SELECTED
            ) {
                return@setFragmentResultListener
            }
            val slotIndex = result.getString(AquaTimePickerBottomSheet.RESULT_PAYLOAD_ID)
                ?.toIntOrNull()
                ?: return@setFragmentResultListener
            onSelected(
                slotIndex,
                result.getInt(AquaTimePickerBottomSheet.RESULT_MINUTES_OF_DAY)
            )
        }
    }

    private fun showStartTimeSheet(slotIndex: Int) {
        val slot = viewModel.slotAt(slotIndex) ?: return
        showTimeSheet(
            slotIndex = slotIndex,
            minutesOfDay = slot.startMinutes,
            requestKey = REQUEST_START_TIME,
            titleRes = R.string.device_cooling_program_start_time_sheet_title,
            messageRes = R.string.device_cooling_program_start_time_sheet_message
        )
    }

    private fun showEndTimeSheet(slotIndex: Int) {
        val slot = viewModel.slotAt(slotIndex) ?: return
        showTimeSheet(
            slotIndex = slotIndex,
            minutesOfDay = slot.endMinutes,
            requestKey = REQUEST_END_TIME,
            titleRes = R.string.device_cooling_program_end_time_sheet_title,
            messageRes = R.string.device_cooling_program_end_time_sheet_message
        )
    }

    private fun showFanOnTemperatureSheet(slotIndex: Int) {
        val bounds = fanOnTemperatureStepperBounds(viewModel.uiState.value, slotIndex) ?: return
        IntegerStepperBottomSheet.show(
            fragmentManager = parentFragmentManager,
            title = getString(R.string.device_cooling_program_fan_on_temperature_sheet_title),
            helperText = getString(R.string.device_cooling_program_fan_on_temperature_sheet_helper),
            valueFormat = getString(R.string.device_cooling_automatic_stepper_value_format),
            initialValue = bounds.initialValue,
            minValue = bounds.minimumValue,
            maxValue = bounds.maximumValue,
            step = bounds.step,
            saveText = getString(R.string.device_cooling_automatic_stepper_apply),
            cancelText = getString(R.string.device_cooling_automatic_stepper_cancel),
            decreaseContentDescription = getString(
                R.string.device_cooling_automatic_stepper_decrease
            ),
            increaseContentDescription = getString(
                R.string.device_cooling_automatic_stepper_increase
            ),
            requestKey = REQUEST_FAN_ON_TEMPERATURE,
            payloadId = slotIndex.toString(),
            displayScale = PROGRAM_TEMPERATURE_DISPLAY_SCALE
        )
    }

    private fun showTimeSheet(
        slotIndex: Int,
        minutesOfDay: Int,
        requestKey: String,
        titleRes: Int,
        messageRes: Int
    ) {
        AquaTimePickerBottomSheet.show(
            fragmentManager = parentFragmentManager,
            request = AquaTimePickerBottomSheet.Request(
                title = getString(titleRes),
                message = getString(messageRes),
                initialHour = minutesOfDay / MINUTES_PER_HOUR,
                initialMinute = minutesOfDay % MINUTES_PER_HOUR,
                confirmText = getString(R.string.device_cooling_automatic_stepper_apply),
                cancelText = getString(R.string.device_cooling_automatic_stepper_cancel),
                resultTarget = AquaTimePickerBottomSheet.ResultTarget(
                    requestKey = requestKey,
                    payloadId = slotIndex.toString()
                )
            )
        )
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val REQUEST_START_TIME = "cooling_program_start_time"
        const val REQUEST_END_TIME = "cooling_program_end_time"
        const val REQUEST_FAN_ON_TEMPERATURE = "cooling_program_fan_on_temperature"
    }
}

private fun DeviceCoolingProgramSettingsFragment.showScheduleValidationWarning() {
    (activity as? BaseActivity)?.showSnackBar(
        getString(R.string.device_cooling_program_schedule_invalid),
        BaseActivity.SnackType.WARNING
    )
}

private fun DeviceCoolingProgramSettingsViewModel.slotAt(slotIndex: Int): DeviceCoolingProgramSlot? =
    uiState.value.slots.getOrNull(slotIndex)

private val DeviceCoolingProgramSaveState.isFailure: Boolean
    get() = when (this) {
        DeviceCoolingProgramSaveState.UNSUPPORTED,
        DeviceCoolingProgramSaveState.UNAVAILABLE,
        DeviceCoolingProgramSaveState.NOT_CONNECTED,
        DeviceCoolingProgramSaveState.REJECTED,
        DeviceCoolingProgramSaveState.VALIDATION_ERROR,
        DeviceCoolingProgramSaveState.ERROR -> true
        DeviceCoolingProgramSaveState.IDLE,
        DeviceCoolingProgramSaveState.SAVING,
        DeviceCoolingProgramSaveState.SAVED -> false
    }
