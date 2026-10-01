package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumWaterParameter
import com.aqua.aqualight.application.aquarium.LivestockParameterRange
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentCoverage
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentDirection
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentEntity
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentEntityKind
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentGap
import com.aqua.aqualight.application.aquarium.health.water.WaterHazardSeverity
import com.aqua.aqualight.application.aquarium.health.water.WaterQualityAssessment
import com.aqua.aqualight.application.aquarium.health.water.WaterRuleFinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TankHealthWaterMetricAssessmentTest {

    @Test
    fun allComparableFindingsWithinRangeAreSuitable() {
        val summary = TankHealthWaterMetricAssessment.summarize(
            assessment(
                finding(0, WaterAssessmentDirection.WITHIN),
                finding(1, WaterAssessmentDirection.WITHIN)
            ),
            AquariumWaterParameter.PH
        )

        assertEquals(TankHealthWaterCompatibilityStatus.SUITABLE, summary.status)
        assertEquals(2, summary.comparedCount)
        assertEquals(2, summary.withinCount)
    }

    @Test
    fun mixedWithinAndOutsideFindingsArePartial() {
        val summary = TankHealthWaterMetricAssessment.summarize(
            assessment(
                finding(0, WaterAssessmentDirection.WITHIN),
                finding(1, WaterAssessmentDirection.ABOVE)
            ),
            AquariumWaterParameter.PH
        )

        assertEquals(TankHealthWaterCompatibilityStatus.PARTIAL, summary.status)
        assertEquals(1, summary.withinCount)
        assertEquals(1, summary.aboveCount)
    }

    @Test
    fun incompleteCatalogCoveragePreventsFalseGreenStatus() {
        val summary = TankHealthWaterMetricAssessment.summarize(
            assessment(
                finding(0, WaterAssessmentDirection.WITHIN),
                finding(1, null)
            ),
            AquariumWaterParameter.PH
        )

        assertEquals(TankHealthWaterCompatibilityStatus.PARTIAL, summary.status)
        assertEquals(1, summary.unassessedCount)
    }

    @Test
    fun allComparableFindingsOutsideRangeAreIncompatible() {
        val summary = TankHealthWaterMetricAssessment.summarize(
            assessment(
                finding(0, WaterAssessmentDirection.ABOVE),
                finding(1, WaterAssessmentDirection.BELOW)
            ),
            AquariumWaterParameter.PH
        )

        assertEquals(TankHealthWaterCompatibilityStatus.INCOMPATIBLE, summary.status)
        assertEquals(2, summary.comparedCount)
    }

    @Test
    fun missingOrNonComparableAssessmentIsUnevaluated() {
        assertEquals(
            TankHealthWaterCompatibilityStatus.UNEVALUATED,
            TankHealthWaterMetricAssessment
                .summarize(null, AquariumWaterParameter.PH)
                .status
        )
        assertEquals(
            TankHealthWaterCompatibilityStatus.UNEVALUATED,
            TankHealthWaterMetricAssessment
                .summarize(assessment(finding(0, null)), AquariumWaterParameter.PH)
                .status
        )
        assertNull(
            TankHealthWaterMetricAssessment.assessmentParameter(WaterParameter.NITRITE)
        )
    }

    @Test
    fun compatibilityStatusUsesCompactUserFacingLabels() {
        assertEquals(
            R.string.tank_health_metric_status_suitable,
            TankHealthWaterMetricAssessment.statusRes(
                TankHealthWaterCompatibilityStatus.SUITABLE
            )
        )
        assertEquals(
            R.string.tank_health_metric_status_partial,
            TankHealthWaterMetricAssessment.statusRes(
                TankHealthWaterCompatibilityStatus.PARTIAL
            )
        )
        assertEquals(
            R.string.tank_health_metric_status_incompatible,
            TankHealthWaterMetricAssessment.statusRes(
                TankHealthWaterCompatibilityStatus.INCOMPATIBLE
            )
        )
        assertEquals(
            R.string.tank_health_metric_status_unevaluated,
            TankHealthWaterMetricAssessment.statusRes(
                TankHealthWaterCompatibilityStatus.UNEVALUATED
            )
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

    @Test
    fun metricRouteRoundTripsWithoutInventingDomainParameters() {
        val parameter = TankHealthWaterMetricId.Parameter(WaterTestParameterId.PH)
        assertEquals(
            parameter,
            TankHealthWaterMetricRoute.decode(TankHealthWaterMetricRoute.encode(parameter))
        )
        assertEquals(
            TankHealthWaterMetricId.Temperature,
            TankHealthWaterMetricRoute.decode(
                TankHealthWaterMetricRoute.encode(TankHealthWaterMetricId.Temperature)
            )
        )
        assertNull(TankHealthWaterMetricRoute.decode("parameter:not-real"))
    }

    private fun assessment(
        vararg findings: WaterRuleFinding
    ): WaterQualityAssessment =
        WaterQualityAssessment(
            engineVersion = "test",
            ruleRevision = "test",
            contextRevision = "test",
            hazardSeverity = findings.mapNotNull { it.severity }.maxOrNull(),
            coverage = if (findings.any { it.direction != null }) {
                WaterAssessmentCoverage.PARTIAL
            } else {
                WaterAssessmentCoverage.NONE
            },
            conflictCoverage = WaterAssessmentCoverage.NONE,
            findings = findings.toList(),
            conflicts = emptyList(),
            recommendations = emptyList()
        )

    private fun finding(
        index: Int,
        direction: WaterAssessmentDirection?
    ): WaterRuleFinding {
        val entity = WaterAssessmentEntity(
            kind = WaterAssessmentEntityKind.LIVESTOCK,
            localId = index + 1L,
            catalogId = "test-fish-$index",
            displayName = "Test fish $index"
        )
        return WaterRuleFinding(
            entity = entity,
            parameter = AquariumWaterParameter.PH,
            ruleId = "rule-$index",
            ruleRevision = "test",
            catalogRevision = "test",
            measuredValue = direction?.let { 7.0 },
            expectedRange = direction?.let {
                LivestockParameterRange(minimum = 6.5, maximum = 7.5)
            },
            direction = direction,
            severity = direction?.let {
                if (it == WaterAssessmentDirection.WITHIN) {
                    WaterHazardSeverity.NONE
                } else {
                    WaterHazardSeverity.ADVISORY
                }
            },
            gap = if (direction == null) {
                WaterAssessmentGap.REQUIREMENT_MISSING
            } else {
                null
            }
        )
    }
}
