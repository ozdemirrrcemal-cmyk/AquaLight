package com.aqua.aqualight.ui.tabs.aquarium.catalog.plant

import android.content.Context
import androidx.annotation.StringRes
import com.aqua.aqualight.application.aquarium.AquariumPlantLightCatalog
import com.aqua.aqualight.application.aquarium.AquariumPlantLightDemand

data class AquariumPlantDefinition(
    @StringRes val nameRes: Int,
    @StringRes val categoryRes: Int
) {
    fun resolve(context: Context): AquariumPlant {
        val catalogId = "plant:" + context.resources.getResourceEntryName(nameRes)
            .removePrefix("catalog_plant_")
            .removeSuffix("_name")
        return AquariumPlant(
            catalogId = catalogId,
            name = context.getString(nameRes),
            category = context.getString(categoryRes),
            lightDemand = AquariumPlantLightCatalog.resolve(catalogId)
        )
    }
}

data class AquariumPlant(
    val catalogId: String,
    val name: String,
    val category: String,
    val lightDemand: AquariumPlantLightDemand
)
