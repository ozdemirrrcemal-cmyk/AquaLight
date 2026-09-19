package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.application.aquarium.AquariumSubstrateEvidenceStatus
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun reviewedProductsResolveToResearchBackedSemantics() {
        val expectedSemantics = mapOf(
            "substrate_chihiros_aquasoil_9l" to AquariumSubstrateSemantic.ACTIVE_SOIL,
            "substrate_chihiros_aquasoil_3l" to AquariumSubstrateSemantic.ACTIVE_SOIL,
            "substrate_ada_tourmaline_bc" to AquariumSubstrateSemantic.ADDITIVE,
            "substrate_dennerle_deponitmix_4_8kg" to
                AquariumSubstrateSemantic.NUTRIENT_BASE,
            "gravel_ada_aqua_gravel_s" to AquariumSubstrateSemantic.INERT,
            "gravel_ada_aqua_gravel_m" to AquariumSubstrateSemantic.INERT,
            "gravel_dennerle_nano_gravel_black" to AquariumSubstrateSemantic.INERT,
            "gravel_dennerle_nano_gravel_natural" to AquariumSubstrateSemantic.INERT,
            "gravel_jbl_sansibar_dark" to AquariumSubstrateSemantic.INERT,
            "gravel_jbl_sansibar_white" to AquariumSubstrateSemantic.INERT,
            "gravel_aquael_basaltsand" to AquariumSubstrateSemantic.INERT,
            "gravel_natural_river_sand" to AquariumSubstrateSemantic.UNKNOWN
        )

        val actualSemantics = (SubstrateCatalog.definitions + GravelCatalog.definitions)
            .associate { product -> product.id to product.substrateMetadata?.semantic }

        assertEquals(expectedSemantics, actualSemantics)
    }

    @Test
    fun verifiedProductsKeepAuditableInternetSources() {
        val metadata = (SubstrateCatalog.definitions + GravelCatalog.definitions)
            .mapNotNull(AquariumMaterialDefinition::substrateMetadata)
        val verified = metadata.filter { record -> record.isVerifiedProduct }

        assertEquals(11, verified.size)
        assertTrue(verified.all { record -> record.sourceUrl?.startsWith("https://") == true })
        assertTrue(verified.all { record -> record.sourceOrganization.isNotBlank() })
        assertTrue(verified.all { record -> record.sourceRecordId.isNotBlank() })
        assertTrue(verified.all { record -> record.catalogRevision == 1 })
    }

    @Test
    fun genericAndCustomProductsFailClosedWithoutNameInference() {
        val generic = MaterialCatalog.substrateMetadata(
            productId = "gravel_natural_river_sand",
            categoryKey = MaterialCategoryKey.GRAVEL
        )

        assertEquals(AquariumSubstrateSemantic.UNKNOWN, generic?.semantic)
        assertEquals(AquariumSubstrateEvidenceStatus.UNVERIFIED_GENERIC, generic?.evidenceStatus)
        assertFalse(requireNotNull(generic).isVerifiedProduct)
        assertNull(generic.sourceUrl)
        assertEquals(
            AquariumSubstrateSemantic.UNKNOWN,
            MaterialCatalog.resolveSubstrateSemantic(
                productId = "custom_substrate_user_product",
                categoryKey = MaterialCategoryKey.SUBSTRATE
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
        val EXPECTED_PRODUCT_IDS = setOf(
            "substrate_chihiros_aquasoil_9l",
            "substrate_chihiros_aquasoil_3l",
            "substrate_ada_tourmaline_bc",
            "substrate_dennerle_deponitmix_4_8kg",
            "gravel_ada_aqua_gravel_s",
            "gravel_ada_aqua_gravel_m",
            "gravel_dennerle_nano_gravel_black",
            "gravel_dennerle_nano_gravel_natural",
            "gravel_jbl_sansibar_dark",
            "gravel_jbl_sansibar_white",
            "gravel_aquael_basaltsand",
            "gravel_natural_river_sand"
        )
    }
}
