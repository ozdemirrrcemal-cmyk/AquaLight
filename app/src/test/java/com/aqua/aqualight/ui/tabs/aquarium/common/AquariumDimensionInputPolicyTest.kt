package com.aqua.aqualight.ui.tabs.aquarium.common

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AquariumDimensionInputPolicyTest {

    @Test
    fun turkishAndEnglishValuesRoundTripAcrossCentimetersAndInches() {
        val turkish = Locale("tr", "TR")

        assertEquals("23,62", AquariumDimensionInputPolicy.format(60.0, "in", turkish))
        assertEquals(
            60,
            AquariumDimensionInputPolicy.parseCentimeters("23,62", "in", turkish)
        )
        assertEquals(
            "60",
            AquariumDimensionInputPolicy.convert("23,62", "in", "cm", turkish)
        )

        assertEquals("23.62", AquariumDimensionInputPolicy.format(60.0, "in", Locale.US))
        assertEquals(
            60,
            AquariumDimensionInputPolicy.parseCentimeters("23.62", "in", Locale.US)
        )
        assertEquals(
            "23.62",
            AquariumDimensionInputPolicy.convert("60", "cm", "in", Locale.US)
        )
    }

    @Test
    fun invalidOrOutOfRangeDimensionsAreRejectedBeforePersistence() {
        val turkish = Locale("tr", "TR")

        assertNull(AquariumDimensionInputPolicy.parseCentimeters("0", "cm", turkish))
        assertNull(AquariumDimensionInputPolicy.parseCentimeters("5000,1", "cm", turkish))
        assertNull(AquariumDimensionInputPolicy.parseCentimeters("1.000,5", "cm", turkish))
        assertNull(AquariumDimensionInputPolicy.convert("", "cm", "in", turkish))
    }
}
