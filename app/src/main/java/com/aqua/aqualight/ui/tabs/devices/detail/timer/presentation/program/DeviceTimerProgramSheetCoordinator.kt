package com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.program

import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.bottomsheet.AquaTimePickerBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet

/** Keeps program editor sheet navigation outside the Fragment lifecycle shell. */
internal class DeviceTimerProgramSheetCoordinator(
    private val fragment: Fragment,
    private val state: () -> DeviceTimerProgramUiState
) {
    fun showName(slotId: Int) {
        val schedule = state().schedules.singleOrNull { it.slotId == slotId } ?: return
        TextInputBottomSheet.show(
            fragmentManager = fragment.parentFragmentManager,
            title = fragment.getString(R.string.device_timer_program_name_sheet_title),
            label = fragment.getString(R.string.device_timer_program_name_sheet_label),
            hint = fragment.getString(R.string.device_timer_program_name_sheet_hint),
            initialValue = schedule.name,
            supportingText = fragment.getString(R.string.device_timer_program_name_sheet_helper),
            saveText = fragment.getString(R.string.device_timer_apply),
            cancelText = fragment.getString(R.string.device_timer_cancel),
            required = true,
            requiredMessage = fragment.getString(R.string.device_timer_program_name_required),
            requestKey = REQUEST_NAME,
            payloadId = slotId.toString(),
            maxLength = MAX_NAME_CHARACTERS,
            disableSaveWhenUnchanged = true,
            requestFocus = true
        )
    }

    fun showTime(slotId: Int, start: Boolean) {
        val schedule = state().schedules.singleOrNull { it.slotId == slotId } ?: return
        val minutes = if (start) schedule.startMinutesOfDay else schedule.endMinutesOfDay
        AquaTimePickerBottomSheet.show(
            fragmentManager = fragment.parentFragmentManager,
            request = AquaTimePickerBottomSheet.Request(
                title = fragment.getString(
                    if (start) R.string.device_timer_program_start_sheet_title
                    else R.string.device_timer_program_end_sheet_title
                ),
                message = fragment.getString(R.string.device_timer_program_time_sheet_message),
                initialHour = minutes / MINUTES_PER_HOUR,
                initialMinute = minutes % MINUTES_PER_HOUR,
                confirmText = fragment.getString(R.string.device_timer_apply),
                cancelText = fragment.getString(R.string.device_timer_cancel),
                resultTarget = AquaTimePickerBottomSheet.ResultTarget(
                    requestKey = if (start) REQUEST_START else REQUEST_END,
                    payloadId = slotId.toString()
                )
            )
        )
    }

    fun registerResults(owner: LifecycleOwner, viewModel: DeviceTimerProgramViewModel) {
        fragment.parentFragmentManager.setFragmentResultListener(REQUEST_NAME, owner) { _, result ->
            if (result.getString(TextInputBottomSheet.RESULT_KEY) !=
                TextInputBottomSheet.RESULT_SAVED
            ) return@setFragmentResultListener
            val slotId = result.getString(TextInputBottomSheet.RESULT_PAYLOAD_ID)?.toIntOrNull()
                ?: return@setFragmentResultListener
            viewModel.edit(
                DeviceTimerProgramEdit.UpdateName(
                    slotId,
                    result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty()
                )
            )
        }
        registerTimeResult(owner, REQUEST_START) { slotId, minutes ->
            viewModel.edit(DeviceTimerProgramEdit.UpdateStartTime(slotId, minutes))
        }
        registerTimeResult(owner, REQUEST_END) { slotId, minutes ->
            viewModel.edit(DeviceTimerProgramEdit.UpdateEndTime(slotId, minutes))
        }
    }

    private fun registerTimeResult(
        owner: LifecycleOwner,
        requestKey: String,
        update: (Int, Int) -> Unit
    ) {
        fragment.parentFragmentManager.setFragmentResultListener(requestKey, owner) { _, result ->
            if (result.getString(AquaTimePickerBottomSheet.RESULT_KEY) !=
                AquaTimePickerBottomSheet.RESULT_SELECTED
            ) return@setFragmentResultListener
            val slotId = result.getString(AquaTimePickerBottomSheet.RESULT_PAYLOAD_ID)?.toIntOrNull()
                ?: return@setFragmentResultListener
            update(slotId, result.getInt(AquaTimePickerBottomSheet.RESULT_MINUTES_OF_DAY))
        }
    }

    private companion object {
        const val REQUEST_NAME = "timer_program_name"
        const val REQUEST_START = "timer_program_start"
        const val REQUEST_END = "timer_program_end"
        const val MINUTES_PER_HOUR = 60
        const val MAX_NAME_CHARACTERS = 48
    }
}
