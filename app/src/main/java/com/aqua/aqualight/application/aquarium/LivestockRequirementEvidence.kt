package com.aqua.aqualight.application.aquarium

sealed interface LivestockRequirementParseResult {
    data object Missing : LivestockRequirementParseResult
    data class Parsed(val range: LivestockParameterRange, val raw: String) : LivestockRequirementParseResult
    data class Unparseable(val raw: String) : LivestockRequirementParseResult
}

/** Retains source columns and raw text; ppm columns alone do not establish analyte/basis equivalence. */
data class LivestockRequirementEvidence(
    val catalogId: String,
    val catalogRevision: String,
    val confidence: String?,
    val rawWarningMode: String,
    val parameters: Map<AquariumWaterParameter, LivestockRequirementParseResult>
)

enum class LivestockParameterCoverage {
    COMPARED, MEASUREMENT_MISSING, REQUIREMENT_MISSING, UNPARSEABLE_REQUIREMENT, INFORMATIONAL
}
