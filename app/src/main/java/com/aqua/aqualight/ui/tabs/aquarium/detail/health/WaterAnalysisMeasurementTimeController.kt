package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.fragment.app.Fragment
import com.aqua.aqualight.databinding.ItemTankHealthAnalysisMeasurementTimeBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.dialog.AppDatePickerDialogFragment
import com.aqua.aqualight.ui.common.dialog.AppTimePickerDialogFragment
import java.util.Calendar

internal class WaterAnalysisMeasurementTimeController(
    private val fragment: Fragment,
    private val binding: ItemTankHealthAnalysisMeasurementTimeBinding,
    private val state: WaterAnalysisDraftUiState
) {

    fun bind() {
        registerPickerResults()
        bindPickerActions()
        render()
    }

    fun render() {
        binding.tvDateValue.text = LocaleFormatter.formatDate(
            fragment.requireContext(),
            state.selectedCalendar.timeInMillis
        )
        binding.tvTimeValue.text = LocaleFormatter.formatTime(
            fragment.requireContext(),
            state.selectedCalendar.timeInMillis
        )
    }

    private fun registerPickerResults() {
        fragment.childFragmentManager.setFragmentResultListener(
            DATE_PICKER_REQUEST_KEY,
            fragment.viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(AppDatePickerDialogFragment.RESULT_KEY) ==
                AppDatePickerDialogFragment.RESULT_SELECTED
            ) {
                state.selectedCalendar.timeInMillis = result.getLong(
                    AppDatePickerDialogFragment.RESULT_MILLIS
                )
                render()
            }
        }

        fragment.childFragmentManager.setFragmentResultListener(
            TIME_PICKER_REQUEST_KEY,
            fragment.viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(AppTimePickerDialogFragment.RESULT_KEY) ==
                AppTimePickerDialogFragment.RESULT_SELECTED
            ) {
                state.selectedCalendar.timeInMillis = result.getLong(
                    AppTimePickerDialogFragment.RESULT_MILLIS
                )
                state.selectedCalendar.set(Calendar.SECOND, 0)
                state.selectedCalendar.set(Calendar.MILLISECOND, 0)
                render()
            }
        }
    }

    private fun bindPickerActions() {
        binding.cardDate.setOnClickListener {
            AppDatePickerDialogFragment.show(
                fragmentManager = fragment.childFragmentManager,
                requestKey = DATE_PICKER_REQUEST_KEY,
                initialMillis = state.selectedCalendar.timeInMillis
            )
        }

        binding.cardTime.setOnClickListener {
            AppTimePickerDialogFragment.show(
                fragmentManager = fragment.childFragmentManager,
                requestKey = TIME_PICKER_REQUEST_KEY,
                initialMillis = state.selectedCalendar.timeInMillis
            )
        }
    }

    private companion object {
        const val DATE_PICKER_REQUEST_KEY = "tank_health_analysis_date_picker"
        const val TIME_PICKER_REQUEST_KEY = "tank_health_analysis_time_picker"
    }
}
