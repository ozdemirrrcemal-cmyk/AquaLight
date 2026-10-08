package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AquariumCatalogTest {

    @Test
    fun `catalog contains the researched aquarium hardware set with unique stable ids`() {
        val definitions = AquariumCatalog.definitions

        assertEquals(291, definitions.size)
        assertEquals(definitions.size, definitions.map(AquariumMaterialDefinition::id).toSet().size)
        assertTrue(definitions.all { definition -> definition.categoryKey == MaterialCategoryKey.AQUARIUM })
        assertEquals("aquarium_ada_cube_garden_w15", definitions.first().id)
        assertEquals("aquarium_tropica_spring_scissors", definitions.last().id)
        assertTrue(definitions.none { definition -> Regex("^aquarium_\\d{4}$").matches(definition.id) })
    }

    @Test
    fun `every aquarium item keeps category and hardware search aliases`() {
        AquariumCatalog.definitions.forEach { definition ->
            assertTrue(definition.keywordRes.contains(R.string.catalog_keyword_aquarium))
            assertTrue(definition.keywordRes.size >= 3)
        }
    }

    @Test
    fun `hardware aliases cover the new aquarium catalog families`() {
        val keywordResources = AquariumCatalog.definitions.flatMap { definition -> definition.keywordRes }.toSet()

        assertTrue(keywordResources.contains(R.string.catalog_keyword_mat))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_thermometer))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_lily_pipe))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_scissors))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_tweezers))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_cleaning))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_tds))
    }
}
