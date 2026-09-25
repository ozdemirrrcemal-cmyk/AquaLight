package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.text.format.DateFormat
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.LivestockHealthObservation
import com.aqua.aqualight.application.aquarium.LivestockHealthTrend
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.common.dialog.AppDatePickerDialogFragment
import com.aqua.aqualight.ui.common.dialog.AppTimePickerDialogFragment
import java.util.Date

internal const val CHECK_DATE_REQUEST = "livestock_health_check_date"
internal const val CHECK_TIME_REQUEST = "livestock_health_check_time"

internal fun LivestockHealthCheckSheet.render(observation: LivestockHealthObservation) {
    body.removeAllViews()
    renderCheckHeading(observation)
    renderCheckTrend(observation)
    renderCheckCount(observation)
    renderCheckTime(observation)
    renderCheckPhotoAndFooter(observation)
}

private fun LivestockHealthCheckSheet.renderCheckHeading(observation: LivestockHealthObservation) {
        val header = ui.row()
        header.addView(ui.heading(R.string.livestock_health_check_title).apply {
            layoutParams = LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(ui.text(getString(R.string.common_close),
            R.dimen.aqua_text_size_title_large).apply {
            contentDescription = getString(R.string.livestock_health_check_close)
            isClickable = true
            isFocusable = true
            setOnClickListener { dismiss() }
        })
        body.addView(header)
        body.addView(ui.text(getString(R.string.livestock_health_check_context,
            observation.livestockName, observation.latestAffectedCount,
            maxCount(observation)), colorRes = R.color.aqua_card_text_secondary))
        body.addView(ui.spacer())
        body.addView(ui.heading(R.string.livestock_health_check_question))
        body.addView(ui.spacer(R.dimen.aqua_size_8))
}

private fun LivestockHealthCheckSheet.renderCheckTrend(observation: LivestockHealthObservation) {
        val trends = listOf(
            LivestockHealthTrend.INCREASING to R.string.livestock_health_form_increasing,
            LivestockHealthTrend.SAME to R.string.livestock_health_form_same,
            LivestockHealthTrend.DECREASING to R.string.livestock_health_form_decreasing,
            LivestockHealthTrend.RESOLVED to R.string.livestock_health_form_resolved
        )
        val trendsRow = ui.row()
        trends.forEach { (value, label) ->
            trendsRow.addView(ui.compactChoice(getString(label), value == checkTrend) {
                checkTrend = value
                render(observation)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = ui.size(R.dimen.aqua_size_8)
                }
            })
        }
        body.addView(trendsRow)
}

private fun LivestockHealthCheckSheet.renderCheckCount(observation: LivestockHealthObservation) {
        body.addView(ui.spacer())
        body.addView(ui.heading(R.string.livestock_health_check_count))
        val maximum = maxCount(observation)
        body.addView(ui.spacer(R.dimen.aqua_size_8))
        body.addView(ui.countStepper(checkCount, maximum, onDecrease = {
            checkCount = (checkCount - 1).coerceAtLeast(1)
            render(observation)
        }, onIncrease = {
            checkCount = (checkCount + 1).coerceAtMost(maximum)
            render(observation)
        }))
}

private fun LivestockHealthCheckSheet.renderCheckTime(observation: LivestockHealthObservation) {
        body.addView(ui.spacer())
        body.addView(ui.heading(R.string.livestock_health_check_when_title))
        body.addView(ui.spacer(R.dimen.aqua_size_8))
        val dateTime = ui.row()
        dateTime.addView(ui.choice(DateFormat.getDateFormat(requireContext()).format(Date(checkTimeMillis)),
            false) {
            val last = observation.checks.lastOrNull()?.observedAtMillis
                ?: observation.observedAtMillis
            AppDatePickerDialogFragment.show(childFragmentManager, CHECK_DATE_REQUEST,
                checkTimeMillis, minMillis = last, maxMillis = System.currentTimeMillis())
        }.apply { layoutParams = LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        dateTime.addView(ui.choice(DateFormat.getTimeFormat(requireContext()).format(Date(checkTimeMillis)),
            false) {
            AppTimePickerDialogFragment.show(childFragmentManager, CHECK_TIME_REQUEST, checkTimeMillis)
        }.apply { layoutParams = LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        body.addView(dateTime)
}

private fun LivestockHealthCheckSheet.renderCheckPhotoAndFooter(observation: LivestockHealthObservation) {
        body.addView(ui.spacer())
        body.addView(EditText(requireContext()).apply {
            hint = getString(R.string.livestock_health_form_note_hint)
            setText(checkNote)
            maxLines = CHECK_NOTE_MAX_LINES
            doAfterTextChanged { checkNote = it?.toString().orEmpty() }
        })
        body.addView(ui.spacer())
        body.addView(ui.heading(R.string.livestock_health_check_photo_title))
        if (!photoUri.isNullOrBlank()) {
            current?.livestock?.firstOrNull { it.id == observation.livestockId }?.let { item ->
                body.addView(ui.speciesImage(item, R.dimen.aqua_size_96,
                    R.dimen.aqua_size_96, photoUri))
                body.addView(ui.spacer(R.dimen.aqua_size_8))
            }
        }
        body.addView(ui.choice(if (photoUri.isNullOrBlank())
            getString(R.string.livestock_health_form_photo_add)
            else getString(R.string.livestock_health_form_photo_selected),
            !photoUri.isNullOrBlank()) {
            PhotoSourceBottomSheet.newInstance(getString(R.string.livestock_health_check_photo_title),
                !photoUri.isNullOrBlank()).show(childFragmentManager, PhotoSourceBottomSheet.TAG)
        })
        if (checkTrend == LivestockHealthTrend.RESOLVED) {
            body.addView(ui.text(getString(R.string.livestock_health_check_resolved_note),
                colorRes = R.color.aqua_card_text_secondary))
        }
        body.addView(ui.spacer())
        body.addView(ui.text(getString(R.string.livestock_health_assessment_missing_water),
            colorRes = R.color.aqua_card_text_secondary))
        body.addView(ui.spacer(R.dimen.aqua_size_8))
        body.addView(ui.button(R.string.livestock_health_check_save) { save(observation) })
}

