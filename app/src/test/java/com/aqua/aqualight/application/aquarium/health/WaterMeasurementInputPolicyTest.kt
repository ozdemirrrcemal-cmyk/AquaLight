package com.aqua.aqualight.application.aquarium.health

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterMeasurementInputPolicyTest {
    @Test
    fun `pH product envelope includes both endpoints and never labels dangerous values as safe`() {
        assertTrue(WaterMeasurementInputPolicy.accepts(WaterParameter.PH, 0.0))
        assertTrue(WaterMeasurementInputPolicy.accepts(WaterParameter.PH, 14.0))
        assertFalse(WaterMeasurementInputPolicy.accepts(WaterParameter.PH, Math.nextUp(14.0)))
        assertFalse(WaterMeasurementInputPolicy.accepts(WaterParameter.PH, -0.001))
        assertTrue(WaterMeasurementInputPolicy.accepts(WaterParameter.NITRATE, 1000.0))
    }

    @Test
    fun `every parameter rejects invalid numbers while exact zero remains a value`() {
        WaterParameter.entries.forEach { parameter ->
            assertTrue(WaterMeasurementInputPolicy.accepts(parameter, 0.0))
            listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0).forEach {
                assertFalse(WaterMeasurementInputPolicy.accepts(parameter, it))
            }
        }
        assertTrue(WaterMeasurementInputPolicy.acceptsTemperature(-2.0))
        assertFalse(WaterMeasurementInputPolicy.acceptsTemperature(Double.NaN))
        assertFalse(WaterMeasurementInputPolicy.acceptsTemperature(101.0))
    }
}
