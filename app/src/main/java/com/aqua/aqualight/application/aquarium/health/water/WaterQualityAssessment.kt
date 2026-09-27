package com.aqua.aqualight.application.aquarium.health.water

import com.aqua.aqualight.application.aquarium.AquariumWaterParameter
import com.aqua.aqualight.application.aquarium.LivestockParameterRange

enum class WaterHazardSeverity { NONE, ADVISORY, WARNING, CRITICAL }
enum class WaterAssessmentCoverage { NONE, PARTIAL, COMPLETE }
enum class WaterAssessmentDirection { BELOW, WITHIN, ABOVE }
enum class WaterAssessmentEntityKind { PLANT, LIVESTOCK }
enum class WaterAssessmentGap {
    MEASUREMENT_MISSING, REQUIREMENT_MISSING, UNPARSEABLE_REQUIREMENT, INFORMATIONAL,
    SOURCE_UNRESOLVED, CATALOG_UNAVAILABLE, CATALOG_ENTRY_MISSING, CUSTOM_UNVERIFIED,
    PARTIAL_PLANT, UNKNOWN_TANK_TYPE, UNKNOWN_REQUIREMENT_SEMANTIC, RULE_MISSING,
    INCOMPATIBLE_HABITAT, UNKNOWN_HABITAT
}

data class WaterAssessmentEntity(
    val kind: WaterAssessmentEntityKind,
    val localId: Long,
    val catalogId: String,
    val displayName: String = ""
)

data class WaterRuleFinding(
    val entity: WaterAssessmentEntity,
    val parameter: AquariumWaterParameter,
    val ruleId: String,
    val ruleRevision: String,
    val catalogRevision: String?,
    val measuredValue: Double?,
    val expectedRange: LivestockParameterRange?,
    val direction: WaterAssessmentDirection?,
    val severity: WaterHazardSeverity?,
    val gap: WaterAssessmentGap?
)

/** Pairwise interval witnesses are minimal conflicting sets for a one-dimensional interval. */
data class WaterRequirementConflict(
    val parameter: AquariumWaterParameter,
    val first: WaterRuleFinding,
    val second: WaterRuleFinding,
    val authoritative: Boolean
)

data class WaterAssessmentRecommendation(val code: String, val findingRuleIds: List<String>)

data class WaterHabitatConflict(
    val entity: WaterAssessmentEntity,
    val tankEnvironment: String,
    val sourceWaterGroup: String
)

data class WaterQualityAssessment(
    val engineVersion: String,
    val ruleRevision: String,
    val contextRevision: String,
    val hazardSeverity: WaterHazardSeverity?,
    val coverage: WaterAssessmentCoverage,
    val conflictCoverage: WaterAssessmentCoverage,
    val findings: List<WaterRuleFinding>,
    val conflicts: List<WaterRequirementConflict>,
    val recommendations: List<WaterAssessmentRecommendation>,
    val chemistryGap: WaterAssessmentGap = WaterAssessmentGap.RULE_MISSING,
    val habitatConflicts: List<WaterHabitatConflict> = emptyList()
)
