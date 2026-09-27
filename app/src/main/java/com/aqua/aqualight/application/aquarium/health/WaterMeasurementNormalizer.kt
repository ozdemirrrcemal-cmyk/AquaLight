package com.aqua.aqualight.application.aquarium.health

object WaterMeasurementNormalizer {
    private const val PPM_CACO3_PER_DEGREE = 17.86
    private const val MEQ_L_PER_DKH_ALKALINITY = 0.358
    private const val MG_L_CACO3_PER_MEQ_L = 50.0

    private data class ConversionKey(
        val parameter: WaterParameter,
        val basis: WaterMeasurementBasis,
        val unit: WaterMeasurementUnit
    )

    private val conversions: Map<ConversionKey, (Double) -> Double> = mapOf(
        // Nitrogen/phosphorus forms need verified method scope before conversion.
        ConversionKey(WaterParameter.GH, WaterMeasurementBasis.GH, WaterMeasurementUnit.DGH) to
            { value -> value * PPM_CACO3_PER_DEGREE },
        // Only the explicitly typed total-alkalinity result may use these
        // reporting-unit conversions. Historical KH does not acquire this meaning.
        ConversionKey(
            WaterParameter.TOTAL_ALKALINITY,
            WaterMeasurementBasis.TOTAL_ALKALINITY,
            WaterMeasurementUnit.DKH
        ) to { value -> value * MEQ_L_PER_DKH_ALKALINITY },
        ConversionKey(
            WaterParameter.TOTAL_ALKALINITY,
            WaterMeasurementBasis.TOTAL_ALKALINITY,
            WaterMeasurementUnit.PPM_CACO3
        ) to { value -> value / MG_L_CACO3_PER_MEQ_L }
    )

    /** Only an explicitly entered manual basis has a resolved numeric conversion today. */
    fun canonicalValueForStoredSource(
        parameter: WaterParameter,
        value: Double,
        selection: WaterMeasurementSelection
    ): Double? {
        if (selection.method != WaterMeasurementMethod.MANUAL) return null
        return canonicalValue(parameter, value, selection.basis, selection.unit)
    }

    fun supportsCanonicalSource(
        parameter: WaterParameter,
        selection: WaterMeasurementSelection
    ): Boolean {
        if (selection.method != WaterMeasurementMethod.MANUAL || !hasCanonicalSemantics(parameter)) {
            return false
        }
        val isCanonical = selection.basis == WaterParameterDefinitions.canonicalBasis(parameter) &&
            selection.unit == WaterParameterDefinitions.canonicalUnit(parameter)
        return isCanonical || ConversionKey(parameter, selection.basis, selection.unit) in conversions
    }

    fun canonicalValue(
        parameter: WaterParameter,
        value: Double,
        basis: WaterMeasurementBasis,
        unit: WaterMeasurementUnit
    ): Double? {
        // These legacy slots omit the chemical species, sample matrix or device
        // calibration needed to assign an authoritative canonical meaning.
        if (!hasCanonicalSemantics(parameter)) return null
        val converted = value.takeIf { candidate -> candidate.isFinite() && candidate >= 0.0 }
            ?.let { validValue ->
                val isCanonical =
                    basis == WaterParameterDefinitions.canonicalBasis(parameter) &&
                        unit == WaterParameterDefinitions.canonicalUnit(parameter)
                if (isCanonical) {
                    validValue
                } else {
                    conversions[ConversionKey(parameter, basis, unit)]?.invoke(validValue)
                }
            }
        return converted?.takeIf(Double::isFinite)
    }

    fun hasCanonicalSemantics(parameter: WaterParameter): Boolean =
        parameter !in UNRESOLVED_LEGACY_PARAMETERS

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
