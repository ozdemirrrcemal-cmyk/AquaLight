package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterMeasurementUiModelsTest {

    @Test
    fun legacyAmbiguousMethodSheetDoesNotAdvertiseCanonicalResult() {
        assertFalse(
            WaterMeasurementCanonicalUi.hasCanonicalSemantics(
                WaterTestParameterId.AMMONIA_AMMONIUM
            )
        )
        assertTrue(WaterMeasurementCanonicalUi.hasCanonicalSemantics(WaterTestParameterId.NITRATE))
    }

    @Test
    fun sensorMethodNeedsAnAssignedVerifiedSampleBeforeSaving() {
        val selection = WaterMeasurementUiCatalog.defaultSelection(WaterTestParameterId.PH)
            .copy(method = WaterMeasurementMethodUi.SENSOR)
        assertFalse(WaterMeasurementUiCatalog.isSelectionValid(WaterTestParameterId.PH, selection))
    }

    @Test
    fun nitrateDefaultDoesNotAssumeAUserTestKit() {
        val selection = WaterMeasurementUiCatalog.defaultSelection(
            WaterTestParameterId.NITRATE
        )

        assertEquals(WaterMeasurementMethodUi.MANUAL, selection.method)
        assertEquals(WaterMeasurementUiCatalog.OPTION_NONE, selection.testKitId)
        assertEquals(WaterMeasurementUiCatalog.BASIS_NO3, selection.basisId)
        assertEquals(WaterMeasurementUiCatalog.UNIT_MG_L, selection.unitId)
        assertTrue(
            WaterMeasurementUiCatalog.isSelectionValid(
                WaterTestParameterId.NITRATE,
                selection
            )
        )
    }

    @Test
    fun testKitMethodRequiresAnExplicitKitSelection() {
        val selection = WaterMeasurementSelectionUi(
            method = WaterMeasurementMethodUi.TEST_KIT,
            testKitId = WaterMeasurementUiCatalog.OPTION_NONE,
            basisId = WaterMeasurementUiCatalog.BASIS_NO3,
            unitId = WaterMeasurementUiCatalog.UNIT_MG_L
        )

        assertFalse(
            WaterMeasurementUiCatalog.isSelectionValid(
                WaterTestParameterId.NITRATE,
                selection
            )
        )
    }

    @Test
    fun knownNitrateKitConstrainsBasisAndUnit() {
        val normalized = WaterMeasurementUiCatalog.normalizeSelection(
            parameterId = WaterTestParameterId.NITRATE,
            selection = WaterMeasurementSelectionUi(
                method = WaterMeasurementMethodUi.TEST_KIT,
                testKitId = WaterMeasurementUiCatalog.KIT_SALIFERT_NITRATE,
                basisId = WaterMeasurementUiCatalog.BASIS_NO3_N,
                unitId = WaterMeasurementUiCatalog.UNIT_PPM
            )
        )

        assertEquals(WaterMeasurementUiCatalog.BASIS_NO3, normalized.basisId)
        assertEquals(WaterMeasurementUiCatalog.UNIT_MG_L, normalized.unitId)
        assertTrue(
            WaterMeasurementUiCatalog.isSelectionValid(
                WaterTestParameterId.NITRATE,
                normalized
            )
        )
    }

    @Test
    fun nonTestKitMethodCannotCarryStaleKitMetadata() {
        val normalized = WaterMeasurementUiCatalog.normalizeSelection(
            parameterId = WaterTestParameterId.NITRATE,
            selection = WaterMeasurementSelectionUi(
                method = WaterMeasurementMethodUi.DIGITAL,
                testKitId = WaterMeasurementUiCatalog.KIT_SALIFERT_NITRATE,
                basisId = WaterMeasurementUiCatalog.BASIS_NO3,
                unitId = WaterMeasurementUiCatalog.UNIT_MG_L
            )
        )

        assertEquals(WaterMeasurementUiCatalog.OPTION_NONE, normalized.testKitId)
    }

    @Test
    fun unitlessPhSelectionRemainsValid() {
        val selection = WaterMeasurementUiCatalog.defaultSelection(
            WaterTestParameterId.PH
        )

        assertEquals(WaterMeasurementUiCatalog.UNIT_NONE, selection.unitId)
        assertTrue(WaterMeasurementUiCatalog.unitOptions(WaterTestParameterId.PH).isEmpty())
        assertTrue(
            WaterMeasurementUiCatalog.isSelectionValid(
                WaterTestParameterId.PH,
                selection
            )
        )
    }
}
