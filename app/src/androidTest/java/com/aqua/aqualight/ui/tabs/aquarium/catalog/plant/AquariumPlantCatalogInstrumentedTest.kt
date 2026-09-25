package com.aqua.aqualight.ui.tabs.aquarium.catalog.plant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantLightCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AquariumPlantCatalogInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everySelectablePlantResolvesToAnExactReviewedLightRecord() {
        val resolvedPlants = PlantCatalog.resolve(context)
        val uniquePlants = resolvedPlants.distinctBy(AquariumPlant::catalogId)

        assertEquals(AquariumPlantCatalog.EXPECTED_RECORD_COUNT, uniquePlants.size)
        assertEquals(
            AquariumPlantLightCatalog.catalogIds,
            uniquePlants.map(AquariumPlant::catalogId).toSet()
        )
        assertTrue(uniquePlants.all { plant ->
            plant.lightDemand == AquariumPlantLightCatalog.resolve(plant.catalogId)
        })
        assertEquals(182, AquariumPlantCatalog.records(context).count { it.care.healthAnalysisReady })
        assertTrue(AquariumPlantCatalog.records(context).all { it.placement.isNotEmpty() })
        assertTrue(AquariumPlantCatalog.records(context).all { record ->
            AquariumPlantLightCatalog.requireRecord(record.id).lightRequirement == record.care.lightRequirement
        })
    }

    @Test
    fun pickerUsesEightOrderedCategoriesAndRetainsSharedPlantIds() {
        val plants = PlantCatalog.resolve(context)
        val sectionNames = listOf(
            R.string.catalog_plant_category_foreground_title,
            R.string.catalog_plant_category_middle_ground_title,
            R.string.catalog_plant_category_background_title,
            R.string.catalog_plant_category_ground_cover_title,
            R.string.catalog_plant_category_mosses_title,
            R.string.catalog_plant_category_floating_plants_title,
            R.string.catalog_plant_category_epiphytes_title,
            R.string.catalog_plant_category_rare_title
        ).map(context::getString)
        assertEquals(sectionNames, plants.map(AquariumPlant::category).distinct())
        sectionNames.forEach { section ->
            val names = plants.filter { it.category == section }.map(AquariumPlant::name)
            assertEquals(names.sortedBy(String::lowercase), names)
        }
        val sharedPlant = plants.filter { it.name == "Anubias barteri var. nana" }
        assertEquals(sectionNames.take(2), sharedPlant.map(AquariumPlant::category))
        assertEquals(1, sharedPlant.map(AquariumPlant::catalogId).distinct().size)
        val javaMoss = plants.filter { it.name == "Ectropothecium barbieri" }
        assertTrue(javaMoss.map(AquariumPlant::category).contains(sectionNames[4]))
        assertTrue(javaMoss.first().searchNames.any { it.contains("Java moss", ignoreCase = true) })
        assertTrue(plants.filter { it.name == "Riccia fluitans" }
            .map(AquariumPlant::category).containsAll(listOf(sectionNames[4], sectionNames[5])))
        assertEquals(18, plants.map(AquariumPlant::name).distinct().count { it.startsWith("Bucephalandra") })
        assertTrue(plants.count { it.category == sectionNames[4] } >= 20)
    }

    @Test
    fun packagedCatalogContainsOnlyPlantData() {
        val packagedJson = context.assets.open("aqualight_plant_catalog.json")
            .bufferedReader().use { it.readText() }
        assertTrue(!packagedJson.contains("https://"))
        assertTrue(!packagedJson.contains("sourceUrl"))
        assertTrue(!packagedJson.contains("sourceOrganization"))
        assertTrue(!packagedJson.contains("selectedRecordEvidence"))
        assertEquals(271, AquariumPlantCatalog.parse(packagedJson).size)
    }
}
