package com.aqua.aqualight.ui.tabs.aquarium.catalog.plant

import android.content.Context
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantCatalogRecord
import com.aqua.aqualight.application.aquarium.AquariumPlantLightCatalog

/** The aquarium picker uses the same stable-record catalog pattern as material pickers. */
object PlantCatalog {
    fun resolve(context: Context): List<AquariumPlant> = AquariumPlantCatalog.records(context).map { record ->
        AquariumPlant(
            catalogId = record.id,
            name = record.displayName,
            category = context.getString(categoryFor(record)),
            lightDemand = AquariumPlantLightCatalog.requireRecord(record.id).lightDemand
        )
    }

    @StringRes
    private fun categoryFor(record: AquariumPlantCatalogRecord): Int {
        val placement = record.placement
        return when {
            record.growthForm == "MOSS" -> R.string.catalog_plant_category_mosses_title
            "FLOATING" in placement || ("SURFACE" in placement && "BACKGROUND" !in placement) ->
                R.string.catalog_plant_category_floating_plants_title
            "EPIPHYTE" in placement -> R.string.catalog_plant_category_epiphytes_title
            "CARPET" in placement -> R.string.catalog_plant_category_ground_cover_title
            "FOREGROUND" in placement -> R.string.catalog_plant_category_foreground_title
            "MIDGROUND" in placement -> R.string.catalog_plant_category_middle_ground_title
            "BACKGROUND" in placement -> R.string.catalog_plant_category_background_title
            else -> error("Missing plant category for ${record.id}")
        }
    }
}
