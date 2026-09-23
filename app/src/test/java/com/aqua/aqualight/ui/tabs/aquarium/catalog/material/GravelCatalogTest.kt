package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R
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

    @Test
    fun localizedSearchKeywordResourcesRemainWiredToRelevantProducts() {
        val definitionsById = GravelCatalog.definitions.associateBy { definition -> definition.id }

        assertKeyword(definitionsById, "gravel_0001", R.string.catalog_keyword_aqua_gravel)
        assertKeyword(definitionsById, "gravel_0031", R.string.catalog_keyword_black)
        assertKeyword(definitionsById, "gravel_0031", R.string.catalog_keyword_nano)
        assertKeyword(definitionsById, "gravel_0003", R.string.catalog_keyword_natural)
        assertKeyword(definitionsById, "gravel_0076", R.string.catalog_keyword_dark)
        assertKeyword(definitionsById, "gravel_0076", R.string.catalog_keyword_black)
        assertKeyword(definitionsById, "gravel_0070", R.string.catalog_keyword_white)
        assertKeyword(definitionsById, "gravel_0142", R.string.catalog_keyword_basalt)
        assertKeyword(definitionsById, "gravel_0142", R.string.catalog_keyword_black)
        assertKeyword(definitionsById, "gravel_0020", R.string.catalog_keyword_river)
    }

    private fun assertKeyword(
        definitionsById: Map<String, AquariumMaterialDefinition>,
        productId: String,
        keywordRes: Int
    ) {
        assertTrue(keywordRes in requireNotNull(definitionsById[productId]).keywordRes)
    }

    private companion object {
        const val EXPECTED_PRODUCT_COUNT = 181

        val EXPECTED_PRODUCT_IDS = (1..EXPECTED_PRODUCT_COUNT).mapTo(mutableSetOf()) { index ->
            "gravel_${index.toString().padStart(4, '0')}"
        }
    }
}
