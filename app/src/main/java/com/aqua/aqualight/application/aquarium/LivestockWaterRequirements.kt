package com.aqua.aqualight.application.aquarium

data class LivestockParameterRange(
    val minimum: Double? = null,
    val maximum: Double? = null,
    val approximate: Boolean = false,
    val minimumInclusive: Boolean = true,
    val maximumInclusive: Boolean = true,
    val nominalValue: Double? = null,
    val sourceText: String? = null
) {
    init {
        require(minimum?.isFinite() != false)
        require(maximum?.isFinite() != false)
        require(nominalValue?.isFinite() != false)
        require(minimum == null || maximum == null || minimum <= maximum)
        require(minimum == null || maximum == null || minimum != maximum ||
            (minimumInclusive && maximumInclusive))
        require(nominalValue == null || (approximate && minimum == null && maximum == null))
    }

    val isComparable: Boolean
        get() = !approximate && nominalValue == null &&
            (minimum != null || maximum != null)

    fun contains(
        value: Double
    ): Boolean {
        if (!isComparable || !value.isFinite()) {
            return false
        }

        return (minimum == null || if (minimumInclusive) value >= minimum else value > minimum) &&
            (maximum == null || if (maximumInclusive) value <= maximum else value < maximum)
    }
}

enum class LivestockWarningMode {
    SOFT,
    HARD,
    INFORMATIONAL,
    UNKNOWN;

    companion object {
        fun fromCatalogValue(
            value: String
        ): LivestockWarningMode {
            return entries.firstOrNull { mode ->
                mode.name.equals(value.trim(), ignoreCase = true)
            } ?: UNKNOWN
        }
    }
}

enum class LivestockRequirementUnavailableReason {
    UNPARSEABLE_REQUIREMENT,
    UNVERIFIED_SOURCE_SEMANTICS,
    APPROXIMATE_ONLY,
    INFORMATIONAL_ONLY,
    UNKNOWN_WARNING_MODE
}

data class LivestockRequirementUnavailable(
    val parameter: AquariumWaterParameter?,
    val sourceText: String,
    val reason: LivestockRequirementUnavailableReason
)

data class LivestockWaterRequirements(
    val temperatureC: LivestockParameterRange? = null,
    val ph: LivestockParameterRange? = null,
    val generalHardnessMgLAsCaCo3: LivestockParameterRange? = null,
    val carbonateHardnessMgLAsCaCo3: LivestockParameterRange? = null,
    val reportedTdsPpm: LivestockParameterRange? = null,
    val specificGravity: LivestockParameterRange? = null,
    val totalAlkalinityMeqL: LivestockParameterRange? = null,
    val calciumMgLAsCa: LivestockParameterRange? = null,
    val magnesiumMgLAsMg: LivestockParameterRange? = null,
    val nitrateMgLAsNo3: LivestockParameterRange? = null,
    val orthophosphateMgLAsPo4: LivestockParameterRange? = null,
    val par: LivestockParameterRange? = null,
    val flow: String? = null,
    val warningMode: LivestockWarningMode = LivestockWarningMode.SOFT,
    val catalogEntryId: String? = null,
    val unavailableRequirements: List<LivestockRequirementUnavailable> = emptyList()
) {
    val hasAnyMeasuredRequirement: Boolean
        get() = listOf(
            temperatureC,
            ph,
            generalHardnessMgLAsCaCo3,
            carbonateHardnessMgLAsCaCo3,
            reportedTdsPpm,
            specificGravity,
            totalAlkalinityMeqL,
            calciumMgLAsCa,
            magnesiumMgLAsMg,
            nitrateMgLAsNo3,
            orthophosphateMgLAsPo4,
            par
        ).any { range -> range != null }
}

enum class AquariumWaterParameter {
    TEMPERATURE_C,
    PH,
    GENERAL_HARDNESS,
    CARBONATE_HARDNESS,
    REPORTED_TDS,
    SPECIFIC_GRAVITY,
    TOTAL_ALKALINITY,
    CALCIUM_CONCENTRATION,
    MAGNESIUM_CONCENTRATION,
    NITRATE_NO3,
    NITRITE_NO2,
    ORTHOPHOSPHATE_PO4,
    TOTAL_AMMONIA_NITROGEN,
    FREE_AMMONIA_NH3,
    PAR
}

data class AquariumWaterSnapshot(
    val temperatureC: Double? = null,
    val ph: Double? = null,
    val generalHardnessMgLAsCaCo3: Double? = null,
    val carbonateHardnessMgLAsCaCo3: Double? = null,
    val reportedTdsPpm: Double? = null,
    val specificGravity: Double? = null,
    val totalAlkalinityMeqL: Double? = null,
    val calciumMgLAsCa: Double? = null,
    val magnesiumMgLAsMg: Double? = null,
    val nitrateMgLAsNo3: Double? = null,
    val nitriteMgLAsNo2: Double? = null,
    val orthophosphateMgLAsPo4: Double? = null,
    val totalAmmoniaNitrogenMgLAsN: Double? = null,
    val freeAmmoniaMgLAsNh3: Double? = null,
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
        AquariumWaterParameter.GENERAL_HARDNESS to generalHardnessMgLAsCaCo3,
        AquariumWaterParameter.CARBONATE_HARDNESS to carbonateHardnessMgLAsCaCo3,
        AquariumWaterParameter.REPORTED_TDS to reportedTdsPpm,
        AquariumWaterParameter.SPECIFIC_GRAVITY to specificGravity,
        AquariumWaterParameter.TOTAL_ALKALINITY to totalAlkalinityMeqL,
        AquariumWaterParameter.CALCIUM_CONCENTRATION to calciumMgLAsCa,
        AquariumWaterParameter.MAGNESIUM_CONCENTRATION to magnesiumMgLAsMg,
        AquariumWaterParameter.NITRATE_NO3 to nitrateMgLAsNo3,
        AquariumWaterParameter.NITRITE_NO2 to nitriteMgLAsNo2,
        AquariumWaterParameter.ORTHOPHOSPHATE_PO4 to orthophosphateMgLAsPo4,
        AquariumWaterParameter.TOTAL_AMMONIA_NITROGEN to totalAmmoniaNitrogenMgLAsN,
        AquariumWaterParameter.FREE_AMMONIA_NH3 to freeAmmoniaMgLAsNh3,
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
    val unavailableRequirements: List<LivestockRequirementUnavailable> = emptyList(),
    val missingMeasurements: List<AquariumWaterParameter> = emptyList()
) {
    val isCompatible: Boolean
        get() = checkedParameterCount > 0 && issues.isEmpty() &&
            warningMode in setOf(LivestockWarningMode.SOFT, LivestockWarningMode.HARD) &&
            unavailableRequirements.isEmpty() && missingMeasurements.isEmpty()
}

object LivestockWaterCompatibilityEvaluator {

    fun evaluate(
        requirements: LivestockWaterRequirements,
        water: AquariumWaterSnapshot
    ): LivestockWaterCompatibility {
        val expected = expectedRanges(requirements)
        val unavailable = unavailableRequirements(requirements, expected)
        val measured = water.values().toMap()
        val missingMeasurements = expected.mapNotNull { (parameter, range) ->
            parameter.takeIf { range?.isComparable == true && measured[parameter] == null }
        }
        var checked = 0
        val issues = water.values().mapNotNull { (parameter, measuredValue) ->
            val range = expected[parameter]
            if (range == null || measuredValue == null) {
                return@mapNotNull null
            }

            if (!range.isComparable || requirements.warningMode == LivestockWarningMode.UNKNOWN ||
                requirements.warningMode == LivestockWarningMode.INFORMATIONAL) {
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
            unavailableRequirements = unavailable,
            missingMeasurements = missingMeasurements
        )
    }

    private fun expectedRanges(
        requirements: LivestockWaterRequirements
    ): Map<AquariumWaterParameter, LivestockParameterRange?> = listOf(
        AquariumWaterParameter.TEMPERATURE_C to requirements.temperatureC,
        AquariumWaterParameter.PH to requirements.ph,
        AquariumWaterParameter.GENERAL_HARDNESS to requirements.generalHardnessMgLAsCaCo3,
        AquariumWaterParameter.CARBONATE_HARDNESS to requirements.carbonateHardnessMgLAsCaCo3,
        AquariumWaterParameter.REPORTED_TDS to requirements.reportedTdsPpm,
        AquariumWaterParameter.SPECIFIC_GRAVITY to requirements.specificGravity,
        AquariumWaterParameter.TOTAL_ALKALINITY to requirements.totalAlkalinityMeqL,
        AquariumWaterParameter.CALCIUM_CONCENTRATION to requirements.calciumMgLAsCa,
        AquariumWaterParameter.MAGNESIUM_CONCENTRATION to requirements.magnesiumMgLAsMg,
        AquariumWaterParameter.NITRATE_NO3 to requirements.nitrateMgLAsNo3,
        AquariumWaterParameter.ORTHOPHOSPHATE_PO4 to requirements.orthophosphateMgLAsPo4,
        AquariumWaterParameter.PAR to requirements.par
    ).toMap()

    private fun unavailableRequirements(
        requirements: LivestockWaterRequirements,
        expected: Map<AquariumWaterParameter, LivestockParameterRange?>
    ): List<LivestockRequirementUnavailable> {
        val unavailable = requirements.unavailableRequirements.toMutableList()
        expected.forEach { (parameter, range) ->
            if (range != null && !range.isComparable) {
                unavailable += LivestockRequirementUnavailable(
                    parameter = parameter,
                    sourceText = range.sourceText.orEmpty(),
                    reason = LivestockRequirementUnavailableReason.APPROXIMATE_ONLY
                )
            }
        }
        if (requirements.warningMode == LivestockWarningMode.INFORMATIONAL) {
            unavailable += LivestockRequirementUnavailable(
                parameter = null,
                sourceText = LivestockWarningMode.INFORMATIONAL.name,
                reason = LivestockRequirementUnavailableReason.INFORMATIONAL_ONLY
            )
        }
        return unavailable
    }
}
