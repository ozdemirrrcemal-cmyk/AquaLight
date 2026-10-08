package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingCatalogTest {

    @Test
    fun `catalog contains the verified dosing hardware set with unique stable ids`() {
        val definitions = DosingCatalog.definitions

        assertEquals(109, definitions.size)
        assertEquals(definitions.size, definitions.map(AquariumMaterialDefinition::id).toSet().size)
        assertEquals(definitions.size, definitions.map(AquariumMaterialDefinition::nameRes).toSet().size)
        assertEquals(35, definitions.map(AquariumMaterialDefinition::brandRes).toSet().size)
        assertTrue(definitions.all { definition -> definition.categoryKey == MaterialCategoryKey.DOSING })
        assertEquals("dosing_aqua_medic_reefdoser_evo_1", definitions.first().id)
        assertEquals("dosing_tmc_reef_reef_accudose", definitions.last().id)
        assertTrue(definitions.none { definition -> Regex("^dosing_\\d{4}$").matches(definition.id) })
    }

    @Test
    fun `every dosing item keeps localized and locale independent search aliases`() {
        DosingCatalog.definitions.forEach { definition ->
            assertTrue(definition.keywordRes.contains(R.string.catalog_keyword_dosing))
            assertTrue(definition.keywordRes.contains(R.string.catalog_dosing_keyword_dosing_pump))
            assertTrue(definition.keywordRes.contains(R.string.catalog_dosing_keyword_doser))
            assertTrue(definition.keywordRes.contains(R.string.catalog_keyword_pump))
            assertTrue(definition.keywordRes.contains(R.string.catalog_dosing_keyword_aquarium_doser))
            assertTrue(definition.keywordRes.contains(R.string.catalog_dosing_keyword_dosing_en))
            assertTrue(definition.keywordRes.contains(R.string.catalog_dosing_keyword_pump_en))
            assertTrue(definition.keywordRes.contains(R.string.catalog_dosing_keyword_dosing_pump_en))
            assertTrue(definition.keywordRes.contains(R.string.catalog_dosing_keyword_doser_en))
            assertTrue(definition.keywordRes.size >= 10)
        }
    }

    @Test
    fun `aliases cover dosing hardware connectivity and commercial topology searches`() {
        val keywordResources = DosingCatalog.definitions
            .flatMap { definition -> definition.keywordRes }
            .toSet()

        assertTrue(keywordResources.contains(R.string.catalog_dosing_keyword_dosing_system))
        assertTrue(keywordResources.contains(R.string.catalog_dosing_keyword_extension))
        assertTrue(keywordResources.contains(R.string.catalog_dosing_keyword_peristaltic_pump))
        assertTrue(keywordResources.contains(R.string.catalog_dosing_keyword_continuous_duty))
        assertTrue(keywordResources.contains(R.string.catalog_dosing_keyword_stepper))
        assertTrue(keywordResources.contains(R.string.catalog_dosing_keyword_wifi))
        assertTrue(keywordResources.contains(R.string.catalog_dosing_keyword_bluetooth))
        assertTrue(keywordResources.contains(R.string.catalog_dosing_keyword_wireless))
        assertTrue(keywordResources.contains(R.string.catalog_dosing_keyword_multi_channel))
        assertTrue(keywordResources.contains(R.string.catalog_dosing_keyword_drive_port))
    }

    @Test
    fun `legacy placeholder dosing ids are not retained`() {
        val ids = DosingCatalog.definitions.map(AquariumMaterialDefinition::id).toSet()
        val removedIds = setOf(
            "dosing_chihiros_dosing_pump",
            "dosing_chihiros_dosing_system",
            "dosing_jebao_dp_4",
            "dosing_kamoer_x1",
            "dosing_kamoer_f4"
        )

        removedIds.forEach { removedId ->
            assertFalse(ids.contains(removedId))
        }
    }
}
