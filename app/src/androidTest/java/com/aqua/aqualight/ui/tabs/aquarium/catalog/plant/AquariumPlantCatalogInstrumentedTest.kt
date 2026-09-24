package com.aqua.aqualight.ui.tabs.aquarium.catalog.plant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    fun packagedCatalogContainsOnlyPlantData() {
        val packagedJson = context.assets.open("aqualight_plant_catalog.json")
            .bufferedReader().use { it.readText() }
        assertTrue(!packagedJson.contains("https://"))
        assertTrue(!packagedJson.contains("sourceUrl"))
        assertTrue(!packagedJson.contains("sourceOrganization"))
        assertTrue(!packagedJson.contains("selectedRecordEvidence"))
        assertEquals(248, AquariumPlantCatalog.parse(packagedJson).size)
    }
}
