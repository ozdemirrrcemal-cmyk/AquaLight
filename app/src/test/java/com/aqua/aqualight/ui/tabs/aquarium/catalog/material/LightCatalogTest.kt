package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightCatalogTest {

    @Test
    fun `catalog contains the researched lighting hardware set with unique stable ids`() {
        val definitions = LightCatalog.definitions

        assertEquals(964, definitions.size)
        assertEquals(definitions.size, definitions.map(AquariumMaterialDefinition::id).toSet().size)
        assertEquals(definitions.size, definitions.map(AquariumMaterialDefinition::nameRes).toSet().size)
        assertEquals(52, definitions.map(AquariumMaterialDefinition::brandRes).toSet().size)
        assertTrue(definitions.all { definition -> definition.categoryKey == MaterialCategoryKey.LIGHT })
        assertEquals("light_ada_na_light_300", definitions.first().id)
        assertEquals("light_zetlight_lancia2_series_marine", definitions.last().id)
        assertTrue(definitions.none { definition -> Regex("^light_\\d{4}$").matches(definition.id) })
    }

    @Test
    fun `every lighting item keeps common search aliases`() {
        LightCatalog.definitions.forEach { definition ->
            assertTrue(definition.keywordRes.contains(R.string.catalog_keyword_light))
            assertTrue(definition.keywordRes.contains(R.string.catalog_light_keyword_lighting))
            assertTrue(definition.keywordRes.contains(R.string.catalog_light_keyword_aquarium_light))
            assertTrue(definition.keywordRes.size >= 5)
        }
    }

    @Test
    fun `aliases cover lighting mounting shade optical use cases and normalized models`() {
        val keywordResources = LightCatalog.definitions
            .flatMap { definition -> definition.keywordRes }
            .toSet()

        assertTrue(keywordResources.contains(R.string.catalog_keyword_led))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_rgb))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_wrgb))
        assertTrue(keywordResources.contains(R.string.catalog_light_keyword_rgbw))
        assertTrue(keywordResources.contains(R.string.catalog_light_keyword_t5))
        assertTrue(keywordResources.contains(R.string.catalog_light_keyword_t8))
        assertTrue(keywordResources.contains(R.string.catalog_light_keyword_freshwater))
        assertTrue(keywordResources.contains(R.string.catalog_light_keyword_marine))
        assertTrue(keywordResources.contains(R.string.catalog_light_keyword_reef))
        assertTrue(keywordResources.contains(R.string.catalog_light_keyword_planted))
        assertTrue(keywordResources.contains(R.string.catalog_light_keyword_full_spectrum))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_shade))
        assertTrue(keywordResources.contains(R.string.catalog_light_keyword_diffuser))
        assertTrue(keywordResources.contains(R.string.catalog_light_keyword_hanging_kit))
        assertTrue(keywordResources.contains(R.string.catalog_light_keyword_mounting))
        assertTrue(keywordResources.contains(R.string.catalog_light_keyword_optical_accessory))
        assertTrue(keywordResources.contains(R.string.catalog_light_search_alias_0006))
        assertTrue(keywordResources.contains(R.string.catalog_light_search_alias_0370))
    }

    @Test
    fun `turkish manufacturers are retained as distinct catalog brands`() {
        val brandResources = LightCatalog.definitions
            .map(AquariumMaterialDefinition::brandRes)
            .toSet()

        assertTrue(brandResources.contains(R.string.catalog_light_brand_aqualed))
        assertTrue(brandResources.contains(R.string.catalog_light_brand_aquareef))
        assertTrue(brandResources.contains(R.string.catalog_light_brand_armaturk))
        assertTrue(brandResources.contains(R.string.catalog_light_brand_creaqua))
        assertTrue(brandResources.contains(R.string.catalog_light_brand_orionled))
        assertTrue(
            brandResources.contains(
                R.string.catalog_light_brand_shark_akvaryum_aydinlatma_sistemleri
            )
        )
    }

    @Test
    fun `legacy placeholder light ids are not retained`() {
        val ids = LightCatalog.definitions.map(AquariumMaterialDefinition::id).toSet()
        val removedIds = setOf(
            "light_chihiros_wrgb_ii",
            "light_chihiros_wrgb_ii_slim",
            "light_chihiros_magnetic_lamp_led",
            "light_twinstar_s_series"
        )

        removedIds.forEach { removedId ->
            assertFalse(ids.contains(removedId))
        }
    }
}
