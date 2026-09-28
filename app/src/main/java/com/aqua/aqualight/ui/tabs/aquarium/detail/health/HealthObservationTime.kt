package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.fragment.app.Fragment
import com.aqua.aqualight.databinding.FragmentHealthObservationFormBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.dialog.AppDatePickerDialogFragment
import com.aqua.aqualight.ui.common.dialog.AppTimePickerDialogFragment
import java.util.Calendar
import java.util.TimeZone

internal class HealthObservationTime(
    private val fragment: Fragment,
    private val ui: FragmentHealthObservationFormBinding,
    private val model: HealthObservationViewModel
) {
    private val selected = Calendar.getInstance(
        model.draft.getString("time_zone")?.let(TimeZone::getTimeZone) ?: TimeZone.getDefault()
    ).apply { if (model.draft.containsKey("observed")) timeInMillis = model.draft.getLong("observed") }

    fun bind() {
        fragment.childFragmentManager.setFragmentResultListener(DATE, fragment.viewLifecycleOwner) { _, result ->
            if (result.getString(AppDatePickerDialogFragment.RESULT_KEY) ==
                AppDatePickerDialogFragment.RESULT_SELECTED) {
                WaterAnalysisObservationTime.withDate(selected,
                    result.getLong(AppDatePickerDialogFragment.RESULT_MILLIS))
                render()
            }
        }
        fragment.childFragmentManager.setFragmentResultListener(TIME, fragment.viewLifecycleOwner) { _, result ->
            if (result.getString(AppTimePickerDialogFragment.RESULT_KEY) ==
                AppTimePickerDialogFragment.RESULT_SELECTED) {
                WaterAnalysisObservationTime.withTime(selected,
                    result.getLong(AppTimePickerDialogFragment.RESULT_MILLIS))
                render()
            }
        }
        ui.date.setOnClickListener {
            AppDatePickerDialogFragment.show(fragment.childFragmentManager, DATE,
                selected.timeInMillis, zone = selected.timeZone)
        }
        ui.time.setOnClickListener {
            AppTimePickerDialogFragment.show(fragment.childFragmentManager, TIME,
                selected.timeInMillis, zone = selected.timeZone)
        }
        render()
    }

    private fun render() {
        model.draft = model.draft.apply {
            putLong("observed", selected.timeInMillis)
            putString("time_zone", selected.timeZone.id)
        }
        ui.date.text = LocaleFormatter.formatDate(fragment.requireContext(), selected.timeInMillis, selected.timeZone)
        ui.time.text = LocaleFormatter.formatTime(fragment.requireContext(), selected.timeInMillis, selected.timeZone)
    }

    private companion object {
        const val DATE = "health_observation_date"
        const val TIME = "health_observation_time"
    }
}
