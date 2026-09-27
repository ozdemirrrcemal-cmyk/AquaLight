package com.aqua.aqualight.application.aquarium.health.water

import com.aqua.aqualight.application.aquarium.LivestockParameterRange

internal object WaterRequirementIntersection {
    fun conflicts(findings: List<WaterRuleFinding>): List<WaterRequirementConflict> = buildList {
        val byParameter = findings.filter(::hasComparableRequirement).groupBy { it.parameter }.toSortedMap()
        byParameter.forEach { (parameter, rows) ->
            rows.forEachIndexed { index, first ->
                rows.drop(index + 1).forEach { second ->
                    if (!overlaps(requireNotNull(first.expectedRange), requireNotNull(second.expectedRange))) {
                        add(WaterRequirementConflict(parameter, first, second,
                            first.entity.kind == WaterAssessmentEntityKind.PLANT &&
                                second.entity.kind == WaterAssessmentEntityKind.PLANT))
                    }
                }
            }
        }
    }

    fun hasComparableRequirement(finding: WaterRuleFinding): Boolean =
        finding.expectedRange?.let { it.hasComparableBounds && !it.approximate } == true &&
            finding.catalogRevision != null && finding.parameter in WaterAssessmentRequirements.comparable &&
            (finding.gap == null || finding.gap in setOf(
                WaterAssessmentGap.MEASUREMENT_MISSING, WaterAssessmentGap.SOURCE_UNRESOLVED))

    private fun overlaps(first: LivestockParameterRange, second: LivestockParameterRange): Boolean =
        !endsBefore(first, second) && !endsBefore(second, first)

    private fun endsBefore(first: LivestockParameterRange, second: LivestockParameterRange): Boolean {
        val upper = first.maximum
        val lower = second.minimum
        if (upper == null || lower == null) return false
        return upper < lower || upper == lower && (!first.maximumInclusive || !second.minimumInclusive)
    }
}
