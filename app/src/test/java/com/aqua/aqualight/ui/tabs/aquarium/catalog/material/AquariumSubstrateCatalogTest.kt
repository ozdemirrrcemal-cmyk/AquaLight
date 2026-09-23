package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.application.aquarium.AquariumSubstrateEvidenceStatus
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AquariumSubstrateCatalogTest {

    @Test
    fun everyExistingSubstrateProductKeepsItsStableIdentityAndExplicitMetadata() {
        val products = SubstrateCatalog.definitions + GravelCatalog.definitions

        assertEquals(EXPECTED_PRODUCT_IDS, products.map { product -> product.id }.toSet())
        assertEquals(MaterialCatalog.EXPECTED_SUBSTRATE_PRODUCT_COUNT, products.size)
        assertTrue(products.all { product -> product.substrateMetadata != null })
    }

    @Test
    fun reviewedSubstrateProductsKeepTheirResearchBackedSemantics() {
        val expectedSemantics = mapOf(
            "substrate_chihiros_aquasoil_9l" to AquariumSubstrateSemantic.ACTIVE_SOIL,
            "substrate_chihiros_aquasoil_3l" to AquariumSubstrateSemantic.ACTIVE_SOIL,
            "substrate_ada_tourmaline_bc" to AquariumSubstrateSemantic.ADDITIVE,
            "substrate_dennerle_deponitmix_4_8kg" to
                AquariumSubstrateSemantic.NUTRIENT_BASE
        )

        val actualSemantics = SubstrateCatalog.definitions
            .associate { product -> product.id to product.substrateMetadata?.semantic }

        assertEquals(expectedSemantics, actualSemantics)
    }

    @Test
    fun replacementGravelCatalogIsVerifiedAsNonActiveSubstrate() {
        val metadata = GravelCatalog.definitions
            .mapNotNull(AquariumMaterialDefinition::substrateMetadata)

        assertEquals(181, metadata.size)
        assertTrue(metadata.all { record -> record.semantic == AquariumSubstrateSemantic.INERT })
        assertTrue(
            metadata.all { record ->
                record.evidenceStatus == AquariumSubstrateEvidenceStatus.VERIFIED_PRODUCT
            }
        )
        assertTrue(metadata.all { record -> record.isVerifiedProduct })
        assertTrue(metadata.none { record -> record.semantic == AquariumSubstrateSemantic.ACTIVE_SOIL })
    }

    @Test
    fun everyVerifiedCatalogProductKeepsAuditableInternetSources() {
        val metadata = (SubstrateCatalog.definitions + GravelCatalog.definitions)
            .mapNotNull(AquariumMaterialDefinition::substrateMetadata)
        val verified = metadata.filter { record -> record.isVerifiedProduct }

        assertEquals(185, verified.size)
        assertTrue(verified.all { record -> record.sourceUrl?.startsWith("https://") == true })
        assertTrue(verified.all { record -> record.sourceOrganization.isNotBlank() })
        assertTrue(verified.all { record -> record.sourceRecordId.isNotBlank() })
        assertTrue(verified.all { record -> record.catalogRevision == 1 })
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
        val EXPECTED_PRODUCT_IDS =
            setOf(
                "substrate_chihiros_aquasoil_9l",
                "substrate_chihiros_aquasoil_3l",
                "substrate_ada_tourmaline_bc",
                "substrate_dennerle_deponitmix_4_8kg"
            ) + (1..181).mapTo(mutableSetOf()) { index ->
                "gravel_${index.toString().padStart(4, '0')}"
            }
    }
}
