package com.aqua.aqualight.ui.tabs.aquarium.detail.health

internal object WaterAnalysisValueParser {
    private val decimalPattern = Regex("^\\d+(?:[.,]\\d+)?$")

    fun parse(raw: CharSequence): Double? {
        val trimmed = raw.toString().trim()
        if (!decimalPattern.matches(trimmed)) return null
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
