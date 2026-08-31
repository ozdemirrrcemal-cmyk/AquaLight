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
import com.aqua.aqualight.ui.common.bottomsheet.AquaTimePickerBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderPrimaryAction
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.DeviceCoolingModeSettingsFragment
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DeviceCoolingProgramSettingsFragment : DeviceCoolingModeSettingsFragment(
    R.string.device_cooling_program_settings_title
) {

    private val args: DeviceCoolingProgramSettingsFragmentArgs by navArgs()
    private val viewModel: DeviceCoolingProgramSettingsViewModel by viewModels()

    override val destinationDeviceUid: String
        get() = args.deviceUid

    override fun modeSettingsPrimaryAction(): AquaHeaderPrimaryAction = AquaHeaderPrimaryAction(
        text = getString(R.string.device_cooling_program_save),
        contentDescription = getString(R.string.device_cooling_program_save),
        enabled = viewModel.uiState.value.hasChanges,
        onClick = viewModel::saveDraft
    )

    override fun onModeSettingsViewCreated(savedInstanceState: Bundle?) {
        super.onModeSettingsViewCreated(savedInstanceState)
        registerPickerResults()
        bindHeaderSaveState()
        modeSettingsBinding.coolingModeSettingsCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceCoolingProgramSettingsScreen(
                    state = state,
                    actions = DeviceCoolingProgramSettingsActions(
                        onSlotClick = viewModel::selectSlot,
                        onAddSlot = viewModel::addTimeSlot,
                        onDeleteSlot = { slotId -> viewModel.deleteTimeSlot(slotId) },
                        onStartTimeClick = ::showStartTimeSheet,
                        onEndTimeClick = ::showEndTimeSheet,
                        onFanLimitChange = viewModel::updateFanLimit
                    )
                )
            }
        }
    }

    private fun bindHeaderSaveState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { state: DeviceCoolingProgramSettingsUiState -> state.hasChanges }
                    .distinctUntilChanged()
                    .collect { _: Boolean -> refreshModeSettingsHeader() }
            }
        }
    }

    private fun registerPickerResults() {
        registerTimeResult(REQUEST_START_TIME) { slotId, minutesOfDay ->
            if (!viewModel.updateStartTime(slotId, minutesOfDay)) {
                showScheduleOverlapWarning()
            }
        }
        registerTimeResult(REQUEST_END_TIME) { slotId, minutesOfDay ->
            if (!viewModel.updateEndTime(slotId, minutesOfDay)) {
                showScheduleOverlapWarning()
            }
        }
    }

    private fun registerTimeResult(
        requestKey: String,
        onSelected: (String, Int) -> Unit
    ) {
        parentFragmentManager.setFragmentResultListener(requestKey, viewLifecycleOwner) { _, result ->
            if (result.getString(AquaTimePickerBottomSheet.RESULT_KEY) !=
                AquaTimePickerBottomSheet.RESULT_SELECTED
            ) {
                return@setFragmentResultListener
            }
            val slotId = result.getString(AquaTimePickerBottomSheet.RESULT_PAYLOAD_ID).orEmpty()
            if (slotId.isBlank()) return@setFragmentResultListener
            onSelected(
                slotId,
                result.getInt(AquaTimePickerBottomSheet.RESULT_MINUTES_OF_DAY)
            )
        }
    }

    private fun showScheduleOverlapWarning() {
        (activity as? BaseActivity)?.showSnackBar(
            getString(R.string.device_cooling_program_schedule_overlap),
            BaseActivity.SnackType.WARNING
        )
    }

    private fun showStartTimeSheet(slotId: String) {
        val slot = findSlot(slotId) ?: return
        showTimeSheet(
            slotId = slotId,
            minutesOfDay = slot.startMinutes,
            requestKey = REQUEST_START_TIME,
            titleRes = R.string.device_cooling_program_start_time_sheet_title,
            messageRes = R.string.device_cooling_program_start_time_sheet_message
        )
    }

    private fun showEndTimeSheet(slotId: String) {
        val slot = findSlot(slotId) ?: return
        showTimeSheet(
            slotId = slotId,
            minutesOfDay = slot.endMinutes,
            requestKey = REQUEST_END_TIME,
            titleRes = R.string.device_cooling_program_end_time_sheet_title,
            messageRes = R.string.device_cooling_program_end_time_sheet_message
        )
    }

    private fun showTimeSheet(
        slotId: String,
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
                    payloadId = slotId
                )
            )
        )
    }

    private fun findSlot(slotId: String): DeviceCoolingProgramSlot? =
        viewModel.uiState.value.slots.firstOrNull { slot -> slot.id == slotId }

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val REQUEST_START_TIME = "cooling_program_start_time"
        const val REQUEST_END_TIME = "cooling_program_end_time"
    }
}
