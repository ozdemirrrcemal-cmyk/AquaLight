package com.aqua.aqualight.ui.tabs.aquarium.common

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AquariumDimensionInputPolicyTest {

    private val turkish = Locale.forLanguageTag("tr-TR")
    private val english = Locale.ENGLISH

    @Test
    fun turkishAndEnglishValuesRoundTripAcrossCentimetersAndInches() {
        assertEquals("23,62", AquariumDimensionInputPolicy.format(60.0, "in", turkish))
        assertEquals(
            60,
            AquariumDimensionInputPolicy.parseCentimeters("23,62", "in", turkish)
        )
        assertEquals(
            "60",
            AquariumDimensionInputPolicy.convert("23,62", "in", "cm", turkish)
        )

        assertEquals("23.62", AquariumDimensionInputPolicy.format(60.0, "in", english))
        assertEquals(
            60,
            AquariumDimensionInputPolicy.parseCentimeters("23.62", "in", english)
        )
        assertEquals(
            "23.62",
            AquariumDimensionInputPolicy.convert("60", "cm", "in", english)
        )
    }

    @Test
    fun unambiguousAlternateKeyboardSeparatorIsAcceptedAndReformattedForTheAppLanguage() {
        assertEquals(
            "23,62",
            AquariumDimensionInputPolicy.convert("60.00", "cm", "in", turkish)
        )
        assertEquals(
            "23.62",
            AquariumDimensionInputPolicy.convert("60,00", "cm", "in", english)
        )
    }

    @Test
    fun minimumCentimeterRoundTripsThroughDisplayedInches() {
        val inches = AquariumDimensionInputPolicy.format(1.0, "in", turkish)

        assertEquals("0,39", inches)
        assertEquals(1, AquariumDimensionInputPolicy.parseCentimeters(inches, "in", turkish))
    }

    @Test
    fun ambiguousMalformedOrOutOfRangeDimensionsAreRejectedBeforePersistence() {
        assertNull(AquariumDimensionInputPolicy.parseCentimeters("0", "cm", turkish))
        assertNull(AquariumDimensionInputPolicy.parseCentimeters("5000,1", "cm", turkish))
        assertNull(AquariumDimensionInputPolicy.parseCentimeters("1.000,5", "cm", turkish))
        assertNull(AquariumDimensionInputPolicy.parseCentimeters("1.234", "cm", turkish))
        assertNull(AquariumDimensionInputPolicy.parseCentimeters("1,234", "cm", turkish))
        assertNull(AquariumDimensionInputPolicy.parseCentimeters("1.234", "cm", english))
        assertNull(AquariumDimensionInputPolicy.parseCentimeters("1,234", "cm", english))
        assertNull(AquariumDimensionInputPolicy.convert("", "cm", "in", turkish))
    }
}
