package com.aqua.aqualight.data.aquarium.catalog.livestock

import com.aqua.aqualight.application.aquarium.LivestockParameterRange
import com.aqua.aqualight.application.aquarium.LivestockWarningMode
import com.aqua.aqualight.application.aquarium.LivestockWaterRequirements
import com.aqua.aqualight.application.aquarium.AquariumWaterParameter
import com.aqua.aqualight.application.aquarium.LivestockRequirementEvidence
import com.aqua.aqualight.application.aquarium.LivestockRequirementParseResult
import java.util.Collections

internal object LivestockWaterRequirementParser {

    private const val NUMBER = "[-+]?\\d+(?:[.,]\\d+)?"
    private val closedRange = Regex("($NUMBER)\\s*[–-]\\s*($NUMBER)")
    private val bound = Regex("(<=|>=|[<>≤≥])\\s*($NUMBER)")
    private val nominal = Regex(NUMBER)
    private val approximation = Regex("^(?:[~≈]\\s*|about\\s+)", RegexOption.IGNORE_CASE)

    fun parse(
        entry: LivestockCatalogEntry
    ): LivestockWaterRequirements {
        return LivestockWaterRequirements(
            temperatureC = parseRange(entry.temperatureC),
            ph = parseRange(entry.ph),
            ghDgh = parseRange(entry.ghDgh),
            khDkh = parseRange(entry.khDkh),
            tdsPpm = parseRange(entry.tdsPpm),
            specificGravity = parseRange(entry.specificGravity),
            alkalinityDkh = parseRange(entry.alkalinityDkh),
            calciumPpm = parseRange(entry.calciumPpm),
            magnesiumPpm = parseRange(entry.magnesiumPpm),
            nitratePpm = parseRange(entry.nitratePpm),
            phosphatePpm = parseRange(entry.phosphatePpm),
            par = parseRange(entry.par),
            flow = entry.flow?.trim()?.takeIf(String::isNotBlank),
            warningMode = LivestockWarningMode.fromCatalogValue(entry.warningMode),
            evidence = LivestockRequirementEvidence(
                catalogId = entry.id,
                catalogRevision = LivestockCatalogRevision.REVISION,
                confidence = entry.confidence,
                rawWarningMode = entry.warningMode,
                parameters = Collections.unmodifiableMap(
                    rawParameters(entry).mapValues { (_, raw) -> parseResult(raw) }
                )
            )
        )
    }

    internal fun parseRange(
        raw: String?
    ): LivestockParameterRange? {
        return (parseResult(raw) as? LivestockRequirementParseResult.Parsed)?.range
    }

    internal fun parseResult(raw: String?): LivestockRequirementParseResult {
        val normalized = raw?.trim()?.takeIf(String::isNotBlank) ?: return LivestockRequirementParseResult.Missing
        val approximate = approximation.containsMatchIn(normalized)
        val expression = normalized.replaceFirst(approximation, "")
        val range = parseExpression(expression, approximate)
        return range?.let { LivestockRequirementParseResult.Parsed(it, requireNotNull(raw)) }
            ?: LivestockRequirementParseResult.Unparseable(requireNotNull(raw))
    }

    private fun parseExpression(expression: String, approximate: Boolean): LivestockParameterRange? {
        val closed = closedRange.matchEntire(expression)
        val singleBound = bound.matchEntire(expression)
        return when {
            closed != null -> parseClosed(closed, approximate)
            singleBound != null -> parseBound(singleBound, approximate)
            else -> nominal.matchEntire(expression)?.value?.number()?.let {
                LivestockParameterRange(nominalTarget = it, approximate = true)
            }
        }
    }

    private fun parseClosed(match: MatchResult, approximate: Boolean): LivestockParameterRange? {
        val lower = match.groupValues[1].number()
        val upper = match.groupValues[2].number()
        return if (lower != null && upper != null && lower <= upper) {
            LivestockParameterRange(lower, upper, approximate)
        } else null
    }

    private fun parseBound(match: MatchResult, approximate: Boolean): LivestockParameterRange? {
        val operator = match.groupValues[1]
        return match.groupValues[2].number()?.let { value ->
            if (operator.startsWith('<') || operator == "≤") {
                LivestockParameterRange(maximum = value, approximate = approximate, maximumInclusive = operator != "<")
            } else {
                LivestockParameterRange(minimum = value, approximate = approximate, minimumInclusive = operator != ">")
            }
        }
    }

    private fun String.number(): Double? = replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)

    private fun rawParameters(entry: LivestockCatalogEntry): Map<AquariumWaterParameter, String?> = mapOf(
        AquariumWaterParameter.TEMPERATURE_C to entry.temperatureC,
        AquariumWaterParameter.PH to entry.ph,
        AquariumWaterParameter.GH_DGH to entry.ghDgh,
        AquariumWaterParameter.KH_DKH to entry.khDkh,
        AquariumWaterParameter.TDS_PPM to entry.tdsPpm,
        AquariumWaterParameter.SPECIFIC_GRAVITY to entry.specificGravity,
        AquariumWaterParameter.ALKALINITY_DKH to entry.alkalinityDkh,
        AquariumWaterParameter.CALCIUM_PPM to entry.calciumPpm,
        AquariumWaterParameter.MAGNESIUM_PPM to entry.magnesiumPpm,
        AquariumWaterParameter.NITRATE_PPM to entry.nitratePpm,
        AquariumWaterParameter.PHOSPHATE_PPM to entry.phosphatePpm,
        AquariumWaterParameter.PAR to entry.par
    )
}
