package com.aqua.aqualight.ui.tabs.aquarium.catalog.plant

import android.content.Context
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantCatalogRecord
import com.aqua.aqualight.application.aquarium.AquariumPlantLightCatalog

/** Every category refers to the same stable identity, including overlapping types and positions. */
object PlantCatalog {
    private val sections = listOf(
        Section(R.string.catalog_plant_category_foreground_title) { "FOREGROUND" in it.placement },
        Section(R.string.catalog_plant_category_middle_ground_title) { "MIDGROUND" in it.placement },
        Section(R.string.catalog_plant_category_background_title) { "BACKGROUND" in it.placement },
        Section(R.string.catalog_plant_category_ground_cover_title) { "CARPET" in it.placement },
        Section(R.string.catalog_plant_category_mosses_title) { record ->
            record.growthForm == "MOSS" || record.searchNames.any { "moss" in it.lowercase() }
        },
        Section(R.string.catalog_plant_category_floating_plants_title) { "FLOATING" in it.placement },
        Section(R.string.catalog_plant_category_epiphytes_title) { "EPIPHYTE" in it.placement },
        Section(R.string.catalog_plant_category_rare_title) { "RARE_AQUARIUM_TRADE" in it.catalogFlags }
    )

    fun resolve(context: Context): List<AquariumPlant> {
        val records = AquariumPlantCatalog.records(context)
        require(records.all { record -> sections.any { it.includes(record) } })
        return sections.flatMap { section ->
            records.asSequence()
                .filter(section.includes)
                .sortedBy { it.displayName.lowercase() }
                .map { it.toPlant(context.getString(section.title)) }
                .toList()
        }
    }

    private fun AquariumPlantCatalogRecord.toPlant(sectionTitle: String) = AquariumPlant(
        catalogId = id,
        name = displayName,
        category = sectionTitle,
        searchNames = searchNames,
        lightDemand = AquariumPlantLightCatalog.requireRecord(id).lightDemand
    )

    private data class Section(
        @StringRes val title: Int,
        val includes: (AquariumPlantCatalogRecord) -> Boolean
    )
}
