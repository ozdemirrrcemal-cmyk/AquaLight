package com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock

data class LivestockParameterRange(
    val minimum: Double? = null,
    val maximum: Double? = null,
    val approximate: Boolean = false
) {
    init {
        require(minimum?.isFinite() != false)
        require(maximum?.isFinite() != false)
        require(minimum == null || maximum == null || minimum <= maximum)
    }

    fun contains(
        value: Double
    ): Boolean {
        if (!value.isFinite()) {
            return false
        }

        return (minimum == null || value >= minimum) &&
            (maximum == null || value <= maximum)
    }
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
            } ?: SOFT
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
    val warningMode: LivestockWarningMode = LivestockWarningMode.SOFT
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
