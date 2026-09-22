package com.aqua.aqualight.data.aquarium.catalog.livestock

import com.aqua.aqualight.application.aquarium.LivestockParameterRange
import com.aqua.aqualight.application.aquarium.LivestockWarningMode
import com.aqua.aqualight.application.aquarium.LivestockWaterRequirements

internal object LivestockWaterRequirementParser {

    private val numberPattern = Regex("""[-+]?\d+(?:[.,]\d+)?""")

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
            warningMode = LivestockWarningMode.fromCatalogValue(entry.warningMode)
        )
    }

    internal fun parseRange(
        raw: String?
    ): LivestockParameterRange? {
        val normalized = raw
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return null

        val values = numberPattern.findAll(normalized)
            .map { match ->
                match.value.replace(',', '.').toDoubleOrNull()
            }
            .filterNotNull()
            .toList()

        if (values.isEmpty()) {
            return null
        }

        val approximate = normalized.contains('~') ||
            normalized.contains('≈') ||
            normalized.contains("about", ignoreCase = true)

        return when {
            normalized.startsWith("<") || normalized.startsWith("≤") ->
                LivestockParameterRange(
                    maximum = values.first(),
                    approximate = approximate
                )

            normalized.startsWith(">") || normalized.startsWith("≥") ->
                LivestockParameterRange(
                    minimum = values.first(),
                    approximate = approximate
                )

            values.size >= 2 -> {
                val first = values[0]
                val second = values[1]

                LivestockParameterRange(
                    minimum = minOf(first, second),
                    maximum = maxOf(first, second),
                    approximate = approximate
                )
            }

            else -> LivestockParameterRange(
                minimum = values.first(),
                maximum = values.first(),
                approximate = true
            )
        }
    }
}
