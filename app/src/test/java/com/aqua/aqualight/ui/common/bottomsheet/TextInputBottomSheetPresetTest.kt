package com.aqua.aqualight.ui.common.bottomsheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextInputBottomSheetPresetTest {

    @Test
    fun `duplicate comparison ignores case whitespace and unicode width`() {
        assertTrue(
            isTextInputValueDisallowed(
                value = "  EVENING   VIEW ",
                disallowedValues = listOf("Evening View")
            )
        )
        assertTrue(
            isTextInputValueDisallowed(
                value = "Ａ",
                disallowedValues = listOf("a")
            )
        )
    }

    @Test
    fun `preset selection publishes the configured reset value`() {
        assertEquals(
            "",
            resolveTextInputResultValue(
                typedValue = "WRGB Pro Elite 120",
                presetSelected = true,
                presetResultValue = ""
            )
        )
    }

    @Test
    fun `manual input publishes the trimmed custom value`() {
        assertEquals(
            "Living room light",
            resolveTextInputResultValue(
                typedValue = "  Living room light  ",
                presetSelected = false,
                presetResultValue = ""
            )
        )
    }

    @Test
    fun `positive numeric validation accepts comma decimal input`() {
        assertTrue(
            isTextInputValueValid(
                value = "2,50",
                required = true,
                minimumNumericValueExclusive = 0.0
            )
        )
    }

    @Test
    fun `positive numeric validation rejects empty zero and non numeric input`() {
        assertFalse(isTextInputValueValid("", required = true, minimumNumericValueExclusive = 0.0))
        assertFalse(isTextInputValueValid("0", required = true, minimumNumericValueExclusive = 0.0))
        assertFalse(isTextInputValueValid("dose", required = true, minimumNumericValueExclusive = 0.0))
    }
}
