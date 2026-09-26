package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterTestProfileUiCatalogTest {

    @Test
    fun representativeProfilesExposeDeterministicRecommendedSets() {
        assertEquals(
            listOf(
                WaterTestParameterId.PH,
                WaterTestParameterId.AMMONIA_AMMONIUM,
                WaterTestParameterId.NITRITE,
                WaterTestParameterId.NITRATE,
                WaterTestParameterId.GH,
                WaterTestParameterId.KH
            ),
            WaterTestProfileUiCatalog.recommendedIds(
                AquariumTankTaxonomy.TYPE_FRESHWATER_FISH
            )
        )
        assertEquals(
            listOf(
                WaterTestParameterId.PH,
                WaterTestParameterId.NITRATE,
                WaterTestParameterId.PHOSPHATE,
                WaterTestParameterId.GH,
                WaterTestParameterId.KH
            ),
            WaterTestProfileUiCatalog.recommendedIds(
                AquariumTankTaxonomy.TYPE_PLANTED
            )
        )
        assertEquals(
            listOf(
                WaterTestParameterId.PH,
                WaterTestParameterId.AMMONIA_AMMONIUM,
                WaterTestParameterId.NITRITE,
                WaterTestParameterId.NITRATE,
                WaterTestParameterId.GH,
                WaterTestParameterId.KH,
                WaterTestParameterId.TDS
            ),
            WaterTestProfileUiCatalog.recommendedIds(
                AquariumTankTaxonomy.TYPE_SHRIMP
            )
        )
        assertEquals(
            listOf(
                WaterTestParameterId.PH,
                WaterTestParameterId.AMMONIA_AMMONIUM,
                WaterTestParameterId.NITRITE,
                WaterTestParameterId.NITRATE,
                WaterTestParameterId.SALINITY,
                WaterTestParameterId.KH,
                WaterTestParameterId.PHOSPHATE
            ),
            WaterTestProfileUiCatalog.recommendedIds(
                AquariumTankTaxonomy.TYPE_MARINE_FISH
            )
        )
        assertEquals(
            WaterTestProfileUiCatalog.recommendedIds(AquariumTankTaxonomy.TYPE_SPS_REEF),
            WaterTestProfileUiCatalog.recommendedIds(AquariumTankTaxonomy.TYPE_MIXED_REEF)
        )
    }

    @Test
    fun recommendedAndAdditionalSetsNeverOverlap() {
        AquariumTankTaxonomy.tankTypeCodes.forEach { profile ->
            val recommended = WaterTestProfileUiCatalog.recommendedIds(profile).toSet()
            val additional = WaterTestProfileUiCatalog.additionalIds(profile).toSet()
            assertTrue(
                "$profile has duplicate recommended/additional parameters",
                recommended.intersect(additional).isEmpty()
            )
        }
    }

    @Test
    fun emptyPresentationValueRemainsEmptyAndNeverBecomesZero() {
        val model = WaterTestProfileUiCatalog.model(
            tankProfile = AquariumTankTaxonomy.TYPE_PLANTED,
            id = WaterTestParameterId.NITRATE,
            importance = WaterTestImportance.RECOMMENDED,
            value = ""
        )

        assertEquals("", model.value)
    }
}
