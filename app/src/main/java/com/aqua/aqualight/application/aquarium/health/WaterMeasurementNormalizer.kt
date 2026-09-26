package com.aqua.aqualight.application.aquarium.health

object WaterMeasurementNormalizer {
    private const val NITRATE_N_TO_NO3 = 4.42664
    private const val PPM_CACO3_PER_DEGREE = 17.86

    private data class ConversionKey(
        val parameter: WaterParameter,
        val basis: WaterMeasurementBasis,
        val unit: WaterMeasurementUnit
    )

    private val conversions: Map<ConversionKey, (Double) -> Double> = mapOf(
        ConversionKey(WaterParameter.NITRATE, WaterMeasurementBasis.NO3_N, WaterMeasurementUnit.MG_L) to
            { value -> value * NITRATE_N_TO_NO3 },
        // A bare phosphorus result does not establish reactive orthophosphate.
        // Method-scoped phosphorus conversion belongs in the verified profile layer.
        ConversionKey(WaterParameter.GH, WaterMeasurementBasis.GH, WaterMeasurementUnit.DGH) to
            { value -> value * PPM_CACO3_PER_DEGREE }
    )

    fun canonicalValue(
        parameter: WaterParameter,
        value: Double,
        basis: WaterMeasurementBasis,
        unit: WaterMeasurementUnit
    ): Double? {
        // These legacy slots omit the chemical species, sample matrix or device
        // calibration needed to assign an authoritative canonical meaning.
        if (parameter in UNRESOLVED_LEGACY_PARAMETERS) return null
        return value.takeIf { candidate -> candidate.isFinite() && candidate >= 0.0 }?.let { validValue ->
            val isCanonical =
                basis == WaterParameterDefinitions.canonicalBasis(parameter) &&
                    unit == WaterParameterDefinitions.canonicalUnit(parameter)
            if (isCanonical) {
                validValue
            } else {
                conversions[ConversionKey(parameter, basis, unit)]?.invoke(validValue)
            }
        }
    }

    private val UNRESOLVED_LEGACY_PARAMETERS = setOf(
        WaterParameter.AMMONIA_AMMONIUM,
        WaterParameter.KH,
        WaterParameter.TDS,
        WaterParameter.EC,
        WaterParameter.CO2,
        WaterParameter.IRON,
        WaterParameter.SALINITY,
        WaterParameter.SPECIFIC_GRAVITY
    )
}
