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

        assertEquals(AquariumPlantLightCatalog.EXPECTED_RECORD_COUNT, uniquePlants.size)
        assertEquals(
            AquariumPlantLightCatalog.catalogIds,
            uniquePlants.map(AquariumPlant::catalogId).toSet()
        )
        assertTrue(uniquePlants.all { plant ->
            plant.lightDemand == AquariumPlantLightCatalog.resolve(plant.catalogId)
        })
    }
}
