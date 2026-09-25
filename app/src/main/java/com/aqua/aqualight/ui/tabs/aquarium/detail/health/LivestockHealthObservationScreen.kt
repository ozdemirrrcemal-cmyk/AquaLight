package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.view.ViewGroup
import android.widget.LinearLayout
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.BaselineChange
import com.aqua.aqualight.application.aquarium.LivestockHealthSymptom
import com.aqua.aqualight.application.aquarium.LivestockHealthTrend
import com.aqua.aqualight.ui.common.dialog.AppDatePickerDialogFragment
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.SingleChoiceBottomSheet

private const val SYMPTOMS_PER_ROW = 3
private const val MAX_SELECTED_SYMPTOMS = 5

internal fun LivestockHealthFragment.isValidObservation(item: AquariumLivestock): Boolean {
    if (saving || selectedSymptoms.isEmpty()) return false
    if (affectedCount !in 1..item.quantity) return false
    if (LivestockHealthSymptom.SURFACE_FREQUENCY_CHANGE in selectedSymptoms &&
        baseline == null
    ) return false
    return true
}

internal fun LivestockHealthFragment.renderForm(current: AquariumTankSnapshot, content: LinearLayout) {
        content.addView(LivestockHealthFormUi(requireContext()).progress())
        section(content, R.string.livestock_health_form_species_title)
        content.addView(ui.text(getString(R.string.livestock_health_form_species_hint),
            colorRes = R.color.aqua_card_text_secondary))
        content.addView(ui.spacer())
        current.livestock.forEach { item ->
            content.addView(ui.speciesChoice(item,
                selectedLivestockId == item.id) {
                selectedLivestockId = item.id
                affectedCount = affectedCount.coerceIn(1, item.quantity)
                selectedSymptoms.clear()
                baseline = null
                render(current)
            })
            content.addView(ui.spacer(R.dimen.aqua_size_8))
        }
        val selected = current.livestock.firstOrNull { it.id == selectedLivestockId }
        if (selected != null) renderFormDetails(current, selected, content)
        primary(R.string.livestock_health_form_save,
            selected?.let { isValidObservation(it) } == true) {
            saveObservation()
        }
    }

internal fun LivestockHealthFragment.renderFormDetails(
        current: AquariumTankSnapshot, selected: AquariumLivestock, content: LinearLayout
    ) {
        renderSymptomPicker(current, selected, content)
        renderBaselinePicker(current, content)
        renderCountAndDate(current, selected, content)
        renderPhotoAndNote(selected, content)
    }

private fun LivestockHealthFragment.renderSymptomPicker(
    current: AquariumTankSnapshot, selected: AquariumLivestock, content: LinearLayout
) {
        section(content, R.string.livestock_health_form_symptoms_title)
        content.addView(ui.text(getString(R.string.livestock_health_form_symptoms_hint),
            colorRes = R.color.aqua_card_text_secondary))
        content.addView(ui.spacer())
        symptomsFor(selected.category).chunked(SYMPTOMS_PER_ROW).forEach { group ->
            val row = ui.row()
            group.forEach { symptom ->
                row.addView(ui.symptomChoice(symptom, symptom in selectedSymptoms) {
                    if (!selectedSymptoms.remove(symptom) &&
                        selectedSymptoms.size < MAX_SELECTED_SYMPTOMS) {
                        selectedSymptoms += symptom
                    }
                    if (LivestockHealthSymptom.SURFACE_FREQUENCY_CHANGE !in selectedSymptoms) baseline = null
                    render(current)
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = ui.size(R.dimen.aqua_size_8)
                    }
                    minimumHeight = ui.size(R.dimen.aqua_size_60)
                })
            }
            content.addView(row)
            content.addView(ui.spacer(R.dimen.aqua_size_8))
        }
}

private fun LivestockHealthFragment.renderBaselinePicker(
    current: AquariumTankSnapshot, content: LinearLayout
) {
        if (LivestockHealthSymptom.SURFACE_FREQUENCY_CHANGE in selectedSymptoms) {
            val notice = ui.row()
            notice.addView(ui.text(getString(R.string.livestock_health_form_baseline_general),
                R.dimen.aqua_text_size_body_small).apply {
                layoutParams = LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 2f)
            })
            listOf(BaselineChange.YES to R.string.livestock_health_form_yes,
                BaselineChange.UNSURE to R.string.livestock_health_form_unsure).forEach { (value, label) ->
                notice.addView(ui.compactChoice(getString(label), baseline == value) {
                    baseline = value
                    render(current)
                }.apply { layoutParams = LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            }
            content.addView(ui.card(content = ui.column().apply { addView(notice) }))
        }
}

private fun LivestockHealthFragment.renderCountAndDate(
    current: AquariumTankSnapshot, selected: AquariumLivestock, content: LinearLayout
) {
        section(content, R.string.livestock_health_form_count_title)
        content.addView(ui.text(getString(R.string.livestock_health_form_count_hint,
            selected.quantity), colorRes = R.color.aqua_card_text_secondary))
        content.addView(ui.spacer(R.dimen.aqua_size_8))
        content.addView(ui.countStepper(affectedCount, selected.quantity, onDecrease = {
            affectedCount = (affectedCount - 1).coerceAtLeast(1)
            render(current)
        }, onIncrease = {
            affectedCount = (affectedCount + 1).coerceAtMost(selected.quantity)
            render(current)
        }))
        section(content, R.string.livestock_health_form_when_title)
        val dateAndTrend = ui.row()
        dateAndTrend.addView(ui.choice(getString(R.string.livestock_health_form_started_label,
            if (startedAtMillis > 0L) date(startedAtMillis)
            else getString(R.string.livestock_health_form_started_today)), false) {
            AppDatePickerDialogFragment.show(childFragmentManager, DATE_REQUEST,
                startedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
                maxMillis = System.currentTimeMillis())
        }.apply { layoutParams = LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        dateAndTrend.addView(ui.choice(getString(R.string.livestock_health_form_trend_label,
            getString(trendOptions().first { it.first == trend }.second)), false) {
            SingleChoiceBottomSheet.show(childFragmentManager,
                getString(R.string.livestock_health_form_trend_title),
                trendOptions().filterNot { it.first == LivestockHealthTrend.RESOLVED }
                    .map { it.first.code to getString(it.second) },
                trend.code, 3, TREND_REQUEST)
        }.apply { layoutParams = LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        content.addView(dateAndTrend)
}

private fun LivestockHealthFragment.renderPhotoAndNote(
    selected: AquariumLivestock, content: LinearLayout
) {
        section(content, R.string.livestock_health_form_photo_title)
        content.addView(LivestockHealthFormUi(requireContext()).photoPicker(
            selected, photoUri) {
            PhotoSourceBottomSheet.newInstance(getString(R.string.livestock_health_form_photo_title),
                !photoUri.isNullOrBlank()).show(childFragmentManager, PhotoSourceBottomSheet.TAG)
        })
        content.addView(ui.spacer())
        noteField(content, note) { note = it }
    }
