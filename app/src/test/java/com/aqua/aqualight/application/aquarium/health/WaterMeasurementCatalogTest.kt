package com.aqua.aqualight.application.aquarium.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterMeasurementCatalogTest {

    @Test
    fun defaultSelectionNeverAssumesAUserTestKit() {
        val selection = WaterMeasurementCatalog.defaultSelection(WaterParameter.NITRATE)

        assertEquals(WaterMeasurementMethod.MANUAL, selection.method)
        assertEquals(null, selection.testKitId)
        assertEquals(WaterMeasurementBasis.NO3, selection.basis)
        assertEquals(WaterMeasurementUnit.MG_L, selection.unit)
        assertTrue(WaterMeasurementCatalog.isSelectionValid(WaterParameter.NITRATE, selection))
    }

    @Test
    fun testKitMethodRequiresExplicitSupportedKitId() {
        val selection = WaterMeasurementSelection(
            method = WaterMeasurementMethod.TEST_KIT,
            testKitId = null,
            basis = WaterMeasurementBasis.NO3,
            unit = WaterMeasurementUnit.MG_L
        )

        assertFalse(WaterMeasurementCatalog.isSelectionValid(WaterParameter.NITRATE, selection))
    }

    @Test
    fun builtInKitOwnsItsReportedBasisAndUnit() {
        val normalized = WaterMeasurementCatalog.normalizeSelection(
            parameter = WaterParameter.NITRATE,
            selection = WaterMeasurementSelection(
                method = WaterMeasurementMethod.TEST_KIT,
                testKitId = WaterMeasurementCatalog.SALIFERT_NITRATE_TEST_KIT_ID,
                basis = WaterMeasurementBasis.NO3_N,
                unit = WaterMeasurementUnit.MG_L
            )
        )

        assertEquals(WaterMeasurementBasis.NO3, normalized.basis)
        assertEquals(WaterMeasurementUnit.MG_L, normalized.unit)
        assertEquals(
            listOf(WaterMeasurementBasis.NO3),
            WaterMeasurementCatalog.selectableBasisOptions(WaterParameter.NITRATE, normalized)
        )
        assertEquals(
            listOf(WaterMeasurementUnit.MG_L),
            WaterMeasurementCatalog.selectableUnitOptions(WaterParameter.NITRATE, normalized)
        )
    }

    @Test
    fun userDefinedOtherKitMayUseSupportedAlternativeBasis() {
        val selection = WaterMeasurementSelection(
            method = WaterMeasurementMethod.TEST_KIT,
            testKitId = WaterMeasurementCatalog.OTHER_TEST_KIT_ID,
            basis = WaterMeasurementBasis.NO3_N,
            unit = WaterMeasurementUnit.MG_L
        )

        assertTrue(WaterMeasurementCatalog.isSelectionValid(WaterParameter.NITRATE, selection))
    }

    @Test
    fun unitlessParametersUseExplicitNoneUnit() {
        val selection = WaterMeasurementCatalog.defaultSelection(WaterParameter.PH)

        assertEquals(WaterMeasurementUnit.NONE, selection.unit)
        assertTrue(WaterParameterDefinitions.unitOptions(WaterParameter.PH).isEmpty())
    }
}
