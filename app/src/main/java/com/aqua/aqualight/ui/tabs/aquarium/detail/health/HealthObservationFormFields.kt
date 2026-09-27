package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.observation.AlgaeAppearance
import com.aqua.aqualight.application.aquarium.health.observation.AlgaeExtent
import com.aqua.aqualight.application.aquarium.health.observation.AlgaeLocation
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationKind
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationPreparation
import com.aqua.aqualight.application.aquarium.health.observation.LivestockFinding
import com.aqua.aqualight.application.aquarium.health.observation.ObservationPhase
import com.aqua.aqualight.application.aquarium.health.observation.PlantFinding
import com.aqua.aqualight.application.aquarium.health.observation.ReportedCo2Pattern
import com.aqua.aqualight.databinding.FragmentHealthObservationFormBinding

internal class HealthObservationFormFields(
    private val ui: FragmentHealthObservationFormBinding,
    private val model: HealthObservationViewModel
) {
    fun bind() {
        bindVisibility()
        bindChoices()
        listOf(ui.notes to "notes", ui.affected to "affected", ui.photoperiod to "photoperiod",
            ui.light to "light", ui.dosing to "dosing").forEach { (view, key) -> bindText(view, key) }
    }

    fun preparation(value: HealthObservationPreparation): Boolean {
        val subjects = when (model.kind) {
            HealthObservationKind.ALGAE -> emptyList()
            HealthObservationKind.PLANT -> value.context.plants.map { it.plantId to it.displayName }
            HealthObservationKind.LIVESTOCK -> value.context.livestock.map { it.livestockId to it.displayName }
        }
        ui.subject.setAdapter(ArrayAdapter(ui.root.context, android.R.layout.simple_dropdown_item_1line,
            subjects.map { it.second }))
        val selected = subjects.singleOrNull { it.first == model.draft.getLong("subject") }
        ui.subject.setText(selected?.second.orEmpty(), false)
        ui.subject.setOnItemClickListener { _, _, position, _ ->
            model.draft = model.draft.apply { putLong("subject", subjects[position].first) }
        }
        ui.subject.isEnabled = model.previousId == 0L
        return model.kind == HealthObservationKind.ALGAE || subjects.isNotEmpty() &&
            (model.previousId == 0L || selected != null)
    }

    private fun bindText(view: EditText, key: String) {
        view.setText(model.draft.getString(key).orEmpty())
        view.doAfterTextChanged { text -> model.draft = model.draft.apply { putString(key, text?.toString()) } }
    }

    private fun bindChoices() {
        if (model.kind == HealthObservationKind.PLANT) choices(ui.findings, "findings", PlantFinding.entries)
        if (model.kind == HealthObservationKind.LIVESTOCK) choices(ui.findings, "findings", LivestockFinding.entries)
        if (model.kind == HealthObservationKind.ALGAE) {
            choices(ui.locations, "locations", AlgaeLocation.entries)
            choices(ui.appearances, "appearances", AlgaeAppearance.entries)
            choices(ui.extent, "extent", AlgaeExtent.entries, single = true)
            choices(ui.co2, "co2", ReportedCo2Pattern.entries, single = true)
        }
        choices(ui.phases, "phases", listOf(ObservationPhase.FOLLOW_UP, ObservationPhase.INTERVENTION), single = true)
    }

    private fun bindVisibility() {
        val algae = model.kind == HealthObservationKind.ALGAE
        ui.subjectInput.isVisible = !algae
        ui.findings.isVisible = !algae
        ui.findingsLabel.isVisible = !algae
        listOf(ui.locations, ui.locationsLabel, ui.appearances, ui.appearancesLabel, ui.extent, ui.extentLabel,
            ui.operatingNote, ui.photoperiodInput, ui.lightInput, ui.co2, ui.co2Label, ui.dosingInput)
            .forEach { it.isVisible = algae }
        ui.affectedInput.isVisible = model.kind == HealthObservationKind.LIVESTOCK
        ui.phases.isVisible = model.previousId > 0L
        ui.phasesLabel.isVisible = model.previousId > 0L
        ui.parent.isVisible = model.previousId > 0L
    }

    private fun <T : Enum<T>> choices(view: LinearLayout, key: String, values: List<T>, single: Boolean = false) {
        view.healthChoices(values, model.draft.getStringArrayList(key).orEmpty().toSet(), single) { chosen ->
            model.draft = model.draft.apply { putStringArrayList(key, ArrayList(chosen)) }
        }
    }
}
