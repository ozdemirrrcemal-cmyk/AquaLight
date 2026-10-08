package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.PlantObservationRules
import com.aqua.aqualight.application.aquarium.health.PlantObservationSnapshot
import com.aqua.aqualight.databinding.FragmentPlantObservationRecordBinding
import com.aqua.aqualight.ui.common.dialog.ConfirmDialogFragment
import com.aqua.aqualight.utils.DialogType

internal const val PLANT_OBSERVATION_DELETE_REQUEST = "delete_plant_observation"

internal fun FragmentPlantObservationRecordBinding.renderSavedPlantObservation(
    record: PlantObservationSnapshot,
    photos: PlantObservationPhotos?
) {
    recordContainer.isVisible = true
    tvDate.text = root.context.plantObservationDate(record)
    tvSymptoms.text = root.context.plantObservationSigns(record)
    tvNote.text = record.note.ifBlank { root.context.getString(R.string.plant_health_record_no_note) }
    photos?.render(record.photoUris)
    algaeContainer.isVisible = PlantObservationRules.ALGAE in record.symptomKeys
}

internal fun Fragment.confirmPlantObservationDeletion() {
    ConfirmDialogFragment.show(childFragmentManager, ConfirmDialogFragment.Request(
        title = getString(R.string.plant_health_delete_title),
        message = getString(R.string.plant_health_delete_body),
        confirmText = getString(R.string.common_delete),
        cancelText = getString(R.string.common_cancel),
        presentation = ConfirmDialogFragment.Presentation(DialogType.WARNING, destructive = true),
        resultTarget = ConfirmDialogFragment.ResultTarget(PLANT_OBSERVATION_DELETE_REQUEST)
    ))
}
