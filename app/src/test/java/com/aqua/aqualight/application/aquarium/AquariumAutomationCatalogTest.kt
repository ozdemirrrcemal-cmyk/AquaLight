package com.aqua.aqualight.application.aquarium

import com.aqua.aqualight.data.care.smartcare.SmartCareEvidenceId
import com.aqua.aqualight.ui.tabs.aquarium.catalog.material.GravelCatalog
import com.aqua.aqualight.ui.tabs.aquarium.catalog.material.SubstrateCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AquariumAutomationCatalogTest {

    @Test
    fun `substrate semantics come only from the reviewed product id`() {
        assertEquals(
            AquariumSubstrateSemantic.ACTIVE_SOIL,
            AquariumSubstrateSemantics.resolve(
                productId = "substrate_chihiros_aquasoil_9l",
                categoryKey = "substrate"
            )
        )
        assertEquals(
            AquariumSubstrateSemantic.ADDITIVE,
            AquariumSubstrateSemantics.resolve(
                productId = "substrate_ada_tourmaline_bc",
                categoryKey = "substrate"
            )
        )
        assertEquals(
            AquariumSubstrateSemantic.NUTRIENT_BASE,
            AquariumSubstrateSemantics.resolve(
                productId = "substrate_dennerle_deponit_mix_pro_4_8kg",
                categoryKey = "substrate"
            )
        )
        assertEquals(
            AquariumSubstrateSemantic.UNKNOWN,
            AquariumSubstrateSemantics.resolve(
                productId = "custom_substrate_user_product",
                categoryKey = "substrate"
            )
        )
        assertEquals(
            AquariumSubstrateSemantic.UNKNOWN,
            AquariumSubstrateSemantics.resolve(
                productId = "custom_gravel_user_product",
                categoryKey = "gravel"
            )
        )
        assertEquals(
            AquariumSubstrateSemantic.INERT,
            AquariumSubstrateSemantics.resolve(
                productId = "gravel_jbl_sansibar_dark",
                categoryKey = "gravel"
            )
        )
        assertEquals(
            AquariumSubstrateSemantic.UNKNOWN,
            AquariumSubstrateSemantics.resolve(
                productId = "gravel_natural_river_sand",
                categoryKey = "gravel"
            )
        )
        assertEquals(
            AquariumSubstrateSemantic.UNKNOWN,
            AquariumSubstrateSemantics.resolve(
                productId = "substrate_chihiros_aquasoil_9l",
                categoryKey = "gravel"
            )
        )
        assertEquals(
            false,
            AquariumSubstrateSemantics.matchesCatalogCategory(
                productId = "substrate_chihiros_aquasoil_9l",
                categoryKey = "gravel"
            )
        )
    }

    @Test
    fun `every selectable substrate product has an explicit reviewed record`() {
        val selectableIds = (SubstrateCatalog.definitions + GravelCatalog.definitions)
            .map { definition -> definition.id to definition.categoryKey }
            .toSet()
        val reviewedIds = AquariumSubstrateSemantics.records
            .map { record -> record.productId to record.categoryKey }
            .toSet()

        assertEquals(selectableIds, reviewedIds)
        assertTrue(
            AquariumSubstrateSemantics.records.none { record ->
                record.semantic == AquariumSubstrateSemantic.UNKNOWN ||
                    record.semantic == AquariumSubstrateSemantic.NOT_APPLICABLE
            }
        )
        assertEquals(
            AquariumSubstrateSemantic.ACTIVE_SOIL,
            AquariumSubstrateSemantics.aggregate(
                listOf(
                    "substrate_chihiros_aquasoil_9l",
                    "gravel_jbl_sansibar_dark"
                )
            )
        )
        assertEquals(
            AquariumSubstrateSemantic.UNKNOWN,
            AquariumSubstrateSemantics.aggregate(listOf("custom_substrate_user_product"))
        )
    }

    @Test
    fun `plant demand is exact-record based and never category inferred`() {
        assertEquals(91, AquariumPlantLightCatalog.records.size)
        assertEquals(
            AquariumPlantLightDemand.LOW,
            AquariumPlantLightCatalog.resolve("plant:anubias_barteri")
        )
        assertEquals(
            AquariumPlantLightDemand.HIGH,
            AquariumPlantLightCatalog.resolve("plant:hemianthus_callitrichoides_cuba")
        )
        assertEquals(
            AquariumPlantLightDemand.LOW,
            AquariumPlantLightCatalog.resolve("plant:lilaeopsis_brasiliensis")
        )
        assertEquals(
            AquariumPlantLightDemand.MEDIUM,
            AquariumPlantLightCatalog.resolve("plant:hygrophila_pinnatifida")
        )
        assertEquals(
            AquariumPlantLightDemand.UNKNOWN,
            AquariumPlantLightCatalog.resolve("plant:unreviewed_ground_cover")
        )
    }

    @Test
    fun `unreviewed plant answer cannot lower a reviewed demand floor`() {
        val catalogIds = listOf(
            "plant:micranthemum_tweediei_monte_carlo",
            "plant:unreviewed_ground_cover"
        )

        val unanswered = AquariumPlantLightCatalog.resolveSelection(
            catalogIds,
            AquariumPlantLightDemand.UNKNOWN
        )
        val invalidLow = AquariumPlantLightCatalog.resolveSelection(
            catalogIds,
            AquariumPlantLightDemand.LOW
        )
        val answered = AquariumPlantLightCatalog.resolveSelection(
            catalogIds,
            AquariumPlantLightDemand.HIGH
        )

        assertTrue(unanswered.requiresUserInput)
        assertEquals(AquariumPlantLightDemand.MEDIUM, unanswered.reviewedDemandFloor)
        assertEquals(AquariumPlantLightDemand.UNKNOWN, unanswered.effectiveDemand)
        assertEquals(AquariumPlantLightDemand.UNKNOWN, invalidLow.effectiveDemand)
        assertEquals(AquariumPlantLightDemand.HIGH, answered.effectiveDemand)
    }

    @Test
    fun `reviewed high demand makes an unknown companion unable to raise the maximum`() {
        val selection = AquariumPlantLightCatalog.resolveSelection(
            listOf(
                "plant:hemianthus_callitrichoides_cuba",
                "plant:unreviewed_ground_cover"
            ),
            AquariumPlantLightDemand.UNKNOWN
        )

        assertEquals(AquariumPlantLightDemand.HIGH, selection.reviewedDemandFloor)
        assertEquals(AquariumPlantLightDemand.HIGH, selection.effectiveDemand)
        assertEquals(false, selection.requiresUserInput)
    }

    @Test
    fun `catalog evidence identifies each exact manufacturer record`() {
        val record = requireNotNull(
            AquariumPlantLightCatalog.record("plant:micranthemum_tweediei_monte_carlo")
        )

        assertEquals("4442", record.sourceRecordId)
        assertEquals("tropica_plant_4442", record.evidenceSourceId)
        assertEquals(
            "https://tropica.com/en/plants/plantdetails/4442/4442",
            record.sourceUrl
        )
        assertTrue(
            "chihiros_aqua_soil_launch" in AquariumSubstrateSemantics.evidenceSourceIds(
                listOf("substrate_chihiros_aquasoil_3l")
            )
        )
        val knownEvidenceIds = SmartCareEvidenceId.entries
            .map(SmartCareEvidenceId::stableId)
            .toSet()
        assertTrue(
            AquariumSubstrateSemantics.records
                .mapNotNull(AquariumSubstrateCatalogRecord::evidenceSourceId)
                .all(knownEvidenceIds::contains)
        )
    }
}
