package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.Context
import android.os.Bundle
import com.aqua.aqualight.application.aquarium.health.observation.AlgaeAppearance
import com.aqua.aqualight.application.aquarium.health.observation.AlgaeExtent
import com.aqua.aqualight.application.aquarium.health.observation.AlgaeLocation
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservation
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationInput
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationKind
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationPolicy
import com.aqua.aqualight.application.aquarium.health.observation.LivestockFinding
import com.aqua.aqualight.application.aquarium.health.observation.ObservationFollowUp
import com.aqua.aqualight.application.aquarium.health.observation.ObservationIdentity
import com.aqua.aqualight.application.aquarium.health.observation.ObservationNotes
import com.aqua.aqualight.application.aquarium.health.observation.ObservationOperatingEvidence
import com.aqua.aqualight.application.aquarium.health.observation.ObservationPhase
import com.aqua.aqualight.application.aquarium.health.observation.PlantFinding
import com.aqua.aqualight.application.aquarium.health.observation.ReportedCo2Pattern
import com.aqua.aqualight.i18n.LocaleFormatter

internal fun HealthObservationViewModel.formInput(context: Context): HealthObservationInput {
    val values = draft
    val observation = when (kind) {
        HealthObservationKind.ALGAE -> HealthObservation.Algae(values.selected<AlgaeLocation>("locations"),
            values.selected<AlgaeAppearance>("appearances"), values.selected<AlgaeExtent>("extent").single(),
            ObservationOperatingEvidence(values.optionalInteger(context, "photoperiod"),
                values.getString("light").orEmpty(), values.selected<ReportedCo2Pattern>("co2").single(),
                values.getString("dosing").orEmpty()))
        HealthObservationKind.PLANT -> HealthObservation.Plant(values.getLong("subject"),
            values.selected<PlantFinding>("findings"))
        HealthObservationKind.LIVESTOCK -> HealthObservation.Livestock(values.getLong("subject"),
            values.optionalInteger(context, "affected"), values.selected<LivestockFinding>("findings"))
    }
    val phase = if (previousId == 0L) ObservationPhase.OBSERVATION
        else values.selected<ObservationPhase>("phases").single()
    val input = HealthObservationInput(ObservationIdentity(tankId, values.getLong("observed"), requestId), observation,
        ObservationNotes(values.getString("notes").orEmpty(), values.getString("photo"),
            ObservationFollowUp(phase, previousId.takeIf { it > 0L })))
    return HealthObservationPolicy.validate(input, System.currentTimeMillis())
}

private inline fun <reified T : Enum<T>> Bundle.selected(key: String): Set<T> =
    getStringArrayList(key).orEmpty().map { enumValueOf<T>(it) }.toSet()

private fun Bundle.optionalInteger(context: Context, key: String): Int? {
    val value = getString(key).orEmpty().trim()
    if (value.isEmpty()) return null
    val number = requireNotNull(LocaleFormatter.parseDecimal(context, value))
    require(number.isFinite() && number in 0.0..Int.MAX_VALUE.toDouble() && number % 1.0 == 0.0)
    return number.toInt()
}
