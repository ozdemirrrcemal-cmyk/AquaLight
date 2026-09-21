package com.aqua.aqualight.application.devices.light.quicksetup

import com.aqua.aqualight.application.aquarium.AquariumPlantLightCatalog
import com.aqua.aqualight.application.aquarium.AquariumPlantLightDemand

object DeviceLightQuickSetupPlantProfileResolver {
    fun resolve(context: DeviceLightQuickSetupContext): DeviceLightQuickSetupPlantProfile? {
        if (context.plants.isEmpty()) return null
        val records = context.plants.map { plant ->
            AquariumPlantLightCatalog.record(plant.catalogId) ?: return null
        }
        val counts = records.groupingBy { record -> record.lightDemand }.eachCount()
        return DeviceLightQuickSetupPlantProfile(
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
