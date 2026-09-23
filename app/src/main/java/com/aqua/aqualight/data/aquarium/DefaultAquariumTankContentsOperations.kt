package com.aqua.aqualight.data.aquarium

import android.content.Context
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.application.aquarium.AquariumTankContentsOperations
import com.aqua.aqualight.data.aquarium.catalog.livestock.LivestockSelectionValidator
import com.aqua.aqualight.data.aquarium.health.AquariumHealthDataStoreManager
import com.aqua.aqualight.data.aquarium.model.TankPlantTag
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.user.withCurrentOwnerScope

internal class DefaultAquariumTankContentsOperations(
    context: Context,
    private val tankStore: AquariumTankDataStoreManager,
    private val healthStore: AquariumHealthDataStoreManager
) : AquariumTankContentsOperations {

    private val livestockSelectionValidator =
        LivestockSelectionValidator(context.applicationContext)

    override suspend fun updateTankMaterials(
        tankId: Long,
        categoryKey: String,
        materials: List<AquariumMaterialSelection>
    ) = tankStore.updateTankMaterialsForCategory(
        tankId = tankId,
        categoryKey = categoryKey,
        materials = materials.map(
            AquariumMaterialSelection::toDataSelection
        )
    )

    override suspend fun updateTankPlants(
        tankId: Long,
        plants: List<AquariumPlantTag>
    ) = withCurrentOwnerScope { ownerUid ->
        val existingTank = tankStore
            .tanksSnapshotForOwner(ownerUid)
            .firstOrNull { tank -> tank.id == tankId }
            ?: throw IllegalArgumentException(
                "Tank not found for the active owner."
            )

        val previousPlants = existingTank.plants.map { plant ->
            TankPlantTag(
                id = plant.id,
                catalogId = plant.catalogId,
                plantName = plant.plantName,
                category = plant.category,
                markerX = plant.markerX,
                markerY = plant.markerY
            )
        }
        val updatedPlants =
            plants.map(AquariumPlantTag::toDataTag)

        val updateError = runCatching {
            tankStore.updateTankPlants(
                tankId,
                updatedPlants
            )
            healthStore.plantObservations.retainPlantTargets(
                ownerUid = ownerUid,
                tankId = tankId,
                validPlantIds = plants.mapTo(
                    mutableSetOf()
                ) { plant -> plant.id }
            )
        }.exceptionOrNull()

        if (updateError != null) {
            runCatching {
                tankStore.updateTankPlants(
                    tankId,
                    previousPlants
                )
            }.exceptionOrNull()?.let(
                updateError::addSuppressed
            )
            throw updateError
        }
        Unit
    }

    override suspend fun addLivestock(
        tankId: Long,
        livestock: AquariumLivestock
    ) {
        livestockSelectionValidator.requireCurrent(livestock)
        tankStore.addLivestockToTank(
            tankId,
            livestock.toDataLivestock()
        )
    }

    override suspend fun updateLivestock(
        tankId: Long,
        livestock: AquariumLivestock
    ) {
        livestockSelectionValidator.requireCurrent(livestock)
        tankStore.updateLivestockInTank(
            tankId,
            livestock.toDataLivestock()
        )
    }

    override suspend fun removeLivestock(
        tankId: Long,
        livestockId: Long
    ) = withCurrentOwnerScope { ownerUid ->
        tankStore.removeLivestockFromTank(
            tankId,
            livestockId
        )
        healthStore.livestockObservations
            .removeForLivestock(
                ownerUid = ownerUid,
                tankId = tankId,
                livestockId = livestockId
            )
    }
}
