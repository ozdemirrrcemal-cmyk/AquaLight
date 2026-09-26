package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.aqua.aqualight.databinding.ItemTankHealthAnalysisMeasurementTimeBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.dialog.AppDatePickerDialogFragment
import com.aqua.aqualight.ui.common.dialog.AppTimePickerDialogFragment
import java.util.Calendar

internal class WaterAnalysisMeasurementTimeController(
    private val fragment: Fragment,
    private val binding: ItemTankHealthAnalysisMeasurementTimeBinding,
    savedInstanceState: Bundle?
) {
    private val selectedCalendar = Calendar.getInstance().apply {
        val savedTime = savedInstanceState?.getLong(
            STATE_MEASUREMENT_TIME_MILLIS,
            NO_SAVED_TIME
        ) ?: NO_SAVED_TIME
        if (savedTime != NO_SAVED_TIME) {
            timeInMillis = savedTime
        }
    }

    fun bind() {
        fragment.childFragmentManager.setFragmentResultListener(
            DATE_PICKER_REQUEST_KEY,
            fragment.viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(AppDatePickerDialogFragment.RESULT_KEY) ==
                AppDatePickerDialogFragment.RESULT_SELECTED
            ) {
                selectedCalendar.timeInMillis = result.getLong(
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
                selectedCalendar.timeInMillis = result.getLong(
                    AppTimePickerDialogFragment.RESULT_MILLIS
                )
                selectedCalendar.set(Calendar.SECOND, 0)
                selectedCalendar.set(Calendar.MILLISECOND, 0)
                render()
            }
        }

        binding.cardDate.setOnClickListener {
            AppDatePickerDialogFragment.show(
                fragmentManager = fragment.childFragmentManager,
                requestKey = DATE_PICKER_REQUEST_KEY,
                initialMillis = selectedCalendar.timeInMillis
            )
        }
        binding.cardTime.setOnClickListener {
            AppTimePickerDialogFragment.show(
                fragmentManager = fragment.childFragmentManager,
                requestKey = TIME_PICKER_REQUEST_KEY,
                initialMillis = selectedCalendar.timeInMillis
            )
        }
        render()
    }

    fun saveState(outState: Bundle) {
        outState.putLong(STATE_MEASUREMENT_TIME_MILLIS, selectedCalendar.timeInMillis)
    }

    private fun render() {
        binding.tvDateValue.text = LocaleFormatter.formatDate(
            fragment.requireContext(),
            selectedCalendar.timeInMillis
        )
        binding.tvTimeValue.text = LocaleFormatter.formatTime(
            fragment.requireContext(),
            selectedCalendar.timeInMillis
        )
    }

    private companion object {
        const val DATE_PICKER_REQUEST_KEY = "tank_health_analysis_date_picker"
        const val TIME_PICKER_REQUEST_KEY = "tank_health_analysis_time_picker"
        const val STATE_MEASUREMENT_TIME_MILLIS = "measurement_time_millis"
        const val NO_SAVED_TIME = -1L
    }
}
