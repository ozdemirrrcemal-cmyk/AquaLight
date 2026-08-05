package com.aqua.aqualight.ui.common.bottomsheet

import org.junit.Assert.assertEquals
import org.junit.Test

class IntegerStepperBottomSheetTest {

    @Test
    fun `restores process state instead of resetting to initial value`() {
        assertEquals(
            64,
            restoreIntegerStepperSelection(
                savedValue = 64,
                initialValue = 60,
                minValue = 50,
                maxValue = 70
            )
        )
    }

    @Test
    fun `clamps restored state to the current editor contract`() {
        assertEquals(
            70,
            restoreIntegerStepperSelection(
                savedValue = 72,
                initialValue = 60,
                minValue = 50,
                maxValue = 70
            )
        )
        assertEquals(
            50,
            restoreIntegerStepperSelection(
                savedValue = null,
                initialValue = 48,
                minValue = 50,
                maxValue = 70
            )
        )
    }
}
