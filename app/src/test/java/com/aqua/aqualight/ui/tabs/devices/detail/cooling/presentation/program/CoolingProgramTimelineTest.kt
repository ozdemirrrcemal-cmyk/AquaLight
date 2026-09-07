package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.program

import org.junit.Assert.assertEquals
import org.junit.Test

class CoolingProgramTimelineTest {

    @Test
    fun timelineTicksDivideTheDayIntoFourEqualIntervals() {
        assertEquals(
            listOf(0, 6 * 60, 12 * 60, 18 * 60, 24 * 60),
            coolingProgramTimelineTicks().map(CoolingProgramTimelineTick::minutesOfDay)
        )
    }
}
