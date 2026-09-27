package com.aqua.aqualight.ui.tabs.aquarium.detail.health

internal object WaterAnalysisValueParser {
    private val decimalPattern = Regex("^\\d+(?:[.,]\\d+)?$")

    private const val MAX_INPUT_CHARS = 64

    fun parseTemperature(raw: CharSequence): Double? {
        val trimmed = raw.toString().trim()
        return if (trimmed.startsWith('-')) parse(trimmed.drop(1))?.unaryMinus() else parse(trimmed)
    }

    fun parse(raw: CharSequence): Double? {
        val trimmed = raw.toString().trim()
        if (trimmed.length > MAX_INPUT_CHARS || !decimalPattern.matches(trimmed)) return null
        return trimmed
            .replace(',', '.')
            .toBigDecimalOrNull()
            ?.let { decimal ->
                val value = decimal.toDouble()
                value.takeIf { candidate ->
                    candidate.isFinite() && candidate >= 0.0 &&
                        (candidate != 0.0 || decimal.signum() == 0)
                }
            }
    }
}
