package com.aqua.aqualight.ui.common.bottomsheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedIntStepperStateTest {

    @Test
    fun `increments and decrements by the configured step`() {
        val state = BoundedIntStepperState(
            initialValue = 60,
            minimumValue = 50,
            maximumValue = 70,
            step = 1
        )

        state.increment()
        assertEquals(61, state.value)

        state.decrement()
        assertEquals(60, state.value)
    }

    @Test
    fun `never exceeds the configured boundaries`() {
        val maximum = BoundedIntStepperState(
            initialValue = 70,
            minimumValue = 50,
            maximumValue = 70,
            step = 1
        )
        maximum.increment()
        assertEquals(70, maximum.value)
        assertFalse(maximum.canIncrement)
        assertTrue(maximum.canDecrement)

        val minimum = BoundedIntStepperState(
            initialValue = 50,
            minimumValue = 50,
            maximumValue = 70,
            step = 1
        )
        minimum.decrement()
        assertEquals(50, minimum.value)
        assertFalse(minimum.canDecrement)
        assertTrue(minimum.canIncrement)
    }
}
