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

    private val builtInTestKits = listOf(
        WaterTestKitDefinition(
            id = SALIFERT_NITRATE_TEST_KIT_ID,
            brand = "Salifert",
            name = "Nitrate",
            parameter = WaterParameter.NITRATE,
            basis = WaterMeasurementBasis.NO3,
            unit = WaterMeasurementUnit.MG_L
        )
    )

    fun builtInTestKitsFor(parameter: WaterParameter): List<WaterTestKitDefinition> =
        builtInTestKits.filter { definition -> definition.parameter == parameter }

    fun testKitDefinition(id: String): WaterTestKitDefinition? =
        builtInTestKits.firstOrNull { definition -> definition.id == id }

    fun testKitIdsFor(parameter: WaterParameter): List<String> =
        builtInTestKitsFor(parameter).map { definition -> definition.id } +
            OTHER_TEST_KIT_ID

    fun selectableBasisOptions(
        parameter: WaterParameter,
        selection: WaterMeasurementSelection
    ): List<WaterMeasurementBasis> =
        selectedBuiltInKit(parameter, selection)?.let { listOf(it.basis) }
            ?: WaterParameterDefinitions.basisOptions(parameter)

    fun selectableUnitOptions(
        parameter: WaterParameter,
        selection: WaterMeasurementSelection
    ): List<WaterMeasurementUnit> =
        selectedBuiltInKit(parameter, selection)?.let { listOf(it.unit) }
            ?: WaterParameterDefinitions.unitOptions(parameter)

    fun defaultSelection(parameter: WaterParameter): WaterMeasurementSelection =
        WaterMeasurementSelection(
            method = WaterMeasurementMethod.MANUAL,
            testKitId = null,
            basis = WaterParameterDefinitions.canonicalBasis(parameter),
            unit = if (parameter == WaterParameter.TOTAL_ALKALINITY) {
                WaterMeasurementUnit.DKH
            } else {
                WaterParameterDefinitions.canonicalUnit(parameter)
            }
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
        val kitAdjusted = selection.copy(testKitId = testKitId)
        val builtInKit = selectedBuiltInKit(parameter, kitAdjusted)

        return if (builtInKit != null) {
            kitAdjusted.copy(basis = builtInKit.basis, unit = builtInKit.unit)
        } else {
            kitAdjusted.copy(
                basis = selection.basis.takeIf { candidate ->
                    candidate in WaterParameterDefinitions.basisOptions(parameter)
                } ?: WaterParameterDefinitions.canonicalBasis(parameter),
                unit = selection.unit.takeIf { candidate ->
                    candidate in WaterParameterDefinitions.unitOptions(parameter) ||
                        candidate == WaterParameterDefinitions.canonicalUnit(parameter)
                } ?: WaterParameterDefinitions.canonicalUnit(parameter)
            )
        }
    }

    fun isSelectionValid(
        parameter: WaterParameter,
        selection: WaterMeasurementSelection
    ): Boolean {
        // The current input has no assigned device, sample identity or freshness proof.
        if (selection.method == WaterMeasurementMethod.SENSOR) return false
        return isStoredSelectionValid(parameter, selection)
    }

    /** Structural validation of previously committed raw data, including legacy SENSOR rows. */
    fun isStoredSelectionValid(
        parameter: WaterParameter,
        selection: WaterMeasurementSelection
    ): Boolean {
        val normalized = normalizeSelection(parameter, selection)
        val hasRequiredKit =
            selection.method != WaterMeasurementMethod.TEST_KIT ||
                !selection.testKitId.isNullOrBlank()
        return selection == normalized && hasRequiredKit
    }

    private fun selectedBuiltInKit(
        parameter: WaterParameter,
        selection: WaterMeasurementSelection
    ): WaterTestKitDefinition? =
        selection.testKitId
            ?.takeIf { selection.method == WaterMeasurementMethod.TEST_KIT }
            ?.let(::testKitDefinition)
            ?.takeIf { definition -> definition.parameter == parameter }
}
