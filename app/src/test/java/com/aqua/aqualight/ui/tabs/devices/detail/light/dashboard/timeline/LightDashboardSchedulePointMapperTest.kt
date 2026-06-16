package com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.timeline

import com.aqua.aqualight.data.devices.api.light.LightChannelRole
import com.aqua.aqualight.data.devices.api.light.LightScheduleChannelState
import com.aqua.aqualight.data.devices.api.light.LightSchedulePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightDashboardSchedulePointMapperTest {

    @Test
    fun `linear device lp points are preserved for dashboard graph`() {
        val segments = LightDashboardSchedulePointMapper.mainProgramSegments(
            listOf(
                scheduleChannel(
                    points = listOf(
                        point(8 * 60, 0),
                        point(10 * 60, 80),
                        point(16 * 60, 80),
                        point(18 * 60, 0)
                    )
                )
            )
        )

        val graphPoints = segments.single().runtimePoints

        assertEquals(listOf(8 * 60, 10 * 60, 16 * 60, 18 * 60), graphPoints.map { it.minute })
        assertEquals(listOf(0, 80, 80, 0), graphPoints.map { it.percent })
    }

    @Test
    fun `expanded smooth natural device lp points are not collapsed into a single segment shape`() {
        val lpPoints = listOf(
            point(8 * 60, 0),
            point(8 * 60 + 15, 3),
            point(8 * 60 + 30, 10),
            point(8 * 60 + 45, 22),
            point(9 * 60, 40),
            point(9 * 60 + 15, 62),
            point(9 * 60 + 30, 80),
            point(9 * 60 + 45, 93),
            point(10 * 60, 100),
            point(16 * 60, 100),
            point(16 * 60 + 15, 93),
            point(16 * 60 + 30, 80),
            point(16 * 60 + 45, 62),
            point(17 * 60, 40),
            point(17 * 60 + 15, 22),
            point(17 * 60 + 30, 10),
            point(17 * 60 + 45, 3),
            point(18 * 60, 0)
        )

        val segments = LightDashboardSchedulePointMapper.mainProgramSegments(
            listOf(
                scheduleChannel(points = lpPoints)
            )
        )

        val graphPoints = segments.single().runtimePoints

        assertEquals(lpPoints.map { it.minuteOfDay }, graphPoints.map { it.minute })
        assertEquals(lpPoints.map { it.percent }, graphPoints.map { it.percent })
        assertTrue(graphPoints.size > 4)
    }

    @Test
    fun `timeline mapper forwards runtime lp points to graph state`() {
        val result = LightDashboardTimelineMapper.activeAuto(
            currentTimeMinute = 9 * 60,
            mainSegments = listOf(
                LightDashboardTimelineSegment(
                    id = "device-main-schedule",
                    name = "Auto",
                    startMinute = 8 * 60,
                    peakStartMinute = 10 * 60,
                    peakEndMinute = 16 * 60,
                    endMinute = 18 * 60,
                    outputPercent = 80,
                    runtimePoints = listOf(
                        LightDashboardTimelinePoint(8 * 60, 0),
                        LightDashboardTimelinePoint(10 * 60, 80),
                        LightDashboardTimelinePoint(16 * 60, 80),
                        LightDashboardTimelinePoint(18 * 60, 0)
                    )
                )
            ),
            statusText = "Auto schedule",
            nextEventText = "10:00"
        )

        val graphPoints = result.graphState.segments.single().runtimePoints

        assertEquals(listOf(8 * 60, 10 * 60, 16 * 60, 18 * 60), graphPoints.map { it.minute })
        assertEquals(listOf(0, 80, 80, 0), graphPoints.map { it.percent })
    }

    private fun scheduleChannel(
        points: List<LightSchedulePoint>
    ): LightScheduleChannelState {
        return LightScheduleChannelState(
            index = 1,
            role = LightChannelRole.RED,
            points = points
        )
    }

    private fun point(
        minute: Int,
        percent: Int
    ): LightSchedulePoint {
        return LightSchedulePoint(
            minuteOfDay = minute,
            timeText = "%02d:%02d".format(minute / 60, minute % 60),
            value = percent / 100.0,
            percent = percent
        )
    }
}
