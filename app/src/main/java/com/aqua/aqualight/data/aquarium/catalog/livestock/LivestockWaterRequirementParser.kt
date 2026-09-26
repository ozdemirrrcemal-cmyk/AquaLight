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

        return LivestockWaterRequirements(
            temperatureC = resolved(AquariumWaterParameter.TEMPERATURE_C, entry.temperatureC),
            ph = resolved(AquariumWaterParameter.PH, entry.ph),
            ghDgh = resolved(AquariumWaterParameter.GH_DGH, entry.ghDgh),
            khDkh = resolved(AquariumWaterParameter.KH_DKH, entry.khDkh),
            tdsPpm = resolved(AquariumWaterParameter.TDS_PPM, entry.tdsPpm),
            specificGravity = resolved(
                AquariumWaterParameter.SPECIFIC_GRAVITY,
                entry.specificGravity
            ),
            alkalinityDkh = resolved(AquariumWaterParameter.ALKALINITY_DKH, entry.alkalinityDkh),
            calciumPpm = resolved(AquariumWaterParameter.CALCIUM_PPM, entry.calciumPpm),
            magnesiumPpm = resolved(AquariumWaterParameter.MAGNESIUM_PPM, entry.magnesiumPpm),
            nitratePpm = resolved(AquariumWaterParameter.NITRATE_PPM, entry.nitratePpm),
            phosphatePpm = resolved(AquariumWaterParameter.PHOSPHATE_PPM, entry.phosphatePpm),
            par = resolved(AquariumWaterParameter.PAR, entry.par),
            flow = entry.flow?.trim()?.takeIf(String::isNotBlank),
            warningMode = mode,
            catalogEntryId = entry.id,
            unavailableRequirements = unavailable.toList()
        )
    }

    internal fun parseRange(raw: String?): LivestockParameterRange? =
        (parseRangeResult(raw) as? RangeParseResult.Parsed)?.range

    internal fun parseRangeResult(raw: String?): RangeParseResult {
        val sourceText = raw ?: return RangeParseResult.Missing
        val normalized = sourceText.trim().takeIf(String::isNotBlank)
            ?: return RangeParseResult.Missing
        val prefix = approximation.find(normalized)?.takeIf { it.range.first == 0 }
        val approximate = prefix != null
        val expression = if (prefix == null) normalized else {
            normalized.substring(prefix.range.last + 1).trim()
        }

        fun number(value: String): Double? = value.replace(',', '.').toDoubleOrNull()
            ?.takeIf(Double::isFinite)

        upperBound.matchEntire(expression)?.let { match ->
            val value = number(match.groupValues[2])
                ?: return RangeParseResult.Unparseable(sourceText)
            if (approximate) return RangeParseResult.Unparseable(sourceText)
            return RangeParseResult.Parsed(
                LivestockParameterRange(
                    maximum = value,
                    maximumInclusive = match.groupValues[1] in setOf("<=", "≤"),
                    sourceText = sourceText
                )
            )
        }
        lowerBound.matchEntire(expression)?.let { match ->
            val value = number(match.groupValues[2])
                ?: return RangeParseResult.Unparseable(sourceText)
            if (approximate) return RangeParseResult.Unparseable(sourceText)
            return RangeParseResult.Parsed(
                LivestockParameterRange(
                    minimum = value,
                    minimumInclusive = match.groupValues[1] in setOf(">=", "≥"),
                    sourceText = sourceText
                )
            )
        }
        interval.matchEntire(expression)?.let { match ->
            val minimum = number(match.groupValues[1])
                ?: return RangeParseResult.Unparseable(sourceText)
            val maximum = number(match.groupValues[2])
                ?: return RangeParseResult.Unparseable(sourceText)
            if (minimum > maximum) return RangeParseResult.Unparseable(sourceText)
            return RangeParseResult.Parsed(
                LivestockParameterRange(
                    minimum = minimum,
                    maximum = maximum,
                    approximate = approximate,
                    sourceText = sourceText
                )
            )
        }
        single.matchEntire(expression)?.let { match ->
            val value = number(match.value) ?: return RangeParseResult.Unparseable(sourceText)
            return RangeParseResult.Parsed(
                LivestockParameterRange(
                    approximate = true,
                    nominalValue = value,
                    sourceText = sourceText
                )
            )
        }
        return RangeParseResult.Unparseable(sourceText)
    }
}
