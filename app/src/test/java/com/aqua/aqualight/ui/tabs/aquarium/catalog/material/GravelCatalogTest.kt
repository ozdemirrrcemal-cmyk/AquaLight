package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumGravelProductIds
import com.aqua.aqualight.application.aquarium.AquariumSubstrateMetadataCatalog
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GravelCatalogTest {

    @Test
    fun definitionsContainTheCommercialCatalogWithSemanticStableIds() {
        val definitions = GravelCatalog.definitions
        val ids = definitions.map(AquariumMaterialDefinition::id)

        assertEquals(EXPECTED_PRODUCT_COUNT, definitions.size)
        assertEquals(definitions.size, ids.toSet().size)
        assertEquals(AquariumGravelProductIds.ALL, ids)
        assertEquals("gravel_ada_aqua_gravel_s_2_kg", ids.first())
        assertEquals("gravel_prodac_polycrome_2_3_mm_25_kg", ids.last())
        assertTrue(ids.none { productId -> Regex("^gravel_\\d{4}$").matches(productId) })
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
        val definitionsById = GravelCatalog.definitions.associateBy(AquariumMaterialDefinition::id)

        assertKeyword(
            definitionsById,
            "gravel_ada_aqua_gravel_s_2_kg",
            R.string.catalog_keyword_aqua_gravel
        )
        assertKeyword(
            definitionsById,
            "gravel_dennerle_nano_shrimp_gravel_sulawesi_black_0_7_1_2_mm_2_kg",
            R.string.catalog_keyword_black
        )
        assertKeyword(
            definitionsById,
            "gravel_dennerle_nano_shrimp_gravel_sulawesi_black_0_7_1_2_mm_2_kg",
            R.string.catalog_keyword_nano
        )
        assertKeyword(
            definitionsById,
            "gravel_dennerle_natural_gravel_bairaman_0_1_0_6_mm_500_g",
            R.string.catalog_keyword_natural
        )
        assertKeyword(
            definitionsById,
            "gravel_jbl_sansibar_dark_0_2_0_6_mm_5_kg",
            R.string.catalog_keyword_dark
        )
        assertKeyword(
            definitionsById,
            "gravel_jbl_sansibar_dark_0_2_0_6_mm_5_kg",
            R.string.catalog_keyword_black
        )
        assertKeyword(
            definitionsById,
            "gravel_jbl_sansibar_white_0_2_0_6_mm_5_kg",
            R.string.catalog_keyword_white
        )
        assertKeyword(
            definitionsById,
            "gravel_aquael_basalt_gravel_2_4_mm_2_kg",
            R.string.catalog_keyword_basalt
        )
        assertKeyword(
            definitionsById,
            "gravel_aquael_basalt_gravel_2_4_mm_2_kg",
            R.string.catalog_keyword_black
        )
        assertKeyword(
            definitionsById,
            "gravel_dennerle_natural_gravel_rio_branco_0_1_2_mm_500_g",
            R.string.catalog_keyword_river
        )
    }

    @Test
    fun semanticStableIdsResolveThroughTheQuickSetupSubstrateClassifier() {
        GravelCatalog.definitions.forEach { definition ->
            assertEquals(
                definition.substrateMetadata?.semantic,
                AquariumSubstrateMetadataCatalog.resolveSemantic(
                    productId = definition.id,
                    categoryKey = MaterialCategoryKey.GRAVEL
                )
            )
        }
        assertTrue(
            GravelCatalog.definitions.all { definition ->
                AquariumSubstrateMetadataCatalog.resolveSemantic(
                    productId = definition.id,
                    categoryKey = MaterialCategoryKey.GRAVEL
                ) == AquariumSubstrateSemantic.INERT
            }
        )
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
    }
}
