package com.aqua.aqualight.application.aquarium.health.observation

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisPolicy
import java.util.Collections

/** Local entity IDs identify registered specimens/groups; catalog IDs never identify observations. */
enum class HealthObservationKind { ALGAE, PLANT, LIVESTOCK }
enum class ObservationPhase { OBSERVATION, INTERVENTION, FOLLOW_UP }
enum class AlgaeLocation { GLASS, SUBSTRATE, PLANT, DECORATION, EQUIPMENT, WATER, UNKNOWN }
enum class AlgaeAppearance { FILM, THREADS, SPOTS, CLOUDINESS, UNKNOWN }
enum class AlgaeExtent { LOCAL, MULTIPLE_AREAS, WIDESPREAD, UNKNOWN }
enum class PlantFinding { LEAF_DISCOLORATION, LEAF_DAMAGE, GROWTH_CHANGE, ROOT_CHANGE, ALGAE_PRESENT, UNKNOWN }
enum class LivestockFinding { BEHAVIOR_CHANGE, APPETITE_CHANGE, BREATHING_CHANGE, VISIBLE_MARKS, INJURY, UNKNOWN }
enum class ReportedCo2Pattern { STEADY, VARIABLE, NOT_USED, UNKNOWN }

data class ObservationIdentity(val tankId: Long, val observedAtMillis: Long, val requestId: String)
data class ObservationFollowUp(val phase: ObservationPhase, val previousObservationId: Long?)
data class ObservationNotes(val text: String, val photoUri: String?, val followUp: ObservationFollowUp)

/** User observations, never inferred from the presence of equipment or a fertilizer product. */
data class ObservationOperatingEvidence(
    val photoperiodMinutes: Int?,
    val lightMeasurement: String,
    val co2Pattern: ReportedCo2Pattern,
    val dosingHistory: String
)

sealed interface HealthObservation {
    val kind: HealthObservationKind
    val subjectId: Long?

    data class Algae(
        val locations: Set<AlgaeLocation>,
        val appearances: Set<AlgaeAppearance>,
        val extent: AlgaeExtent,
        val operatingEvidence: ObservationOperatingEvidence
    ) : HealthObservation {
        override val kind = HealthObservationKind.ALGAE
        override val subjectId: Long? = null
    }

    data class Plant(val plantId: Long, val findings: Set<PlantFinding>) : HealthObservation {
        override val kind = HealthObservationKind.PLANT
        override val subjectId get() = plantId
    }

    data class Livestock(
        val livestockId: Long,
        val affectedQuantity: Int?,
        val findings: Set<LivestockFinding>
    ) : HealthObservation {
        override val kind = HealthObservationKind.LIVESTOCK
        override val subjectId get() = livestockId
    }
}

data class HealthObservationInput(
    val identity: ObservationIdentity,
    val observation: HealthObservation,
    val notes: ObservationNotes
)

internal object HealthObservationPolicy {
    const val MAX_NOTE_LENGTH = 4000
    private const val MINUTES_PER_DAY = 1440

    fun validate(input: HealthObservationInput, nowMillis: Long): HealthObservationInput {
        require(input.identity.tankId > 0 && WaterAnalysisPolicy.isValidRequestId(input.identity.requestId))
        require(input.identity.observedAtMillis in
            WaterAnalysisPolicy.MIN_DATE_MILLIS..WaterAnalysisPolicy.MAX_DATE_MILLIS)
        require(input.identity.observedAtMillis <= nowMillis + WaterAnalysisPolicy.FUTURE_TOLERANCE_MILLIS)
        require(input.notes.text.length <= MAX_NOTE_LENGTH)
        val followUp = input.notes.followUp
        require(followUp.previousObservationId == null || followUp.previousObservationId > 0L)
        require(followUp.phase == ObservationPhase.OBSERVATION || followUp.previousObservationId != null)
        require(followUp.phase != ObservationPhase.INTERVENTION || input.notes.text.isNotBlank())
        return input.copy(observation = freeze(input.observation))
    }

    fun freeze(observation: HealthObservation): HealthObservation = when (observation) {
            is HealthObservation.Algae -> validateAlgae(observation)
            is HealthObservation.Plant -> {
                require(observation.plantId > 0L)
                observation.copy(findings = selected(observation.findings, PlantFinding.UNKNOWN))
            }
            is HealthObservation.Livestock -> {
                require(observation.livestockId > 0L)
                require(observation.affectedQuantity == null || observation.affectedQuantity > 0)
                observation.copy(findings = selected(observation.findings, LivestockFinding.UNKNOWN))
            }
        }

    private fun validateAlgae(value: HealthObservation.Algae): HealthObservation.Algae {
        val operating = value.operatingEvidence
        require(operating.photoperiodMinutes == null || operating.photoperiodMinutes in 0..MINUTES_PER_DAY)
        require(operating.lightMeasurement.length <= MAX_NOTE_LENGTH &&
            operating.dosingHistory.length <= MAX_NOTE_LENGTH)
        return value.copy(locations = selected(value.locations, AlgaeLocation.UNKNOWN),
            appearances = selected(value.appearances, AlgaeAppearance.UNKNOWN))
    }

    private fun <T> selected(values: Set<T>, unknown: T): Set<T> {
        require(values.isNotEmpty() && (unknown !in values || values.size == 1))
        return Collections.unmodifiableSet(LinkedHashSet(values))
    }
}
