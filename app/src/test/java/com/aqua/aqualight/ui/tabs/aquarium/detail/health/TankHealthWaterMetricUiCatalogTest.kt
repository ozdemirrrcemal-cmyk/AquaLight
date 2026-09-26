package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TankHealthWaterMetricUiCatalogTest {

    @Test
    fun plantedProfileStartsWithExactlyFiveRecommendedMetrics() {
        val ids = TankHealthWaterMetricUiCatalog.visibleParameterIds(
            tankProfile = AquariumTankTaxonomy.TYPE_PLANTED,
            measuredParameterIds = emptyList()
        )

        assertEquals(
            listOf(
                WaterTestParameterId.PH,
                WaterTestParameterId.NITRATE,
                WaterTestParameterId.PHOSPHATE,
                WaterTestParameterId.GH,
                WaterTestParameterId.KH
            ),
            ids
        )
    }

    @Test
    fun measuredAdditionalTestsAppendAfterRecommendedWithoutDuplicates() {
        val ids = TankHealthWaterMetricUiCatalog.visibleParameterIds(
            tankProfile = AquariumTankTaxonomy.TYPE_PLANTED,
            measuredParameterIds = listOf(
                WaterTestParameterId.NITRATE,
                WaterTestParameterId.POTASSIUM,
                WaterTestParameterId.TDS,
                WaterTestParameterId.IRON
            )
        )

        assertEquals(8, ids.size)
        assertEquals(WaterTestParameterId.POTASSIUM, ids[5])
        assertEquals(WaterTestParameterId.TDS, ids[6])
        assertEquals(WaterTestParameterId.IRON, ids[7])
        assertEquals(1, ids.count { id -> id == WaterTestParameterId.NITRATE })
    }

    @Test
    fun dashboardCapsVisibleMetricsAtEight() {
        val ids = TankHealthWaterMetricUiCatalog.visibleParameterIds(
            tankProfile = AquariumTankTaxonomy.TYPE_PLANTED,
            measuredParameterIds = WaterTestParameterId.entries
        )

        assertEquals(TankHealthWaterMetricUiCatalog.MAX_DASHBOARD_METRICS, ids.size)
        assertTrue(ids.containsAll(WaterTestProfileUiCatalog.recommendedIds(
            AquariumTankTaxonomy.TYPE_PLANTED
        )))
        assertFalse(ids.drop(5).isEmpty())
    }
}
