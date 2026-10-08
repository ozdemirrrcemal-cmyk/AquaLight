package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanPointSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanReason
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceLightPlanPresentationTest {
    @Test
    fun `presentation preserves firmware time and levels without schedule calculation`() {
        val plan = plan(
            points = listOf(
                DeviceLightPlanPointSnapshot(0L, listOf(0, 10, 20)),
                DeviceLightPlanPointSnapshot(43_200_000L, listOf(600, 700, 800))
            )
        )

        val presentation = requireNotNull(plan.toPresentation(CHANNELS))

        assertEquals(43_200_000L, presentation.nowTimeMs)
        assertEquals(1_000, presentation.channelScale)
        assertEquals(listOf(0L, 43_200_000L), presentation.series[0].points.map { it.timeMs })
        assertEquals(listOf(10, 700), presentation.series[1].points.map { it.level })
    }

    @Test
    fun `presentation rejects graph tuples that do not match firmware channels`() {
        val plan = plan(
            points = listOf(DeviceLightPlanPointSnapshot(0L, listOf(0, 10)))
        )

        assertNull(plan.toPresentation(CHANNELS))
    }

    private fun plan(points: List<DeviceLightPlanPointSnapshot>) = DeviceLightPlanSnapshot(
        available = true,
        reason = DeviceLightPlanReason.OK,
        nowTimeMs = 43_200_000L,
        channelScale = 1_000,
        hasScheduleToday = true,
        points = points
    )

    private companion object {
        val CHANNELS = listOf(
            channel("red", 0xFF0000),
            channel("green", 0x00FF00),
            channel("blue", 0x0000FF)
        )

        fun channel(key: String, color: Int) = DeviceLightChannelOutputSnapshot(
            key = key,
            displayName = key,
            displayColorRgb = color,
            effectivePercent = 0
        )
    }
}
