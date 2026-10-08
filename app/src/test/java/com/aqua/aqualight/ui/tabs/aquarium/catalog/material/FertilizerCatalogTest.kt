package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FertilizerCatalogTest {

    @Test
    fun `catalog contains the verified fertilizer set with unique stable ids`() {
        val definitions = FertilizerCatalog.definitions

        assertEquals(704, definitions.size)
        assertEquals(definitions.size, definitions.map(AquariumMaterialDefinition::id).toSet().size)
        assertEquals(43, definitions.map(AquariumMaterialDefinition::brandRes).toSet().size)
        assertTrue(definitions.all { definition -> definition.categoryKey == MaterialCategoryKey.FERTILIZER })
        assertEquals("fertilizer_2hr_aquarist_apt_1_zero_100_ml", definitions.first().id)
        assertEquals("fertilizer_jbl_proflora_plantstart_2_x_8_g", definitions.last().id)
        assertTrue(definitions.none { definition -> Regex("^fertilizer_\\d{4}$").matches(definition.id) })
    }

    @Test
    fun `every fertilizer keeps the shared fertilizer and plant search aliases`() {
        FertilizerCatalog.definitions.forEach { definition ->
            assertTrue(definition.keywordRes.contains(R.string.catalog_keyword_fertilizer))
            assertTrue(definition.keywordRes.contains(R.string.catalog_keyword_plant))
        }
    }

    @Test
    fun `fertilizer aliases cover liquid and carbon searches`() {
        val keywordResources = FertilizerCatalog.definitions
            .flatMap(AquariumMaterialDefinition::keywordRes)
            .toSet()

        assertTrue(keywordResources.contains(R.string.catalog_keyword_liquid))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_carbon))
    }
}
