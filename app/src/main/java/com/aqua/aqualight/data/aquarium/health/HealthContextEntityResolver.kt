package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.AquariumLivestockIdentity
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.LivestockCatalogItem
import com.aqua.aqualight.application.aquarium.catalog.plant.PlantCareCatalogSnapshot
import com.aqua.aqualight.application.aquarium.catalog.plant.PlantCareLookup
import com.aqua.aqualight.application.aquarium.health.context.HealthEntityResolution
import com.aqua.aqualight.application.aquarium.health.context.HealthLivestockContext
import com.aqua.aqualight.application.aquarium.health.context.HealthPlantContext

internal data class ResolvedHealthEntities(
    val plantRevision: String?,
    val livestockRevision: String?,
    val plants: List<HealthPlantContext>,
    val livestock: List<HealthLivestockContext>
)

internal object HealthContextEntityResolver {
    fun resolve(
        tank: AquariumTankSnapshot,
        plantCatalog: PlantCareCatalogSnapshot?,
        livestockCatalog: List<LivestockCatalogItem>?
    ): ResolvedHealthEntities {
        val animalsById = livestockCatalog?.associateBy { it.id }
        val plants = tank.plants.sortedBy { it.id }.map { plant ->
            val record = (plantCatalog?.find(plant.catalogId) as? PlantCareLookup.Found)?.record
            val resolution = when {
                plantCatalog == null -> HealthEntityResolution.CATALOG_UNAVAILABLE
                record == null -> HealthEntityResolution.CATALOG_MISSING
                record.care.healthAnalysisReady && record.care.healthDataStatus == "VERIFIED" ->
                    HealthEntityResolution.RESOLVED
                else -> HealthEntityResolution.PARTIAL
            }
            HealthPlantContext(plant.id, plant.catalogId, plant.plantName, resolution, record?.care)
        }
        val animals = tank.livestock.sortedBy { it.id }.map { animal ->
            val record = animalsById?.get(animal.catalogEntryId)
            val resolution = when {
                AquariumLivestockIdentity.isCustom(animal.catalogEntryId) -> HealthEntityResolution.CUSTOM_UNVERIFIED
                animalsById == null -> HealthEntityResolution.CATALOG_UNAVAILABLE
                record == null -> HealthEntityResolution.CATALOG_MISSING
                else -> HealthEntityResolution.RESOLVED
            }
            HealthLivestockContext(animal.id, animal.catalogEntryId, animal.name, animal.quantity,
                resolution, record?.waterGroup, record?.waterRequirements)
        }
        val revisions = livestockCatalog?.mapNotNull { it.waterRequirements.evidence?.catalogRevision }?.distinct()
        return ResolvedHealthEntities(plantCatalog?.revision, revisions?.singleOrNull(), plants, animals)
    }
}
