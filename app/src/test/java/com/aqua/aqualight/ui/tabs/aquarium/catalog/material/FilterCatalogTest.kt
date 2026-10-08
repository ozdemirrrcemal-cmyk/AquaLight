package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterCatalogTest {

    @Test
    fun `catalog contains the researched filter hardware set with unique stable ids`() {
        val definitions = FilterCatalog.definitions

        assertEquals(749, definitions.size)
        assertEquals(definitions.size, definitions.map(AquariumMaterialDefinition::id).toSet().size)
        assertEquals(definitions.size, definitions.map(AquariumMaterialDefinition::nameRes).toSet().size)
        assertTrue(definitions.all { definition -> definition.categoryKey == MaterialCategoryKey.FILTER })
        assertEquals("filter_eheim_classic_150", definitions.first().id)
        assertEquals("filter_waterbear_wb_2880f", definitions.last().id)
        assertTrue(definitions.none { definition -> Regex("^filter_\\d{4}$").matches(definition.id) })
    }

    @Test
    fun `every filter item keeps base search aliases`() {
        FilterCatalog.definitions.forEach { definition ->
            assertTrue(definition.keywordRes.contains(R.string.catalog_keyword_filter))
            assertTrue(definition.keywordRes.contains(R.string.catalog_filter_keyword_aquarium_filter))
            assertTrue(definition.keywordRes.size >= 2)
        }
    }

    @Test
    fun `filter aliases cover the researched hardware classes and commercial searches`() {
        val keywordResources = FilterCatalog.definitions
            .flatMap(AquariumMaterialDefinition::keywordRes)
            .toSet()

        assertTrue(keywordResources.contains(R.string.catalog_keyword_external))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_canister))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_hang_on))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_hob))
        assertTrue(keywordResources.contains(R.string.catalog_filter_keyword_external_filter))
        assertTrue(keywordResources.contains(R.string.catalog_filter_keyword_canister_filter))
        assertTrue(keywordResources.contains(R.string.catalog_filter_keyword_hang_on_filter))
        assertTrue(keywordResources.contains(R.string.catalog_filter_keyword_internal_filter))
        assertTrue(keywordResources.contains(R.string.catalog_filter_keyword_waterfall_filter))
        assertTrue(keywordResources.contains(R.string.catalog_filter_keyword_power_filter))
        assertTrue(keywordResources.contains(R.string.catalog_filter_keyword_sponge_filter))
        assertTrue(keywordResources.contains(R.string.catalog_filter_keyword_air_driven_filter))
        assertTrue(keywordResources.contains(R.string.catalog_filter_keyword_corner_filter))
        assertTrue(keywordResources.contains(R.string.catalog_filter_keyword_prefilter))
        assertTrue(keywordResources.contains(R.string.catalog_filter_keyword_top_filter))
        assertTrue(keywordResources.contains(R.string.catalog_filter_keyword_overhead_filter))
        assertTrue(keywordResources.contains(R.string.catalog_filter_keyword_pipe_filter))
        assertTrue(keywordResources.contains(R.string.catalog_filter_keyword_mini_external_filter))
        assertTrue(keywordResources.contains(R.string.catalog_filter_keyword_biological_filter))
        assertTrue(keywordResources.contains(R.string.catalog_filter_keyword_heated_filter))
        assertTrue(keywordResources.contains(R.string.catalog_filter_keyword_uv_filter))
    }

    @Test
    fun `generic source category does not invent an unsupported filter type`() {
        val definition = FilterCatalog.definitions.single { it.id == "filter_hailea_sl_306" }

        assertFalse(definition.keywordRes.contains(R.string.catalog_keyword_external))
        assertFalse(definition.keywordRes.contains(R.string.catalog_keyword_canister))
        assertFalse(definition.keywordRes.contains(R.string.catalog_keyword_hang_on))
        assertFalse(definition.keywordRes.contains(R.string.catalog_keyword_hob))
        assertFalse(definition.keywordRes.contains(R.string.catalog_filter_keyword_internal_filter))
    }
}
