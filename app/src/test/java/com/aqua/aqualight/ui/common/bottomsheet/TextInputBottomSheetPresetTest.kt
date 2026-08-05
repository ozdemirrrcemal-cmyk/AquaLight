package com.aqua.aqualight.ui.common.bottomsheet

import org.junit.Assert.assertEquals
import org.junit.Test

class TextInputBottomSheetPresetTest {

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
}
