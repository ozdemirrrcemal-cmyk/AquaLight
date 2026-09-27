package com.aqua.aqualight.smoke

import com.aqua.aqualight.application.aquarium.AquariumLivestockIdentity
import com.aqua.aqualight.application.aquarium.health.observation.AlgaeAppearance
import com.aqua.aqualight.application.aquarium.health.observation.AlgaeExtent
import com.aqua.aqualight.application.aquarium.health.observation.AlgaeLocation
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservation
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationInput
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationKind
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationOperations
import com.aqua.aqualight.application.aquarium.health.observation.LivestockFinding
import com.aqua.aqualight.application.aquarium.health.observation.ObservationFollowUp
import com.aqua.aqualight.application.aquarium.health.observation.ObservationIdentity
import com.aqua.aqualight.application.aquarium.health.observation.ObservationNotes
import com.aqua.aqualight.application.aquarium.health.observation.ObservationOperatingEvidence
import com.aqua.aqualight.application.aquarium.health.observation.ObservationPhase
import com.aqua.aqualight.application.aquarium.health.observation.PlantFinding
import com.aqua.aqualight.application.aquarium.health.observation.ReportedCo2Pattern
import com.aqua.aqualight.data.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.aquarium.model.TankPlantTag
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.user.UserDataScope
import java.util.UUID
import kotlinx.coroutines.flow.first

/** Smoke-only owned data, written through the same operations as real observations. */
internal data class HealthObservationSmokeData(val tankId: Long, val records: Map<HealthObservationKind, Long>)

internal class HealthObservationSmokeFixture(
    private val owner: String, private val tanks: AquariumTankDataStoreManager,
    private val operations: HealthObservationOperations
) {
    suspend fun create(): HealthObservationSmokeData = UserDataScope.withOwnerUid(owner) {
        val tankId = tanks.tanksSnapshotForOwner(owner).singleOrNull { it.name == TANK_NAME }?.id
            ?: tanks.addTankFromDraft(TankDraft(name = TANK_NAME, widthCm = 60, lengthCm = 40, heightCm = 40,
                tankType = "Planted", plants = listOf(TankPlantTag(PLANT_ID, "smoke-plant",
                    "Recorded plant", "Foreground"))))
        val tank = tanks.tanksSnapshotForOwner(owner).single { it.id == tankId }
        if (tank.livestock.none { it.id == LIVESTOCK_ID }) tanks.addLivestockToTank(tankId,
            SavedAquariumLivestock(id = LIVESTOCK_ID, name = "Recorded livestock", category = "Fish",
                quantity = 3, catalogEntryId = AquariumLivestockIdentity.custom(LIVESTOCK_ID)))
        val records = HealthObservationKind.entries.associateWith { kind ->
            val request = UUID.nameUUIDFromBytes("health-smoke:$tankId:$kind".toByteArray(Charsets.UTF_8))
            val input = HealthObservationInput(ObservationIdentity(tankId, OBSERVED_AT, request.toString()),
                observation(kind), ObservationNotes("Recorded observation for minified UI verification", null,
                    ObservationFollowUp(ObservationPhase.OBSERVATION, null)))
            operations.save(input).also { id ->
                check(operations.save(input) == id) { "Health smoke retry duplicated an observation" }
                check(operations.observation(tankId, id).first()?.input == input)
            }
        }
        HealthObservationSmokeData(tankId, records)
    }

    private fun observation(kind: HealthObservationKind): HealthObservation = when (kind) {
        HealthObservationKind.ALGAE -> HealthObservation.Algae(setOf(AlgaeLocation.GLASS),
            setOf(AlgaeAppearance.FILM), AlgaeExtent.LOCAL,
            ObservationOperatingEvidence(null, "", ReportedCo2Pattern.UNKNOWN, ""))
        HealthObservationKind.PLANT -> HealthObservation.Plant(PLANT_ID,
            setOf(PlantFinding.LEAF_DAMAGE, PlantFinding.ALGAE_PRESENT))
        HealthObservationKind.LIVESTOCK -> HealthObservation.Livestock(LIVESTOCK_ID, 1,
            setOf(LivestockFinding.APPETITE_CHANGE))
    }
    private companion object {
        const val TANK_NAME = "Health observation smoke"
        const val PLANT_ID = 3L
        const val LIVESTOCK_ID = 4L
        const val OBSERVED_AT = 1_780_000_000_000L
    }
}
