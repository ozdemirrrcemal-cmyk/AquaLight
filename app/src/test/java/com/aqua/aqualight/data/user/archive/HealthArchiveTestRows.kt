package com.aqua.aqualight.data.user.archive

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
import com.aqua.aqualight.data.aquarium.health.observation.HealthObservationRecordCodec
import com.aqua.aqualight.data.aquarium.health.observation.prepareHealthObservation
import java.util.UUID

internal object HealthArchiveTestRows {
    const val TIME = 1_780_000_000_000L
    const val TANK = 7L

    fun row(id: Long = 1, photo: String? = null) = HealthObservationRecordCodec.encode("source-owner", id, TIME,
        prepareHealthObservation(
            HealthObservationInput(ObservationIdentity(TANK, TIME,
                UUID.nameUUIDFromBytes("request-$id".toByteArray()).toString()),
                HealthObservation.Plant(11, setOf(PlantFinding.LEAF_DAMAGE)),
                ObservationNotes("Historical finding", photo, ObservationFollowUp(ObservationPhase.OBSERVATION, null))),
            HealthObservationPreparation(
                AquariumHealthContext(HealthContextCapture(TIME, "original-context"),
                    HealthTankFacts(TANK, "Freshwater Fish", "Freshwater", null, null),
                    HealthCatalogRevisions("p1", "l1"),
                    listOf(HealthPlantContext(11, "plant-catalog", "Original plant",
                        HealthEntityResolution.PARTIAL, null)),
                    emptyList(), emptyList()),
                ObservationWaterEvidencePolicy.resolve(TANK, TIME, null, ObservationWaterWindow(100, "window-v1")))))
}
