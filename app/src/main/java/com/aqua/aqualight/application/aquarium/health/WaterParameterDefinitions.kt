package com.aqua.aqualight.application.aquarium.health

data class WaterParameterDefinition(
    val canonicalBasis: WaterMeasurementBasis,
    val canonicalUnit: WaterMeasurementUnit,
    val basisOptions: List<WaterMeasurementBasis> = listOf(canonicalBasis),
    val unitOptions: List<WaterMeasurementUnit> =
        listOf(canonicalUnit).filterNot { unit -> unit == WaterMeasurementUnit.NONE }
)

object WaterParameterDefinitions {
    private val definitions: Map<WaterParameter, WaterParameterDefinition> = mapOf(
        WaterParameter.PH to WaterParameterDefinition(
            WaterMeasurementBasis.PH,
            WaterMeasurementUnit.NONE
        ),
        WaterParameter.NITRATE to WaterParameterDefinition(
            canonicalBasis = WaterMeasurementBasis.NO3,
            canonicalUnit = WaterMeasurementUnit.MG_L,
            basisOptions = listOf(WaterMeasurementBasis.NO3, WaterMeasurementBasis.NO3_N)
        ),
        WaterParameter.NITRITE to WaterParameterDefinition(
            WaterMeasurementBasis.NO2,
            WaterMeasurementUnit.MG_L
        ),
        WaterParameter.AMMONIA_AMMONIUM to WaterParameterDefinition(
            canonicalBasis = WaterMeasurementBasis.NH3_NH4,
            canonicalUnit = WaterMeasurementUnit.MG_L,
            basisOptions = listOf(
                WaterMeasurementBasis.NH3_NH4,
                WaterMeasurementBasis.TAN
            )
        ),
        WaterParameter.GH to WaterParameterDefinition(
            canonicalBasis = WaterMeasurementBasis.GH,
            canonicalUnit = WaterMeasurementUnit.DGH,
            unitOptions = listOf(
                WaterMeasurementUnit.DGH,
                WaterMeasurementUnit.PPM_CACO3
            )
        ),
        WaterParameter.KH to WaterParameterDefinition(
            canonicalBasis = WaterMeasurementBasis.KH,
            canonicalUnit = WaterMeasurementUnit.DKH,
            unitOptions = listOf(
                WaterMeasurementUnit.DKH,
                WaterMeasurementUnit.MEQ_L,
                WaterMeasurementUnit.PPM_CACO3
            )
        ),
        WaterParameter.PHOSPHATE to WaterParameterDefinition(
            canonicalBasis = WaterMeasurementBasis.PO4,
            canonicalUnit = WaterMeasurementUnit.MG_L,
            basisOptions = listOf(WaterMeasurementBasis.PO4, WaterMeasurementBasis.P)
        ),
        WaterParameter.TDS to WaterParameterDefinition(WaterMeasurementBasis.TDS, WaterMeasurementUnit.PPM),
        WaterParameter.EC to WaterParameterDefinition(WaterMeasurementBasis.EC, WaterMeasurementUnit.US_CM),
        WaterParameter.CO2 to WaterParameterDefinition(WaterMeasurementBasis.CO2, WaterMeasurementUnit.MG_L),
        WaterParameter.IRON to WaterParameterDefinition(WaterMeasurementBasis.FE, WaterMeasurementUnit.MG_L),
        WaterParameter.POTASSIUM to WaterParameterDefinition(WaterMeasurementBasis.K, WaterMeasurementUnit.MG_L),
        WaterParameter.SALINITY to WaterParameterDefinition(WaterMeasurementBasis.SALINITY, WaterMeasurementUnit.PPT),
        WaterParameter.SPECIFIC_GRAVITY to WaterParameterDefinition(
            WaterMeasurementBasis.SG,
            WaterMeasurementUnit.NONE
        ),
        WaterParameter.CALCIUM to WaterParameterDefinition(WaterMeasurementBasis.CA, WaterMeasurementUnit.MG_L),
        WaterParameter.MAGNESIUM to WaterParameterDefinition(WaterMeasurementBasis.MG, WaterMeasurementUnit.MG_L),
        WaterParameter.COPPER to WaterParameterDefinition(WaterMeasurementBasis.CU, WaterMeasurementUnit.MG_L),
        WaterParameter.DISSOLVED_OXYGEN to WaterParameterDefinition(WaterMeasurementBasis.O2, WaterMeasurementUnit.MG_L)
    )

    fun definition(parameter: WaterParameter): WaterParameterDefinition =
        requireNotNull(definitions[parameter]) {
            "Missing water parameter definition for $parameter."
        }

    fun canonicalBasis(parameter: WaterParameter): WaterMeasurementBasis =
        definition(parameter).canonicalBasis

    fun canonicalUnit(parameter: WaterParameter): WaterMeasurementUnit =
        definition(parameter).canonicalUnit

    fun basisOptions(parameter: WaterParameter): List<WaterMeasurementBasis> =
        definition(parameter).basisOptions

    fun unitOptions(parameter: WaterParameter): List<WaterMeasurementUnit> =
        definition(parameter).unitOptions
}
