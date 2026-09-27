package com.aqua.aqualight.data.aquarium.health.observation

import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContext
import com.aqua.aqualight.application.aquarium.health.context.HealthCatalogRevisions
import com.aqua.aqualight.application.aquarium.health.context.HealthContextCapture
import com.aqua.aqualight.application.aquarium.health.context.HealthEntityResolution
import com.aqua.aqualight.application.aquarium.health.context.HealthPlantContext
import com.aqua.aqualight.application.aquarium.health.context.HealthTankFacts
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservation
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationInput
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationPreparation
import com.aqua.aqualight.application.aquarium.health.observation.ObservationFollowUp
import com.aqua.aqualight.application.aquarium.health.observation.ObservationIdentity
import com.aqua.aqualight.application.aquarium.health.observation.ObservationNotes
import com.aqua.aqualight.application.aquarium.health.observation.ObservationPhase
import com.aqua.aqualight.application.aquarium.health.observation.ObservationWaterEvidencePolicy
import com.aqua.aqualight.application.aquarium.health.observation.ObservationWaterWindow
import com.aqua.aqualight.application.aquarium.health.observation.PlantFinding
import java.util.UUID

internal object HealthRoomFixture {
    const val TIME = 1_780_000_000_000L
    const val OWNER = "health-room-owner"
    const val TANK = 2L

    fun prepared(tank: Long = TANK, plant: Long = 3L, observedAt: Long = TIME): PreparedHealthObservation {
        val context = AquariumHealthContext(HealthContextCapture(TIME, "test-context-v1"),
            HealthTankFacts(tank, "Freshwater Fish", "Freshwater", null, null), HealthCatalogRevisions("p1", "l1"),
            listOf(HealthPlantContext(plant, "same-catalog", "Original plant", HealthEntityResolution.PARTIAL, null)),
            emptyList(), emptyList())
        val input = HealthObservationInput(ObservationIdentity(tank, observedAt, UUID.randomUUID().toString()),
            HealthObservation.Plant(plant, setOf(PlantFinding.LEAF_DAMAGE)),
            ObservationNotes("Recorded finding", null, ObservationFollowUp(ObservationPhase.OBSERVATION, null)))
        val water = ObservationWaterEvidencePolicy.resolve(tank, observedAt, null,
            ObservationWaterWindow(100, "test-window"))
        return prepareHealthObservation(input, HealthObservationPreparation(context, water))
    }
}
