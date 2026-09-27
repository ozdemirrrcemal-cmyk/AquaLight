package com.aqua.aqualight.application.aquarium

data class LivestockParameterRange(
    val minimum: Double? = null,
    val maximum: Double? = null,
    val approximate: Boolean = false,
    val minimumInclusive: Boolean = true,
    val maximumInclusive: Boolean = true,
    val nominalTarget: Double? = null
) {
    init {
        require(minimum?.isFinite() != false)
        require(maximum?.isFinite() != false)
        require(minimum == null || maximum == null || minimum <= maximum)
        require(nominalTarget?.isFinite() != false)
    }

    fun contains(
        value: Double
    ): Boolean {
        if (!value.isFinite()) {
            return false
        }

        return (minimum == null || if (minimumInclusive) value >= minimum else value > minimum) &&
            (maximum == null || if (maximumInclusive) value <= maximum else value < maximum)
    }

    val hasComparableBounds: Boolean
        get() = minimum != null || maximum != null
}

enum class LivestockWarningMode {
    SOFT,
    HARD,
    INFORMATIONAL;

    companion object {
        fun fromCatalogValue(
            value: String
        ): LivestockWarningMode {
            return entries.firstOrNull { mode ->
                mode.name.equals(value.trim(), ignoreCase = true)
            } ?: INFORMATIONAL
        }
    }
}

data class LivestockWaterRequirements(
    val temperatureC: LivestockParameterRange? = null,
    val ph: LivestockParameterRange? = null,
    val ghDgh: LivestockParameterRange? = null,
    val khDkh: LivestockParameterRange? = null,
    val tdsPpm: LivestockParameterRange? = null,
    val specificGravity: LivestockParameterRange? = null,
    val alkalinityDkh: LivestockParameterRange? = null,
    val calciumPpm: LivestockParameterRange? = null,
    val magnesiumPpm: LivestockParameterRange? = null,
    val nitratePpm: LivestockParameterRange? = null,
    val phosphatePpm: LivestockParameterRange? = null,
    val par: LivestockParameterRange? = null,
    val flow: String? = null,
    val warningMode: LivestockWarningMode = LivestockWarningMode.SOFT,
    val evidence: LivestockRequirementEvidence? = null
) {
    val hasAnyMeasuredRequirement: Boolean
        get() = listOf(
            temperatureC,
            ph,
            ghDgh,
            khDkh,
            tdsPpm,
            specificGravity,
            alkalinityDkh,
            calciumPpm,
            magnesiumPpm,
            nitratePpm,
            phosphatePpm,
            par
        ).any { range -> range != null }
}

enum class AquariumWaterParameter {
    TEMPERATURE_C,
    PH,
    GH_DGH,
    KH_DKH,
    TDS_PPM,
    SPECIFIC_GRAVITY,
    ALKALINITY_DKH,
    CALCIUM_PPM,
    MAGNESIUM_PPM,
    NITRATE_PPM,
    PHOSPHATE_PPM,
    PAR
}

data class AquariumWaterSnapshot(
    val temperatureC: Double? = null,
    val ph: Double? = null,
    val ghDgh: Double? = null,
    val khDkh: Double? = null,
    val tdsPpm: Double? = null,
    val specificGravity: Double? = null,
    val alkalinityDkh: Double? = null,
    val calciumPpm: Double? = null,
    val magnesiumPpm: Double? = null,
    val nitratePpm: Double? = null,
    val phosphatePpm: Double? = null,
    val par: Double? = null
) {
    init {
        values().forEach { (_, value) ->
            require(value?.isFinite() != false) {
                "Aquarium water measurements must be finite."
            }
        }
    }

    internal fun values(): List<Pair<AquariumWaterParameter, Double?>> = listOf(
        AquariumWaterParameter.TEMPERATURE_C to temperatureC,
        AquariumWaterParameter.PH to ph,
        AquariumWaterParameter.GH_DGH to ghDgh,
        AquariumWaterParameter.KH_DKH to khDkh,
        AquariumWaterParameter.TDS_PPM to tdsPpm,
        AquariumWaterParameter.SPECIFIC_GRAVITY to specificGravity,
        AquariumWaterParameter.ALKALINITY_DKH to alkalinityDkh,
        AquariumWaterParameter.CALCIUM_PPM to calciumPpm,
        AquariumWaterParameter.MAGNESIUM_PPM to magnesiumPpm,
        AquariumWaterParameter.NITRATE_PPM to nitratePpm,
        AquariumWaterParameter.PHOSPHATE_PPM to phosphatePpm,
        AquariumWaterParameter.PAR to par
    )
}

data class LivestockWaterParameterIssue(
    val parameter: AquariumWaterParameter,
    val measuredValue: Double,
    val expectedRange: LivestockParameterRange
)

data class LivestockWaterCompatibility(
    val checkedParameterCount: Int,
    val issues: List<LivestockWaterParameterIssue>,
    val warningMode: LivestockWarningMode,
    val coverage: Map<AquariumWaterParameter, LivestockParameterCoverage> = emptyMap()
) {
    val isCompatible: Boolean
        get() = checkedParameterCount > 0 && issues.isEmpty()
}

object LivestockWaterCompatibilityEvaluator {

    fun evaluate(
        requirements: LivestockWaterRequirements,
        water: AquariumWaterSnapshot
    ): LivestockWaterCompatibility {
        val expected = listOf(
            AquariumWaterParameter.TEMPERATURE_C to requirements.temperatureC,
            AquariumWaterParameter.PH to requirements.ph,
            AquariumWaterParameter.GH_DGH to requirements.ghDgh,
            AquariumWaterParameter.KH_DKH to requirements.khDkh,
            AquariumWaterParameter.TDS_PPM to requirements.tdsPpm,
            AquariumWaterParameter.SPECIFIC_GRAVITY to requirements.specificGravity,
            AquariumWaterParameter.ALKALINITY_DKH to requirements.alkalinityDkh,
            AquariumWaterParameter.CALCIUM_PPM to requirements.calciumPpm,
            AquariumWaterParameter.MAGNESIUM_PPM to requirements.magnesiumPpm,
            AquariumWaterParameter.NITRATE_PPM to requirements.nitratePpm,
            AquariumWaterParameter.PHOSPHATE_PPM to requirements.phosphatePpm,
            AquariumWaterParameter.PAR to requirements.par
        ).toMap()

        var checked = 0
        val coverage = linkedMapOf<AquariumWaterParameter, LivestockParameterCoverage>()
        val issues = water.values().mapNotNull { (parameter, measuredValue) ->
            val range = expected[parameter]
            val state = comparisonCoverage(parameter, range, measuredValue, requirements)
            coverage[parameter] = state
            if (state != LivestockParameterCoverage.COMPARED || range == null || measuredValue == null) {
                return@mapNotNull null
            }

            checked += 1

            if (range.contains(measuredValue)) {
                null
            } else {
                LivestockWaterParameterIssue(
                    parameter = parameter,
                    measuredValue = measuredValue,
                    expectedRange = range
                )
            }
        }

        return LivestockWaterCompatibility(
            checkedParameterCount = checked,
            issues = issues,
            warningMode = requirements.warningMode,
            coverage = java.util.Collections.unmodifiableMap(coverage)
        )
    }

    private fun comparisonCoverage(
        parameter: AquariumWaterParameter,
        range: LivestockParameterRange?,
        measuredValue: Double?,
        requirements: LivestockWaterRequirements
    ): LivestockParameterCoverage = when {
        requirements.evidence?.parameters?.get(parameter) is LivestockRequirementParseResult.Unparseable ->
            LivestockParameterCoverage.UNPARSEABLE_REQUIREMENT
        range == null -> LivestockParameterCoverage.REQUIREMENT_MISSING
        range.approximate || !range.hasComparableBounds ||
            requirements.warningMode == LivestockWarningMode.INFORMATIONAL -> LivestockParameterCoverage.INFORMATIONAL
        measuredValue == null -> LivestockParameterCoverage.MEASUREMENT_MISSING
        else -> LivestockParameterCoverage.COMPARED
    }
}
