package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Co2CatalogTest {

    @Test
    fun `catalog contains the researched co2 hardware set with unique stable ids`() {
        val definitions = Co2Catalog.definitions

        assertEquals(135, definitions.size)
        assertEquals(definitions.size, definitions.map(AquariumMaterialDefinition::id).toSet().size)
        assertTrue(definitions.all { definition -> definition.categoryKey == MaterialCategoryKey.CO2 })
        assertEquals("co2_chihiros_regulator_pro_limited_edition", definitions.first().id)
        assertEquals("co2_jbl_proflora_m2003_2000gr_tup_seti", definitions.last().id)
        assertTrue(definitions.none { definition -> Regex("^co2_\\d{4}$").matches(definition.id) })
    }

    @Test
    fun `every co2 item keeps category and hardware search aliases`() {
        Co2Catalog.definitions.forEach { definition ->
            assertTrue(definition.keywordRes.contains(R.string.catalog_keyword_co2))
            assertTrue(definition.keywordRes.contains(R.string.catalog_keyword_carbon_dioxide))
            assertTrue(definition.keywordRes.size >= 6)
        }
    }

    @Test
    fun `co2 aliases cover regulator system and commercial feature searches`() {
        val keywordResources = Co2Catalog.definitions.flatMap { definition -> definition.keywordRes }.toSet()

        assertTrue(keywordResources.contains(R.string.catalog_keyword_co2_regulator))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_co2_system))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_pressure_reducer))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_solenoid_valve))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_needle_valve))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_dual_stage))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_reactor))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_diffuser))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_bio_co2))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_cylinder))
        assertTrue(keywordResources.contains(R.string.catalog_keyword_bottle))
    }

    @Test
    fun `legacy placeholder catalog ids are not retained`() {
        val ids = Co2Catalog.definitions.map(AquariumMaterialDefinition::id).toSet()
        val removedIds = setOf(
            "co2_chihiros_co2_regulator",
            "co2_chihiros_co2_generator",
            "co2_ista_professional_regulator",
            "co2_ista_aluminium_cylinder",
            "co2_jbl_proflora_u504",
            "co2_dennerle_quantum",
            "co2_aquario_neo_diffuser",
            "co2_do_aqua_music_glass"
        )

        removedIds.forEach { removedId ->
            assertFalse(ids.contains(removedId))
        }
    }
}
