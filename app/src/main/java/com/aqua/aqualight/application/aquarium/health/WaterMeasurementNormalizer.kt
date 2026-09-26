package com.aqua.aqualight.application.aquarium.health

object WaterMeasurementNormalizer {
    private const val NITRATE_N_TO_NO3 = 4.42664
    private const val PHOSPHORUS_TO_PO4 = 3.06618
    private const val PPM_CACO3_PER_DEGREE = 17.848
    private const val DKH_PER_MEQ_L = 2.8

    fun canonicalValue(
        parameter: WaterParameter,
        value: Double,
        basis: WaterMeasurementBasis,
        unit: WaterMeasurementUnit
    ): Double? {
        if (!value.isFinite() || value < 0.0) return null

        val canonicalBasis = WaterMeasurementCatalog.canonicalBasis(parameter)
        val canonicalUnit = WaterMeasurementCatalog.canonicalUnit(parameter)
        if (basis == canonicalBasis && unit == canonicalUnit) {
            return value
        }

        return when (parameter) {
            WaterParameter.NITRATE -> if (
                basis == WaterMeasurementBasis.NO3_N &&
                unit == WaterMeasurementUnit.MG_L
            ) {
                value * NITRATE_N_TO_NO3
            } else {
                null
            }

            WaterParameter.PHOSPHATE -> if (
                basis == WaterMeasurementBasis.P &&
                unit == WaterMeasurementUnit.MG_L
            ) {
                value * PHOSPHORUS_TO_PO4
            } else {
                null
            }

            WaterParameter.GH -> when {
                basis != WaterMeasurementBasis.GH -> null
                unit == WaterMeasurementUnit.PPM_CACO3 -> value / PPM_CACO3_PER_DEGREE
                else -> null
            }

            WaterParameter.KH -> when {
                basis != WaterMeasurementBasis.KH -> null
                unit == WaterMeasurementUnit.PPM_CACO3 -> value / PPM_CACO3_PER_DEGREE
                unit == WaterMeasurementUnit.MEQ_L -> value * DKH_PER_MEQ_L
                else -> null
            }

            else -> null
        }
    }
}
