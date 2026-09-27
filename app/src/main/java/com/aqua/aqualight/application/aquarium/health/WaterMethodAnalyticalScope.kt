package com.aqua.aqualight.application.aquarium.health

/** Scope is explicit even when this app has no authorized conversion or assessment for it. */
enum class WaterMethodAnalyticalScope {
    NAMED_PARAMETER,
    GENERAL_HARDNESS,
    CARBONATE_HARDNESS,
    TOTAL_ALKALINITY,
    REACTIVE_ORTHOPHOSPHATE,
    TOTAL_IRON,
    DISSOLVED_IRON,
    FERROUS_IRON,
    PRACTICAL_SALINITY_PSS78,
    MASS_FRACTION_SALINITY,
    SPECIFIC_GRAVITY,
    CONDUCTIVITY_IN_SITU,
    CONDUCTIVITY_AT_REFERENCE_TEMPERATURE,
    TDS_GRAVIMETRIC,
    TDS_DEVICE_DERIVED,
    DIRECT_CO2;

    companion object {
        private val scopedParameters = mapOf(
            WaterParameter.GH to setOf(GENERAL_HARDNESS),
            WaterParameter.KH to setOf(CARBONATE_HARDNESS),
            WaterParameter.TOTAL_ALKALINITY to setOf(TOTAL_ALKALINITY),
            WaterParameter.PHOSPHATE to setOf(REACTIVE_ORTHOPHOSPHATE),
            WaterParameter.IRON to setOf(TOTAL_IRON, DISSOLVED_IRON, FERROUS_IRON),
            WaterParameter.SALINITY to setOf(PRACTICAL_SALINITY_PSS78, MASS_FRACTION_SALINITY),
            WaterParameter.SPECIFIC_GRAVITY to setOf(SPECIFIC_GRAVITY),
            WaterParameter.EC to setOf(CONDUCTIVITY_IN_SITU, CONDUCTIVITY_AT_REFERENCE_TEMPERATURE),
            WaterParameter.TDS to setOf(TDS_GRAVIMETRIC, TDS_DEVICE_DERIVED),
            WaterParameter.CO2 to setOf(DIRECT_CO2)
        )

        fun allowedFor(parameter: WaterParameter): Set<WaterMethodAnalyticalScope> =
            scopedParameters[parameter] ?: setOf(NAMED_PARAMETER)
    }
}
