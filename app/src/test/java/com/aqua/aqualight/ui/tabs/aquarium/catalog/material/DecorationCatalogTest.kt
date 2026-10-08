package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecorationCatalogTest {

    @Test
    fun `catalog contains the verified decoration set with unique stable ids`() {
        val definitions = DecorationCatalog.definitions

        assertEquals(233, definitions.size)
        assertEquals(definitions.size, definitions.map(AquariumMaterialDefinition::id).toSet().size)
        assertEquals(10, definitions.map(AquariumMaterialDefinition::brandRes).toSet().size)
        assertTrue(definitions.all { definition -> definition.brandRes != 0 })
        assertTrue(definitions.all { definition -> definition.categoryKey == MaterialCategoryKey.DECORATION })
        assertEquals("decoration_wio_adder_boulder", definitions.first().id)
        assertEquals("decoration_ista_water_plant_stylish_rock_ceramic", definitions.last().id)
        assertTrue(definitions.none { definition -> Regex("^decoration_\\d{4}$").matches(definition.id) })
    }

    @Test
    fun `normalization aliases remain searchable through keywords`() {
        val definitionsById = DecorationCatalog.definitions.associateBy(AquariumMaterialDefinition::id)

        assertTrue(
            R.string.catalog_decoration_alias_ohko in
                requireNotNull(definitionsById["decoration_wio_dragon_stone"]).keywordRes
        )
        assertTrue(
            R.string.catalog_decoration_alias_dragon_stone in
                requireNotNull(definitionsById["decoration_ada_ohko_stone"]).keywordRes
        )
        assertTrue(
            R.string.catalog_decoration_alias_redmoor in
                requireNotNull(definitionsById["decoration_greenworks_red_moor_wood"]).keywordRes
        )
        assertTrue(
            R.string.catalog_decoration_alias_mangrow in
                requireNotNull(definitionsById["decoration_aquael_mangro_root"]).keywordRes
        )
    }

    @Test
    fun `legacy placeholder decoration ids are not retained`() {
        val ids = DecorationCatalog.definitions.map(AquariumMaterialDefinition::id).toSet()

        assertFalse(ids.contains("decoration_seiryu_stone"))
        assertFalse(ids.contains("decoration_dragon_stone"))
    }
}
