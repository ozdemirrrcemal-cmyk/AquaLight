package com.aqua.aqualight.application.aquarium.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaterMeasurementNormalizerTest {

    @Test
    fun genericNitrateNitrogenCannotBePromotedWithoutVerifiedMethodScope() {
        assertNull(
            WaterMeasurementNormalizer.canonicalValue(
                parameter = WaterParameter.NITRATE,
                value = 10.0,
                basis = WaterMeasurementBasis.NO3_N,
                unit = WaterMeasurementUnit.MG_L
            )
        )
    }

    @Test
    fun unverifiedKitResultStaysSourceNativeEvenWhenItsReportedUnitMatchesCanonical() {
        listOf(
            WaterMeasurementCatalog.OTHER_TEST_KIT_ID,
            WaterMeasurementCatalog.SALIFERT_NITRATE_TEST_KIT_ID
        ).forEach { kitId ->
            assertNull(
                WaterMeasurementNormalizer.canonicalValueForStoredSource(
                    parameter = WaterParameter.NITRATE,
                    value = SAMPLE_NITRATE_MG_L,
                    selection = WaterMeasurementSelection(
                        method = WaterMeasurementMethod.TEST_KIT,
                        testKitId = kitId,
                        basis = WaterMeasurementBasis.NO3,
                        unit = WaterMeasurementUnit.MG_L
                    )
                )
            )
        }
        assertEquals(
            SAMPLE_NITRATE_MG_L,
            WaterMeasurementNormalizer.canonicalValueForStoredSource(
                parameter = WaterParameter.NITRATE,
                value = SAMPLE_NITRATE_MG_L,
                selection = WaterMeasurementCatalog.defaultSelection(WaterParameter.NITRATE)
            ) ?: error("Expected a manually specified NO3 measurement"),
            EXACT_CONVERSION_TOLERANCE
        )
    }

    @Test
    fun uncalibratedDigitalAndLegacySensorResultsRemainSourceNative() {
        listOf(WaterMeasurementMethod.DIGITAL, WaterMeasurementMethod.SENSOR).forEach { method ->
            assertNull(
                WaterMeasurementNormalizer.canonicalValueForStoredSource(
                    parameter = WaterParameter.PH,
                    value = SAMPLE_PH,
                    selection = WaterMeasurementSelection(
                        method = method,
                        testKitId = null,
                        basis = WaterMeasurementBasis.PH,
                        unit = WaterMeasurementUnit.NONE
                    )
                )
            )
        }
    }

    @Test
    fun unspecifiedPhosphorusDoesNotBecomeReactivePhosphate() {
        assertNull(
            WaterMeasurementNormalizer.canonicalValue(
                parameter = WaterParameter.PHOSPHATE,
                value = 1.0,
                basis = WaterMeasurementBasis.P,
                unit = WaterMeasurementUnit.MG_L
            )
        )
    }

    @Test
    fun legacyKhCannotBecomeFreshwaterCarbonateOrMarineAlkalinityWithoutTankContext() {
        assertNull(
            WaterMeasurementNormalizer.canonicalValue(
                parameter = WaterParameter.KH,
                value = 178.6,
                basis = WaterMeasurementBasis.KH,
                unit = WaterMeasurementUnit.PPM_CACO3
            )
        )
        assertNull(
            WaterMeasurementNormalizer.canonicalValue(
                parameter = WaterParameter.KH,
                value = 2.0,
                basis = WaterMeasurementBasis.KH,
                unit = WaterMeasurementUnit.MEQ_L
            )
        )
    }

    @Test
    fun explicitlyTypedTotalAlkalinityUnitsConvertWithoutReinterpretingKhOrBarePpm() {
        assertEquals(
            WaterMeasurementUnit.MEQ_L,
            WaterParameterDefinitions.canonicalUnit(WaterParameter.TOTAL_ALKALINITY)
        )
        assertEquals(
            WaterMeasurementUnit.DKH,
            WaterMeasurementCatalog.defaultSelection(WaterParameter.TOTAL_ALKALINITY).unit
        )
        val basis = WaterMeasurementBasis.TOTAL_ALKALINITY
        assertEquals(3.58, requireNotNull(WaterMeasurementNormalizer.canonicalValue(
            WaterParameter.TOTAL_ALKALINITY, 10.0, basis, WaterMeasurementUnit.DKH
        )), 0.0001)
        assertEquals(2.0, requireNotNull(WaterMeasurementNormalizer.canonicalValue(
            WaterParameter.TOTAL_ALKALINITY, 100.0, basis, WaterMeasurementUnit.PPM_CACO3
        )), 0.0001)
        assertEquals(2.0, requireNotNull(WaterMeasurementNormalizer.canonicalValue(
            WaterParameter.TOTAL_ALKALINITY, 2.0, basis, WaterMeasurementUnit.MEQ_L
        )), 0.0)
        assertNull(WaterMeasurementNormalizer.canonicalValue(
            WaterParameter.TOTAL_ALKALINITY, 100.0, basis, WaterMeasurementUnit.PPM
        ))
        assertNull(WaterMeasurementNormalizer.canonicalValue(
            WaterParameter.TOTAL_ALKALINITY, 10.0,
            WaterMeasurementBasis.KH, WaterMeasurementUnit.DKH
        ))
        assertNull(WaterMeasurementNormalizer.canonicalValueForStoredSource(
            WaterParameter.TOTAL_ALKALINITY,
            10.0,
            WaterMeasurementSelection(
                WaterMeasurementMethod.TEST_KIT,
                WaterMeasurementCatalog.OTHER_TEST_KIT_ID,
                basis,
                WaterMeasurementUnit.DKH
            )
        ))
        assertNull(WaterMeasurementNormalizer.canonicalValue(
            WaterParameter.TOTAL_ALKALINITY,
            Double.MAX_VALUE,
            basis,
            WaterMeasurementUnit.DKH
        ))
    }

    @Test
    fun generalHardnessDegreesConvertToCaco3WithoutChangingRawUnit() {
        assertEquals(WaterMeasurementUnit.PPM_CACO3, WaterParameterDefinitions.canonicalUnit(WaterParameter.GH))
        assertEquals(
            178.6,
            WaterMeasurementNormalizer.canonicalValue(
                parameter = WaterParameter.GH,
                value = 10.0,
                basis = WaterMeasurementBasis.GH,
                unit = WaterMeasurementUnit.DGH
            ) ?: error("Expected conversion"),
            0.0001
        )
    }

    @Test
    fun ambiguousTanIsNotSilentlyConverted() {
        assertNull(
            WaterMeasurementNormalizer.canonicalValue(
                parameter = WaterParameter.AMMONIA_AMMONIUM,
                value = 1.0,
                basis = WaterMeasurementBasis.TAN,
                unit = WaterMeasurementUnit.MG_L
            )
        )
        assertNull(
            WaterMeasurementNormalizer.canonicalValue(
                parameter = WaterParameter.AMMONIA_AMMONIUM,
                value = 1.0,
                basis = WaterMeasurementBasis.NH3_NH4,
                unit = WaterMeasurementUnit.MG_L
            )
        )
    }

    @Test
    fun typedTanAndDirectFreeAmmoniaKeepTheirOwnMassBasis() {
        assertEquals(
            WaterMeasurementBasis.TAN_N,
            WaterParameterDefinitions.canonicalBasis(WaterParameter.TOTAL_AMMONIA_NITROGEN)
        )
        assertEquals(
            WaterMeasurementBasis.FREE_NH3,
            WaterParameterDefinitions.canonicalBasis(WaterParameter.FREE_AMMONIA_NH3)
        )
        assertEquals(
            1.0,
            WaterMeasurementNormalizer.canonicalValue(
                WaterParameter.TOTAL_AMMONIA_NITROGEN,
                1.0,
                WaterMeasurementBasis.TAN_N,
                WaterMeasurementUnit.MG_L
            ) ?: error("Expected typed TAN"),
            0.0
        )
        assertNull(
            WaterMeasurementNormalizer.canonicalValue(
                WaterParameter.FREE_AMMONIA_NH3,
                1.0,
                WaterMeasurementBasis.TAN_N,
                WaterMeasurementUnit.MG_L
            )
        )
    }

    @Test
    fun oldAmmoniaResultHasUnassessedSemanticsWithoutMutatingItsRawValue() {
        val measurement = WaterMeasurementSnapshot(
            resultId = WaterMeasurementResultId(11L, WaterParameter.AMMONIA_AMMONIUM),
            parameter = WaterParameter.AMMONIA_AMMONIUM,
            value = 0.25,
            method = WaterMeasurementMethod.TEST_KIT,
            testKitId = WaterMeasurementCatalog.OTHER_TEST_KIT_ID,
            basis = WaterMeasurementBasis.NH3_NH4,
            unit = WaterMeasurementUnit.MG_L,
            canonicalValue = WaterMeasurementNormalizer.canonicalValue(
                WaterParameter.AMMONIA_AMMONIUM,
                0.25,
                WaterMeasurementBasis.NH3_NH4,
                WaterMeasurementUnit.MG_L
            ),
            canonicalBasis = WaterMeasurementBasis.NH3_NH4,
            canonicalUnit = WaterMeasurementUnit.MG_L
        )

        assertEquals(WaterMeasurementSemanticStatus.LEGACY_UNASSESSED, measurement.semanticStatus)
        assertEquals(0.25, measurement.value, 0.0)
        assertNull(measurement.canonicalValue)
    }

    @Test
    fun bareSalinityIronAndDeviceScalesStaySourceNative() {
        listOf(
            Triple(WaterParameter.SALINITY, WaterMeasurementBasis.SALINITY, WaterMeasurementUnit.PPT),
            Triple(WaterParameter.IRON, WaterMeasurementBasis.FE, WaterMeasurementUnit.MG_L),
            Triple(WaterParameter.SPECIFIC_GRAVITY, WaterMeasurementBasis.SG, WaterMeasurementUnit.NONE),
            Triple(WaterParameter.TDS, WaterMeasurementBasis.TDS, WaterMeasurementUnit.PPM),
            Triple(WaterParameter.EC, WaterMeasurementBasis.EC, WaterMeasurementUnit.US_CM)
        ).forEach { (parameter, basis, unit) ->
            assertNull(WaterMeasurementNormalizer.canonicalValue(parameter, 1.0, basis, unit))
        }
    }

    private companion object {
        const val SAMPLE_NITRATE_MG_L = 10.0
        const val SAMPLE_PH = 7.0
        const val EXACT_CONVERSION_TOLERANCE = 0.0
    }
}
