package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GravelCatalogTest {

    @Test
    fun definitionsContainExactlyTheReplacementCatalog() {
        val definitions = GravelCatalog.definitions

        assertEquals(EXPECTED_PRODUCT_COUNT, definitions.size)
        assertEquals(definitions.size, definitions.map { definition -> definition.id }.distinct().size)
        assertEquals(EXPECTED_PRODUCT_IDS, definitions.map { definition -> definition.id }.toSet())
        assertTrue(definitions.all { definition -> definition.brandRes != 0 })
        assertTrue(
            definitions.all { definition ->
                definition.categoryKey == MaterialCategoryKey.GRAVEL
            }
        )
        assertTrue(definitions.all { definition -> definition.substrateMetadata != null })
    }

    private companion object {
        const val EXPECTED_PRODUCT_COUNT = 181

        val EXPECTED_PRODUCT_IDS = (1..EXPECTED_PRODUCT_COUNT).mapTo(mutableSetOf()) { index ->
            "gravel_${index.toString().padStart(4, '0')}"
        }
    }
}
