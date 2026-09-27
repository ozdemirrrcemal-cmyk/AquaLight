package com.aqua.aqualight.data.aquarium.health.observation

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

/** Stable Proto field numbers and enum names survive minification and retain raw user observations. */
internal object HealthObservationInputCodec {
    fun encode(input: HealthObservationInput): StoredHealthObservationInput = StoredHealthObservationInput.newBuilder()
        .setTankId(input.identity.tankId).setObservedAtMillis(input.identity.observedAtMillis)
        .setRequestId(input.identity.requestId).setKind(input.observation.kind.name)
        .setSubjectId(input.observation.subjectId ?: 0L)
        .setNote(input.notes.text).setPhotoUri(input.notes.photoUri.orEmpty())
        .setPhase(input.notes.followUp.phase.name).apply {
            input.notes.followUp.previousObservationId?.let(::setPreviousObservationId)
            when (val observation = input.observation) {
                is HealthObservation.Algae -> {
                    addAllFindings(observation.appearances.map { it.name }.sorted())
                    addAllAlgaeLocations(observation.locations.map { it.name }.sorted())
                    setAlgaeExtent(observation.extent.name)
                    setOperating(encodeOperating(observation.operatingEvidence))
                }
                is HealthObservation.Plant -> addAllFindings(observation.findings.map { it.name }.sorted())
                is HealthObservation.Livestock -> {
                    addAllFindings(observation.findings.map { it.name }.sorted())
                    observation.affectedQuantity?.let(::setAffectedQuantity)
                }
            }
        }.build()

    fun decode(value: StoredHealthObservationInput): HealthObservationInput {
        val kind = enumValueOf<HealthObservationKind>(value.kind)
        val observation = when (kind) {
            HealthObservationKind.ALGAE -> {
                require(value.subjectId == 0L && !value.hasAffectedQuantity() && value.hasOperating())
                HealthObservation.Algae(names<AlgaeLocation>(value.algaeLocationsList),
                    names<AlgaeAppearance>(value.findingsList), enumValueOf<AlgaeExtent>(value.algaeExtent),
                    decodeOperating(value.operating))
            }
            HealthObservationKind.PLANT -> {
                require(!value.hasAffectedQuantity() && !value.hasOperating() && value.algaeLocationsCount == 0)
                HealthObservation.Plant(value.subjectId, names<PlantFinding>(value.findingsList))
            }
            HealthObservationKind.LIVESTOCK -> {
                require(!value.hasOperating() && value.algaeLocationsCount == 0)
                HealthObservation.Livestock(value.subjectId,
                    value.affectedQuantity.takeIf { value.hasAffectedQuantity() },
                    names<LivestockFinding>(value.findingsList))
            }
        }
        return HealthObservationInput(ObservationIdentity(value.tankId, value.observedAtMillis, value.requestId),
            HealthObservationPolicy.freeze(observation), ObservationNotes(value.note, value.photoUri.ifBlank { null },
                ObservationFollowUp(enumValueOf<ObservationPhase>(value.phase),
                    value.previousObservationId.takeIf { value.hasPreviousObservationId() })))
    }

    private fun encodeOperating(value: ObservationOperatingEvidence): StoredHealthOperatingEvidence =
        StoredHealthOperatingEvidence.newBuilder().setLightMeasurement(value.lightMeasurement)
            .setCo2Pattern(value.co2Pattern.name).setDosingHistory(value.dosingHistory)
            .apply { value.photoperiodMinutes?.let(::setPhotoperiodMinutes) }.build()

    private fun decodeOperating(value: StoredHealthOperatingEvidence): ObservationOperatingEvidence =
        ObservationOperatingEvidence(value.photoperiodMinutes.takeIf { value.hasPhotoperiodMinutes() },
            value.lightMeasurement, enumValueOf<ReportedCo2Pattern>(value.co2Pattern), value.dosingHistory)

    private inline fun <reified T : Enum<T>> names(values: List<String>): Set<T> {
        require(values.distinct().size == values.size)
        return values.map { enumValueOf<T>(it) }.toSet()
    }
}
