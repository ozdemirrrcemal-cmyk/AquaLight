package com.aqua.aqualight.ui.tabs.aquarium.catalog.plant

import android.content.Context
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantCatalogRecord
import com.aqua.aqualight.application.aquarium.AquariumPlantLightCatalog

/** Spatial sections contain references to stable records; a plant can appear in several sections. */
object PlantCatalog {
    private val sections = listOf(
        Section("FOREGROUND", R.string.catalog_plant_category_foreground_title),
        Section("MIDGROUND", R.string.catalog_plant_category_middle_ground_title),
        Section("CARPET", R.string.catalog_plant_category_ground_cover_title),
        Section("BACKGROUND", R.string.catalog_plant_category_background_title),
        Section("FLOATING", R.string.catalog_plant_category_floating_plants_title)
    )

    fun resolve(context: Context): List<AquariumPlant> {
        val records = AquariumPlantCatalog.records(context)
        require(records.all { record -> sections.any { it.placement in record.placement } })
        return sections.flatMap { section ->
            records.asSequence()
                .filter { section.placement in it.placement }
                .sortedBy { it.displayName.lowercase() }
                .map { it.toPlant(context.getString(section.title)) }
                .toList()
        }
    }

    private fun AquariumPlantCatalogRecord.toPlant(sectionTitle: String) = AquariumPlant(
        catalogId = id,
        name = displayName,
        category = sectionTitle,
        lightDemand = AquariumPlantLightCatalog.requireRecord(id).lightDemand
    )

    private data class Section(val placement: String, @StringRes val title: Int)
}
