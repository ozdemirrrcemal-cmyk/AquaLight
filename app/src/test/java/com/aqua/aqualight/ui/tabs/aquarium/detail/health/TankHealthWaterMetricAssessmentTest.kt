package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumWaterParameter
import com.aqua.aqualight.application.aquarium.LivestockParameterRange
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentCoverage
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentDirection
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentEntity
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentEntityKind
import com.aqua.aqualight.application.aquarium.health.water.WaterHazardSeverity
import com.aqua.aqualight.application.aquarium.health.water.WaterQualityAssessment
import com.aqua.aqualight.application.aquarium.health.water.WaterRuleFinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TankHealthWaterMetricAssessmentTest {

    @Test
    fun allWithinFindingsRenderWithinCatalogStatus() {
        assertEquals(
            R.string.tank_health_metric_assessment_within,
            TankHealthWaterMetricAssessment.statusRes(
                assessment(WaterAssessmentDirection.WITHIN),
                AquariumWaterParameter.PH
            )
        )
    }

    @Test
    fun singleDirectionOutsideFindingsKeepTheirDirection() {
        assertEquals(
            R.string.tank_health_metric_assessment_above,
            TankHealthWaterMetricAssessment.statusRes(
                assessment(WaterAssessmentDirection.ABOVE),
                AquariumWaterParameter.PH
            )
        )
        assertEquals(
            R.string.tank_health_metric_assessment_below,
            TankHealthWaterMetricAssessment.statusRes(
                assessment(WaterAssessmentDirection.BELOW),
                AquariumWaterParameter.PH
            )
        )
    }

    @Test
    fun mixedEntityResultsDoNotPretendThereIsOneCompatibleRange() {
        assertEquals(
            R.string.tank_health_metric_assessment_mixed,
            TankHealthWaterMetricAssessment.statusRes(
                assessment(
                    WaterAssessmentDirection.WITHIN,
                    WaterAssessmentDirection.ABOVE
                ),
                AquariumWaterParameter.PH
            )
        )
    }

    @Test
    fun missingOrUnsupportedAssessmentIsExplicit() {
        assertEquals(
            R.string.tank_health_metric_assessment_unavailable,
            TankHealthWaterMetricAssessment.statusRes(null, AquariumWaterParameter.PH)
        )
        assertEquals(
            R.string.tank_health_metric_assessment_not_comparable,
            TankHealthWaterMetricAssessment.statusRes(assessment(), AquariumWaterParameter.PH)
        )
        assertNull(
            TankHealthWaterMetricAssessment.assessmentParameter(WaterParameter.NITRITE)
        )
    }

    @Test
    fun dashboardAssessmentMappingMatchesComparableStoredParameters() {
        assertEquals(
            AquariumWaterParameter.PH,
            TankHealthWaterMetricAssessment.assessmentParameter(WaterParameter.PH)
        )
        assertEquals(
            AquariumWaterParameter.GH_DGH,
            TankHealthWaterMetricAssessment.assessmentParameter(WaterParameter.GH)
        )
        assertEquals(
            AquariumWaterParameter.NITRATE_PPM,
            TankHealthWaterMetricAssessment.assessmentParameter(WaterParameter.NITRATE)
        )
        assertEquals(
            AquariumWaterParameter.PHOSPHATE_PPM,
            TankHealthWaterMetricAssessment.assessmentParameter(WaterParameter.PHOSPHATE)
        )
    }

    private fun assessment(
        vararg directions: WaterAssessmentDirection
    ): WaterQualityAssessment {
        val entity = WaterAssessmentEntity(
            kind = WaterAssessmentEntityKind.LIVESTOCK,
            localId = 1L,
            catalogId = "test-fish",
            displayName = "Test fish"
        )
        val range = LivestockParameterRange(minimum = 6.5, maximum = 7.5)
        return WaterQualityAssessment(
            engineVersion = "test",
            ruleRevision = "test",
            contextRevision = "test",
            hazardSeverity = directions
                .map { direction ->
                    if (direction == WaterAssessmentDirection.WITHIN) {
                        WaterHazardSeverity.NONE
                    } else {
                        WaterHazardSeverity.ADVISORY
                    }
                }
                .maxOrNull(),
            coverage = if (directions.isEmpty()) {
                WaterAssessmentCoverage.NONE
            } else {
                WaterAssessmentCoverage.PARTIAL
            },
            conflictCoverage = WaterAssessmentCoverage.NONE,
            findings = directions.mapIndexed { index, direction ->
                WaterRuleFinding(
                    entity = entity,
                    parameter = AquariumWaterParameter.PH,
                    ruleId = "rule-$index",
                    ruleRevision = "test",
                    catalogRevision = "test",
                    measuredValue = 7.0,
                    expectedRange = range,
                    direction = direction,
                    severity = if (direction == WaterAssessmentDirection.WITHIN) {
                        WaterHazardSeverity.NONE
                    } else {
                        WaterHazardSeverity.ADVISORY
                    },
                    gap = null
                )
            },
            conflicts = emptyList(),
            recommendations = emptyList()
        )
    }
}
