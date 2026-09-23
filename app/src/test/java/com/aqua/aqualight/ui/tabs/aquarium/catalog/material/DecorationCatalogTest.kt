package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecorationCatalogTest {

    @Test
    fun definitionsContainExactlyTheReplacementCatalog() {
        val definitions = DecorationCatalog.definitions

        assertEquals(EXPECTED_PRODUCT_COUNT, definitions.size)
        assertEquals(definitions.size, definitions.map { it.id }.distinct().size)
        assertTrue(definitions.all { it.brandRes != 0 })
        assertTrue(definitions.all { it.categoryKey == MaterialCategoryKey.DECORATION })
        assertFalse(definitions.any { it.id == "decoration_seiryu_stone" })
        assertFalse(definitions.any { it.id == "decoration_dragon_stone" })
    }

    @Test
    fun normalizationAliasesRemainSearchableThroughKeywords() {
        val definitionsById = DecorationCatalog.definitions.associateBy { it.id }

        assertTrue(
            R.string.catalog_decoration_alias_ohko in
                requireNotNull(definitionsById["decoration_0004"]).keywordRes
        )
        assertTrue(
            R.string.catalog_decoration_alias_dragon_stone in
                requireNotNull(definitionsById["decoration_0060"]).keywordRes
        )
        assertTrue(
            R.string.catalog_decoration_alias_redmoor in
                requireNotNull(definitionsById["decoration_0188"]).keywordRes
        )
        assertTrue(
            R.string.catalog_decoration_alias_mangrow in
                requireNotNull(definitionsById["decoration_0222"]).keywordRes
        )
    }

    private companion object {
        const val EXPECTED_PRODUCT_COUNT = 233
    }
}
