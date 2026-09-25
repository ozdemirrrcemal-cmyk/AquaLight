package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedBackgroundCatalogTest {

    @Test
    fun `catalog contains the researched led background set with unique stable ids`() {
        val definitions = LedBackgroundCatalog.definitions

        assertEquals(50, definitions.size)
        assertEquals(definitions.size, definitions.map(AquariumMaterialDefinition::id).toSet().size)
        assertEquals(definitions.size, definitions.map(AquariumMaterialDefinition::nameRes).toSet().size)
        assertTrue(
            definitions.all { definition ->
                definition.categoryKey == MaterialCategoryKey.LED_BACKGROUND
            }
        )
        assertEquals("led_background_ada_light_screen_300", definitions.first().id)
        assertEquals("led_background_uns_atmos_backlight", definitions.last().id)
        assertTrue(definitions.none { definition -> Regex("^led_background_\\d{4}$").matches(definition.id) })
    }

    @Test
    fun `every led background item keeps category search aliases`() {
        LedBackgroundCatalog.definitions.forEach { definition ->
            assertTrue(definition.keywordRes.contains(R.string.catalog_keyword_led))
            assertTrue(definition.keywordRes.contains(R.string.catalog_keyword_background))
            assertTrue(definition.keywordRes.contains(R.string.catalog_keyword_led_background))
            assertTrue(definition.keywordRes.contains(R.string.catalog_keyword_aquarium_background))
            assertTrue(definition.keywordRes.size >= 8)
        }
    }

    @Test
    fun `aliases cover screens backlights films brands and ascii dimensions`() {
        val keywordResources =
            LedBackgroundCatalog.definitions.flatMap { definition -> definition.keywordRes }.toSet()

        assertTrue(keywordResources.contains(R.string.catalog_keyword_light_screen))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_backlight))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_background_light))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_gradation))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_gradient))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_background_film))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_illuminated_background))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_rgb))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_aqua_design_amano))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_ultum_nature_systems))
        assertTrue(keywordResources.contains(R.string.catalog_led_background_alias_120x45_cm))
    }

    @Test
    fun `legacy placeholder led background ids are not retained`() {
        val ids = LedBackgroundCatalog.definitions.map(AquariumMaterialDefinition::id).toSet()
        val removedIds = setOf(
            "led_background_chihiros_vivid_background",
            "led_background_chihiros_shades",
            "led_background_twinstar_light_screen",
            "led_background_week_aqua_led_screen"
        )

        removedIds.forEach { removedId ->
            assertFalse(ids.contains(removedId))
        }
    }
}
