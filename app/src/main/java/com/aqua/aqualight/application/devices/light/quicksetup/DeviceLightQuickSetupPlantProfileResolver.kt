package com.aqua.aqualight.application.devices.light.quicksetup

import com.aqua.aqualight.application.aquarium.AquariumPlantLightCatalog
import com.aqua.aqualight.application.aquarium.AquariumPlantLightDemand

object DeviceLightQuickSetupPlantProfileResolver {
    fun resolve(context: DeviceLightQuickSetupContext): DeviceLightQuickSetupPlantProfile? {
        val records = context.plants.mapNotNull { plant ->
            AquariumPlantLightCatalog.record(plant.catalogId)
        }
        val complete = context.plants.isNotEmpty() && records.size == context.plants.size
        val counts = records.groupingBy { record -> record.lightDemand }.eachCount()
        return if (!complete) {
            null
        } else {
            DeviceLightQuickSetupPlantProfile(
                selectedPlantCount = context.plants.size,
                uniqueSpeciesCount = context.plants.map { plant -> plant.catalogId }.distinct().size,
                lowDemandCount = counts[AquariumPlantLightDemand.LOW] ?: 0,
                mediumDemandCount = counts[AquariumPlantLightDemand.MEDIUM] ?: 0,
                highDemandCount = counts[AquariumPlantLightDemand.HIGH] ?: 0,
                highestDemand = records.maxOf { record -> record.lightDemand },
                plantCatalogRevision = AquariumPlantLightCatalog.CATALOG_REVISION
            )
        }
    }
}
