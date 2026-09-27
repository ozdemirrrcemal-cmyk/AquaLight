package com.aqua.aqualight.application.aquarium.health

/** Product input envelopes, never biological safe ranges. Source-specific limits remain separate. */
object WaterMeasurementInputPolicy {
    private const val MAX_PH = 14.0
    fun accepts(parameter: WaterParameter, value: Double): Boolean =
        value.isFinite() && value >= 0.0 && (parameter != WaterParameter.PH || value <= MAX_PH)

    fun acceptsTemperature(value: Double): Boolean = value.isFinite() &&
        value in WaterAnalysisPolicy.MIN_TEMPERATURE_C..WaterAnalysisPolicy.MAX_TEMPERATURE_C
}
