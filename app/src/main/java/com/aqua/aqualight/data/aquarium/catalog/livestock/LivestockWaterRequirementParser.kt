package com.aqua.aqualight.data.aquarium.catalog.livestock

import com.aqua.aqualight.application.aquarium.AquariumWaterParameter
import com.aqua.aqualight.application.aquarium.LivestockParameterRange
import com.aqua.aqualight.application.aquarium.LivestockRequirementUnavailable
import com.aqua.aqualight.application.aquarium.LivestockRequirementUnavailableReason
import com.aqua.aqualight.application.aquarium.LivestockWarningMode
import com.aqua.aqualight.application.aquarium.LivestockWaterRequirements

internal object LivestockWaterRequirementParser {

    private const val NUMBER = """[+-]?\d+(?:[.,]\d+)?"""
    private val upperBound = Regex("""(<=|<|≤)\s*($NUMBER)""")
    private val lowerBound = Regex("""(>=|>|≥)\s*($NUMBER)""")
    private val interval = Regex("""($NUMBER)\s*[-–—]\s*($NUMBER)""")
    private val single = Regex(NUMBER)
    private val approximation = Regex("""(?:~|≈|about\s+)""", RegexOption.IGNORE_CASE)

    internal sealed interface RangeParseResult {
        data object Missing : RangeParseResult
        data class Parsed(val range: LivestockParameterRange) : RangeParseResult
        data class Unparseable(val sourceText: String) : RangeParseResult
    }

    fun parse(entry: LivestockCatalogEntry): LivestockWaterRequirements {
        val unavailable = mutableListOf<LivestockRequirementUnavailable>()

        fun resolved(parameter: AquariumWaterParameter, raw: String?): LivestockParameterRange? =
            when (val result = parseRangeResult(raw)) {
                RangeParseResult.Missing -> null
                is RangeParseResult.Parsed -> result.range
                is RangeParseResult.Unparseable -> {
                    unavailable += LivestockRequirementUnavailable(
                        parameter = parameter,
                        sourceText = result.sourceText,
                        reason = LivestockRequirementUnavailableReason.UNPARSEABLE_REQUIREMENT
                    )
                    null
                }
            }

        val mode = LivestockWarningMode.fromCatalogValue(entry.warningMode)
        if (mode == LivestockWarningMode.UNKNOWN) {
            unavailable += LivestockRequirementUnavailable(
                parameter = null,
                sourceText = entry.warningMode,
                reason = LivestockRequirementUnavailableReason.UNKNOWN_WARNING_MODE
            )
        }


        unavailable += unverifiedSources(entry)

        return LivestockWaterRequirements(
            temperatureC = resolved(AquariumWaterParameter.TEMPERATURE_C, entry.temperatureC),
            ph = resolved(AquariumWaterParameter.PH, entry.ph),
            flow = entry.flow?.trim()?.takeIf(String::isNotBlank),
            warningMode = mode,
            catalogEntryId = entry.id,
            unavailableRequirements = unavailable.toList()
        )
    }

    private fun unverifiedSources(entry: LivestockCatalogEntry): List<LivestockRequirementUnavailable> {
        // JSON field names and numeric syntax do not establish reporting basis or unit.
        // Keep source text until an evidence-backed profile normalizes it for this model.
        val rawFields = listOf(
            AquariumWaterParameter.GENERAL_HARDNESS to entry.ghDgh,
            AquariumWaterParameter.CARBONATE_HARDNESS to entry.khDkh,
            AquariumWaterParameter.REPORTED_TDS to entry.tdsPpm,
            AquariumWaterParameter.SPECIFIC_GRAVITY to entry.specificGravity,
            AquariumWaterParameter.TOTAL_ALKALINITY to entry.alkalinityDkh,
            AquariumWaterParameter.CALCIUM_CONCENTRATION to entry.calciumPpm,
            AquariumWaterParameter.MAGNESIUM_CONCENTRATION to entry.magnesiumPpm,
            AquariumWaterParameter.NITRATE_NO3 to entry.nitratePpm,
            AquariumWaterParameter.ORTHOPHOSPHATE_PO4 to entry.phosphatePpm,
            AquariumWaterParameter.PAR to entry.par
        )
        return rawFields.mapNotNull { (parameter, raw) ->
            val result = parseRangeResult(raw)
            val reason = when (result) {
                RangeParseResult.Missing -> return@mapNotNull null
                is RangeParseResult.Parsed ->
                    LivestockRequirementUnavailableReason.UNVERIFIED_SOURCE_SEMANTICS
                is RangeParseResult.Unparseable ->
                    LivestockRequirementUnavailableReason.UNPARSEABLE_REQUIREMENT
            }
            LivestockRequirementUnavailable(parameter, raw.orEmpty(), reason)
        }
    }

    internal fun parseRange(raw: String?): LivestockParameterRange? =
        (parseRangeResult(raw) as? RangeParseResult.Parsed)?.range

    internal fun parseRangeResult(raw: String?): RangeParseResult {
        val sourceText = raw?.takeIf(String::isNotBlank) ?: return RangeParseResult.Missing
        val normalized = sourceText.trim()
        val prefix = approximation.find(normalized)?.takeIf { it.range.first == 0 }
        val approximate = prefix != null
        val expression = if (prefix == null) normalized else {
            normalized.substring(prefix.range.last + 1).trim()
        }

        return parseUpperBound(expression, sourceText, approximate)
            ?: parseLowerBound(expression, sourceText, approximate)
            ?: parseInterval(expression, sourceText, approximate)
            ?: parseNominal(expression, sourceText)
            ?: RangeParseResult.Unparseable(sourceText)
    }

    private fun parseUpperBound(
        expression: String,
        sourceText: String,
        approximate: Boolean
    ): RangeParseResult? = upperBound.matchEntire(expression)?.let { match ->
        val value = number(match.groupValues[2])
        if (value == null || approximate) {
            RangeParseResult.Unparseable(sourceText)
        } else {
            RangeParseResult.Parsed(
                LivestockParameterRange(
                    maximum = value,
                    maximumInclusive = match.groupValues[1] in setOf("<=", "≤"),
                    sourceText = sourceText
                )
            )
        }
    }

    private fun parseLowerBound(
        expression: String,
        sourceText: String,
        approximate: Boolean
    ): RangeParseResult? = lowerBound.matchEntire(expression)?.let { match ->
        val value = number(match.groupValues[2])
        if (value == null || approximate) {
            RangeParseResult.Unparseable(sourceText)
        } else {
            RangeParseResult.Parsed(
                LivestockParameterRange(
                    minimum = value,
                    minimumInclusive = match.groupValues[1] in setOf(">=", "≥"),
                    sourceText = sourceText
                )
            )
        }
    }

    private fun parseInterval(
        expression: String,
        sourceText: String,
        approximate: Boolean
    ): RangeParseResult? = interval.matchEntire(expression)?.let { match ->
        val minimum = number(match.groupValues[1])
        val maximum = number(match.groupValues[2])
        if (minimum == null || maximum == null || minimum > maximum) {
            RangeParseResult.Unparseable(sourceText)
        } else {
            RangeParseResult.Parsed(
                LivestockParameterRange(
                    minimum = minimum,
                    maximum = maximum,
                    approximate = approximate,
                    sourceText = sourceText
                )
            )
        }
    }

    private fun parseNominal(
        expression: String,
        sourceText: String
    ): RangeParseResult? = single.matchEntire(expression)?.let { match ->
        val value = number(match.value)
        if (value == null) {
            RangeParseResult.Unparseable(sourceText)
        } else {
            RangeParseResult.Parsed(
                LivestockParameterRange(
                    approximate = true,
                    nominalValue = value,
                    sourceText = sourceText
                )
            )
        }
    }

    private fun number(value: String): Double? = value.replace(',', '.').toDoubleOrNull()
        ?.takeIf(Double::isFinite)
}
