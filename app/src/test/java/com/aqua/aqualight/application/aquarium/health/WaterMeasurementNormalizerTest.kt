package com.aqua.aqualight.application.aquarium.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaterMeasurementNormalizerTest {

    @Test
    fun nitrateNitrogenConvertsToNitrate() {
        assertEquals(
            44.2664,
            WaterMeasurementNormalizer.canonicalValue(
                parameter = WaterParameter.NITRATE,
                value = 10.0,
                basis = WaterMeasurementBasis.NO3_N,
                unit = WaterMeasurementUnit.MG_L
            ) ?: error("Expected conversion"),
            0.0001
        )
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
    fun carbonateHardnessUnitsConvertToDkh() {
        assertEquals(
            10.0,
            WaterMeasurementNormalizer.canonicalValue(
                parameter = WaterParameter.KH,
                value = 178.48,
                basis = WaterMeasurementBasis.KH,
                unit = WaterMeasurementUnit.PPM_CACO3
            ) ?: error("Expected conversion"),
            0.0001
        )
        assertEquals(
            5.6,
            WaterMeasurementNormalizer.canonicalValue(
                parameter = WaterParameter.KH,
                value = 2.0,
                basis = WaterMeasurementBasis.KH,
                unit = WaterMeasurementUnit.MEQ_L
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
    }
}
