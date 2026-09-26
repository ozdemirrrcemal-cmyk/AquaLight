package com.aqua.aqualight.ui.tabs.aquarium.detail.health

internal object WaterAnalysisValueParser {
    private val decimalPattern = Regex("^\\d+(?:[.,]\\d+)?$")

    fun parse(raw: CharSequence): Double? {
        val trimmed = raw.toString().trim()
        if (!decimalPattern.matches(trimmed)) return null
        return trimmed
            .replace(',', '.')
            .toBigDecimalOrNull()
            ?.toDouble()
            ?.takeIf { value -> value.isFinite() && value >= 0.0 }
    }
}
