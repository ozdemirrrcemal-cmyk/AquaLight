package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DosingWeekday
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingPlanRecurrenceStateTest {

    @Test
    fun `every-day selection owns all seven weekday flags`() {
        val state = DosingPlanRecurrenceState(emptySet()).selectEveryDay()

        assertTrue(state.isEveryDay)
        assertEquals(DOSING_PLAN_WEEKDAYS.toSet(), state.selectedDays)
        assertArrayEquals(BooleanArray(DOSING_PLAN_WEEKDAYS.size) { true }, state.toWeekdayFlags())
    }

    @Test
    fun `changing one weekday clears and restoring it reactivates every day`() {
        val withoutMonday = DosingPlanRecurrenceState().withDaySelection(
            weekday = DosingWeekday.MONDAY,
            selected = false
        )

        assertFalse(withoutMonday.isEveryDay)
        assertFalse(DosingWeekday.MONDAY in withoutMonday.selectedDays)

        val restored = withoutMonday.withDaySelection(
            weekday = DosingWeekday.MONDAY,
            selected = true
        )
        assertTrue(restored.isEveryDay)
    }

    @Test
    fun `weekday flags round trip in firmware order and malformed flags are rejected`() {
        val flags = booleanArrayOf(true, false, true, false, true, false, true)
        val restored = DosingPlanRecurrenceState.fromWeekdayFlags(flags)

        assertArrayEquals(flags, restored?.toWeekdayFlags())
        assertFalse(restored?.isEveryDay ?: true)
        assertNull(DosingPlanRecurrenceState.fromWeekdayFlags(booleanArrayOf(true)))
    }
}
