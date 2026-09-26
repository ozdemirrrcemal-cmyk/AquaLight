package com.aqua.aqualight.application.aquarium.health

data class WaterTestKitDefinition(
    val id: String,
    val brand: String,
    val name: String,
    val parameter: WaterParameter,
    val basis: WaterMeasurementBasis,
    val unit: WaterMeasurementUnit
)

object WaterMeasurementCatalog {
    const val OTHER_TEST_KIT_ID = "other"
    const val SALIFERT_NITRATE_TEST_KIT_ID = "salifert_nitrate"

    private val salifertNitrate = WaterTestKitDefinition(
        id = SALIFERT_NITRATE_TEST_KIT_ID,
        brand = "Salifert",
        name = "Nitrate",
        parameter = WaterParameter.NITRATE,
        basis = WaterMeasurementBasis.NO3,
        unit = WaterMeasurementUnit.MG_L
    )

    private val builtInTestKits = listOf(salifertNitrate)

    fun builtInTestKitsFor(parameter: WaterParameter): List<WaterTestKitDefinition> =
        builtInTestKits.filter { definition -> definition.parameter == parameter }

    fun testKitDefinition(id: String): WaterTestKitDefinition? =
        builtInTestKits.firstOrNull { definition -> definition.id == id }

    fun testKitIdsFor(parameter: WaterParameter): List<String> =
        builtInTestKitsFor(parameter).map { definition -> definition.id } +
            OTHER_TEST_KIT_ID

    fun canonicalBasis(parameter: WaterParameter): WaterMeasurementBasis =
        when (parameter) {
            WaterParameter.PH -> WaterMeasurementBasis.PH
            WaterParameter.NITRATE -> WaterMeasurementBasis.NO3
            WaterParameter.NITRITE -> WaterMeasurementBasis.NO2
            WaterParameter.AMMONIA_AMMONIUM -> WaterMeasurementBasis.NH3_NH4
            WaterParameter.GH -> WaterMeasurementBasis.GH
            WaterParameter.KH -> WaterMeasurementBasis.KH
            WaterParameter.PHOSPHATE -> WaterMeasurementBasis.PO4
            WaterParameter.TDS -> WaterMeasurementBasis.TDS
            WaterParameter.EC -> WaterMeasurementBasis.EC
            WaterParameter.CO2 -> WaterMeasurementBasis.CO2
            WaterParameter.IRON -> WaterMeasurementBasis.FE
            WaterParameter.POTASSIUM -> WaterMeasurementBasis.K
            WaterParameter.SALINITY -> WaterMeasurementBasis.SALINITY
            WaterParameter.SPECIFIC_GRAVITY -> WaterMeasurementBasis.SG
            WaterParameter.CALCIUM -> WaterMeasurementBasis.CA
            WaterParameter.MAGNESIUM -> WaterMeasurementBasis.MG
            WaterParameter.COPPER -> WaterMeasurementBasis.CU
            WaterParameter.DISSOLVED_OXYGEN -> WaterMeasurementBasis.O2
        }

    fun canonicalUnit(parameter: WaterParameter): WaterMeasurementUnit =
        when (parameter) {
            WaterParameter.PH,
            WaterParameter.SPECIFIC_GRAVITY -> WaterMeasurementUnit.NONE

            WaterParameter.GH -> WaterMeasurementUnit.DGH
            WaterParameter.KH -> WaterMeasurementUnit.DKH
            WaterParameter.TDS -> WaterMeasurementUnit.PPM
            WaterParameter.EC -> WaterMeasurementUnit.US_CM
            WaterParameter.SALINITY -> WaterMeasurementUnit.PPT
            else -> WaterMeasurementUnit.MG_L
        }

    fun basisOptions(parameter: WaterParameter): List<WaterMeasurementBasis> =
        when (parameter) {
            WaterParameter.NITRATE -> listOf(
                WaterMeasurementBasis.NO3,
                WaterMeasurementBasis.NO3_N
            )
            WaterParameter.PHOSPHATE -> listOf(
                WaterMeasurementBasis.PO4,
                WaterMeasurementBasis.P
            )
            WaterParameter.AMMONIA_AMMONIUM -> listOf(
                WaterMeasurementBasis.NH3_NH4,
                WaterMeasurementBasis.TAN
            )
            else -> listOf(canonicalBasis(parameter))
        }

    fun unitOptions(parameter: WaterParameter): List<WaterMeasurementUnit> =
        when (parameter) {
            WaterParameter.GH -> listOf(
                WaterMeasurementUnit.DGH,
                WaterMeasurementUnit.PPM_CACO3
            )
            WaterParameter.KH -> listOf(
                WaterMeasurementUnit.DKH,
                WaterMeasurementUnit.MEQ_L,
                WaterMeasurementUnit.PPM_CACO3
            )
            else -> listOf(canonicalUnit(parameter))
                .filterNot { unit -> unit == WaterMeasurementUnit.NONE }
        }

    fun defaultSelection(parameter: WaterParameter): WaterMeasurementSelection =
        WaterMeasurementSelection(
            method = WaterMeasurementMethod.MANUAL,
            testKitId = null,
            basis = canonicalBasis(parameter),
            unit = canonicalUnit(parameter)
        )

    fun normalizeSelection(
        parameter: WaterParameter,
        selection: WaterMeasurementSelection
    ): WaterMeasurementSelection {
        val normalizedKitId = selection.testKitId?.trim()?.takeIf(String::isNotBlank)
        val testKitId = if (selection.method == WaterMeasurementMethod.TEST_KIT) {
            normalizedKitId?.takeIf { id -> id in testKitIdsFor(parameter) }
        } else {
            null
        }

        val builtInKit = testKitId?.let(::testKitDefinition)
        if (builtInKit != null && builtInKit.parameter == parameter) {
            return selection.copy(
                testKitId = builtInKit.id,
                basis = builtInKit.basis,
                unit = builtInKit.unit
            )
        }

        val basis = selection.basis.takeIf { candidate ->
            candidate in basisOptions(parameter)
        } ?: canonicalBasis(parameter)
        val unit = selection.unit.takeIf { candidate ->
            candidate in unitOptions(parameter) ||
                candidate == canonicalUnit(parameter)
        } ?: canonicalUnit(parameter)

        return selection.copy(
            testKitId = testKitId,
            basis = basis,
            unit = unit
        )
    }

    fun isSelectionValid(
        parameter: WaterParameter,
        selection: WaterMeasurementSelection
    ): Boolean {
        val normalized = normalizeSelection(parameter, selection)
        val hasRequiredKit =
            selection.method != WaterMeasurementMethod.TEST_KIT ||
                !selection.testKitId.isNullOrBlank()
        return selection == normalized && hasRequiredKit
    }
}
