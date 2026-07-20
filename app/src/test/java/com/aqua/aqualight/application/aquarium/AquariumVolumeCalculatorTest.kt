package com.aqua.aqualight.application.aquarium

import org.junit.Assert.assertEquals
import org.junit.Test

class AquariumVolumeCalculatorTest {

    @Test
    fun commercialMaximumDimensionsDoNotOverflowIntArithmetic() {
        assertEquals(
            125_000_000.0,
            AquariumVolumeCalculator.grossLiters(5000, 5000, 5000),
            0.0
        )
    }

    @Test
    fun standardVolumeAndGallonConversionRemainDeterministic() {
        val liters = AquariumVolumeCalculator.grossLiters(60, 40, 40)

        assertEquals(96.0, liters, 0.0)
        assertEquals(25.360512, AquariumVolumeCalculator.litersToGallons(liters), 0.000001)
        assertEquals(0.0, AquariumVolumeCalculator.grossLiters(0, 40, 40), 0.0)
    }
}
