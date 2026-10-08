package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolerCatalogTest {

    @Test
    fun `catalog contains the verified cooler hardware set with unique stable ids`() {
        val definitions = CoolerCatalog.definitions

        assertEquals(185, definitions.size)
        assertEquals(definitions.size, definitions.map(AquariumMaterialDefinition::id).toSet().size)
        assertEquals(44, definitions.map(AquariumMaterialDefinition::brandRes).toSet().size)
        assertTrue(definitions.all { definition -> definition.categoryKey == MaterialCategoryKey.COOLER })
        assertEquals("cooler_aqua_medic_arctic_breeze_2_pac", definitions.first().id)
        assertEquals("cooler_zoo_med_aqua_cool_aquarium_cooling_fan", definitions.last().id)
        assertTrue(definitions.none { definition -> Regex("^cooler_\\d{4}$").matches(definition.id) })
    }

    @Test
    fun `every cooler item keeps category and hardware search aliases`() {
        CoolerCatalog.definitions.forEach { definition ->
            assertTrue(definition.keywordRes.contains(R.string.catalog_keyword_cooler))
            assertTrue(definition.keywordRes.contains(R.string.catalog_cooler_keyword_aquarium_cooler))
            assertTrue(definition.keywordRes.contains(R.string.catalog_keyword_temperature))
            assertTrue(definition.keywordRes.size >= 5)
        }
    }

    @Test
    fun `cooler aliases cover fan chiller and specialized searches`() {
        val keywordResources = CoolerCatalog.definitions.flatMap { definition -> definition.keywordRes }.toSet()

        assertTrue(keywordResources.contains(R.string.catalog_cooler_keyword_cooling_fan))
        assertTrue(keywordResources.contains(R.string.catalog_cooler_keyword_chiller))
        assertTrue(keywordResources.contains(R.string.catalog_cooler_keyword_water_chiller))
        assertTrue(keywordResources.contains(R.string.catalog_cooler_keyword_thermostat))
        assertTrue(keywordResources.contains(R.string.catalog_cooler_keyword_smart))
        assertTrue(keywordResources.contains(R.string.catalog_cooler_keyword_thermoelectric))
        assertTrue(keywordResources.contains(R.string.catalog_cooler_keyword_inline))
        assertTrue(keywordResources.contains(R.string.catalog_cooler_keyword_drop_in))
        assertTrue(keywordResources.contains(R.string.catalog_cooler_keyword_heating))
    }

    @Test
    fun `legacy placeholder cooler ids are not retained`() {
        val ids = CoolerCatalog.definitions.map(AquariumMaterialDefinition::id).toSet()
        val removedIds = setOf(
            "cooler_aquael_fan_mini",
            "cooler_chihiros_cooling_fan",
            "cooler_ista_cooling_fan",
            "cooler_jbl_cooler_100",
            "cooler_jbl_cooler_200"
        )

        removedIds.forEach { removedId ->
            assertFalse(ids.contains(removedId))
        }
    }
}
