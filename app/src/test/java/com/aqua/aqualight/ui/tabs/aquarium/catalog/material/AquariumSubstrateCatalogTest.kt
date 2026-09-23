package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumSubstrateEvidenceStatus
import com.aqua.aqualight.application.aquarium.AquariumSubstrateProductIds
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AquariumSubstrateCatalogTest {

    @Test
    fun replacementCatalogKeepsEveryProductIdentityAndExplicitMetadata() {
        val products = SubstrateCatalog.definitions + GravelCatalog.definitions

        assertEquals(EXPECTED_PRODUCT_IDS, products.map { product -> product.id }.toSet())
        assertEquals(MaterialCatalog.EXPECTED_SUBSTRATE_PRODUCT_COUNT, products.size)
        assertTrue(products.all { product -> product.substrateMetadata != null })
    }

    @Test
    fun replacementSubstrateCatalogKeepsReviewedSemanticDistribution() {
        val metadata = SubstrateCatalog.definitions
            .mapNotNull(AquariumMaterialDefinition::substrateMetadata)

        assertEquals(EXPECTED_SUBSTRATE_COUNT, metadata.size)
        assertEquals(
            EXPECTED_ACTIVE_SOIL_COUNT,
            metadata.count { record -> record.semantic == AquariumSubstrateSemantic.ACTIVE_SOIL }
        )
        assertEquals(
            EXPECTED_NUTRIENT_BASE_COUNT,
            metadata.count { record -> record.semantic == AquariumSubstrateSemantic.NUTRIENT_BASE }
        )
        assertEquals(
            EXPECTED_ADDITIVE_COUNT,
            metadata.count { record -> record.semantic == AquariumSubstrateSemantic.ADDITIVE }
        )
        assertEquals(
            EXPECTED_INERT_COUNT,
            metadata.count { record -> record.semantic == AquariumSubstrateSemantic.INERT }
        )
        assertEquals(
            EXPECTED_UNKNOWN_COUNT,
            metadata.count { record -> record.semantic == AquariumSubstrateSemantic.UNKNOWN }
        )
    }

    @Test
    fun substrateKeywordsKeepExistingSearchContract() {
        val firstProduct = SubstrateCatalog.definitions.first()
        val nutrientBase = SubstrateCatalog.definitions.first { product ->
            product.substrateMetadata?.semantic == AquariumSubstrateSemantic.NUTRIENT_BASE
        }
        val additive = SubstrateCatalog.definitions.first { product ->
            product.substrateMetadata?.semantic == AquariumSubstrateSemantic.ADDITIVE
        }

        assertEquals(
            listOf(
                R.string.catalog_keyword_substrate,
                R.string.catalog_keyword_soil,
                R.string.catalog_keyword_aquasoil,
                R.string.catalog_keyword_chihiros,
                R.string.catalog_keyword_plant
            ),
            firstProduct.keywordRes
        )
        assertTrue(nutrientBase.keywordRes.contains(R.string.catalog_keyword_base_layer))
        assertTrue(additive.keywordRes.contains(R.string.catalog_keyword_additive))
        assertTrue(
            SubstrateCatalog.definitions.all { product ->
                product.keywordRes.contains(R.string.catalog_keyword_substrate) &&
                    product.keywordRes.contains(R.string.catalog_keyword_soil) &&
                    product.keywordRes.contains(R.string.catalog_keyword_plant)
            }
        )
    }

    @Test
    fun legacySubstrateIdsStayStableForSavedTankCompatibility() {
        assertEquals(
            "substrate_chihiros_aquasoil_3l",
            AquariumSubstrateProductIds.productId(CHIHIROS_AQUASOIL_3L_INDEX)
        )
        assertEquals(
            "substrate_chihiros_aquasoil_9l",
            AquariumSubstrateProductIds.productId(CHIHIROS_AQUASOIL_9L_INDEX)
        )
        assertEquals(
            "substrate_ada_tourmaline_bc",
            AquariumSubstrateProductIds.productId(ADA_TOURMALINE_BC_INDEX)
        )
        assertEquals(
            "substrate_dennerle_deponitmix_4_8kg",
            AquariumSubstrateProductIds.productId(DENNERLE_DEPONIT_MIX_INDEX)
        )
    }

    @Test
    fun replacementGravelCatalogRemainsVerifiedAsNonActiveSubstrate() {
        val metadata = GravelCatalog.definitions
            .mapNotNull(AquariumMaterialDefinition::substrateMetadata)

        assertEquals(EXPECTED_GRAVEL_COUNT, metadata.size)
        assertTrue(metadata.all { record -> record.semantic == AquariumSubstrateSemantic.INERT })
        assertTrue(
            metadata.all { record ->
                record.evidenceStatus == AquariumSubstrateEvidenceStatus.VERIFIED_PRODUCT
            }
        )
        assertTrue(metadata.all { record -> record.isVerifiedProduct })
        assertTrue(
            metadata.none { record ->
                record.semantic == AquariumSubstrateSemantic.ACTIVE_SOIL
            }
        )
    }

    @Test
    fun everyCatalogProductKeepsInternalProvenanceWithoutSemanticGuessing() {
        val metadata = (SubstrateCatalog.definitions + GravelCatalog.definitions)
            .mapNotNull(AquariumMaterialDefinition::substrateMetadata)
        val verified = metadata.filter { record -> record.isVerifiedProduct }
        val unresolved = metadata.filter { record -> !record.isVerifiedProduct }

        assertEquals(EXPECTED_TOTAL_COUNT, metadata.size)
        assertEquals(EXPECTED_VERIFIED_COUNT, verified.size)
        assertEquals(EXPECTED_UNKNOWN_COUNT, unresolved.size)
        assertTrue(metadata.all { record -> record.sourceOrganization.isNotBlank() })
        assertTrue(metadata.all { record -> record.sourceRecordId.isNotBlank() })
        assertTrue(metadata.all { record -> record.catalogRevision == 1 })
        assertTrue(
            unresolved.all { record ->
                record.semantic == AquariumSubstrateSemantic.UNKNOWN &&
                    record.evidenceStatus == AquariumSubstrateEvidenceStatus.UNVERIFIED_GENERIC
            }
        )
    }

    @Test
    fun customProductsFailClosedWithoutNameInference() {
        val customProductId = "custom_gravel_user_product"

        assertNull(
            MaterialCatalog.substrateMetadata(
                productId = customProductId,
                categoryKey = MaterialCategoryKey.GRAVEL
            )
        )
        assertEquals(
            AquariumSubstrateSemantic.UNKNOWN,
            MaterialCatalog.resolveSubstrateSemantic(
                productId = customProductId,
                categoryKey = MaterialCategoryKey.GRAVEL
            )
        )
    }

    @Test
    fun productCategoryMismatchCannotLeakAReviewedClassification() {
        assertEquals(
            AquariumSubstrateSemantic.UNKNOWN,
            MaterialCatalog.resolveSubstrateSemantic(
                productId = "substrate_chihiros_aquasoil_9l",
                categoryKey = MaterialCategoryKey.GRAVEL
            )
        )
        assertEquals(
            AquariumSubstrateSemantic.NOT_APPLICABLE,
            MaterialCatalog.resolveSubstrateSemantic(
                productId = "substrate_chihiros_aquasoil_9l",
                categoryKey = MaterialCategoryKey.LIGHT
            )
        )
    }

    private companion object {
        const val EXPECTED_SUBSTRATE_COUNT = 138
        const val EXPECTED_GRAVEL_COUNT = 181
        const val EXPECTED_TOTAL_COUNT = EXPECTED_SUBSTRATE_COUNT + EXPECTED_GRAVEL_COUNT
        const val EXPECTED_ACTIVE_SOIL_COUNT = 97
        const val EXPECTED_NUTRIENT_BASE_COUNT = 20
        const val EXPECTED_ADDITIVE_COUNT = 9
        const val EXPECTED_INERT_COUNT = 9
        const val EXPECTED_UNKNOWN_COUNT = 3
        const val EXPECTED_VERIFIED_COUNT = EXPECTED_TOTAL_COUNT - EXPECTED_UNKNOWN_COUNT
        const val CHIHIROS_AQUASOIL_3L_INDEX = 1
        const val CHIHIROS_AQUASOIL_9L_INDEX = 2
        const val ADA_TOURMALINE_BC_INDEX = 3
        const val DENNERLE_DEPONIT_MIX_INDEX = 4

        val EXPECTED_PRODUCT_IDS =
            (1..AquariumSubstrateProductIds.EXPECTED_CATALOG_PRODUCT_COUNT)
                .mapTo(mutableSetOf()) { index ->
                    AquariumSubstrateProductIds.productId(index)
                } + (1..EXPECTED_GRAVEL_COUNT).mapTo(mutableSetOf()) { index ->
                    "gravel_${index.toString().padStart(4, '0')}"
                }
    }
}
