package com.aqua.aqualight.data.aquarium.health.observation

import com.aqua.aqualight.application.aquarium.health.algae.AlgaeAssessmentEngine
import com.aqua.aqualight.application.aquarium.health.plant.PlantHealthAssessmentEngine
import com.aqua.aqualight.application.aquarium.health.livestock.LivestockHealthAssessmentEngine
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservation
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationInput
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationPreparation
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationSubjectSnapshot
import com.aqua.aqualight.application.aquarium.health.observation.ObservationAssessment

internal data class PreparedHealthObservation(
    val input: HealthObservationInput,
    val preparation: HealthObservationPreparation,
    val subject: HealthObservationSubjectSnapshot?,
    val assessment: ObservationAssessment
)

internal fun prepareHealthObservation(input: HealthObservationInput,
    preparation: HealthObservationPreparation): PreparedHealthObservation {
    val context = preparation.context
    require(input.identity.tankId == context.tankId)
    val subject = when (val observation = input.observation) {
        is HealthObservation.Algae -> null
        is HealthObservation.Plant -> {
            val plant = requireNotNull(context.plants.singleOrNull { it.plantId == observation.plantId }) {
                "The selected registered plant is no longer available."
            }
            HealthObservationSubjectSnapshot(plant.plantId, plant.catalogId, plant.displayName, null, plant.resolution)
        }
        is HealthObservation.Livestock -> {
            val animal = requireNotNull(context.livestock.singleOrNull { it.livestockId == observation.livestockId }) {
                "The selected registered livestock group is no longer available."
            }
            HealthObservationSubjectSnapshot(animal.livestockId, animal.catalogId, animal.displayName,
                animal.quantity, animal.resolution)
        }
    }
    val assessment = when (val observation = input.observation) {
        is HealthObservation.Algae -> AlgaeAssessmentEngine.assess(observation, context, preparation.water)
        is HealthObservation.Plant -> PlantHealthAssessmentEngine.assess(observation, context, preparation.water)
        is HealthObservation.Livestock -> LivestockHealthAssessmentEngine.assess(
            observation, context, preparation.water)
    }
    return PreparedHealthObservation(input, preparation, subject, assessment)
}
