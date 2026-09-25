package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.library

import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryCustomPoint
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLightLibraryCardSummaryTest {

    @Test
    fun customSummaryReportsChannelRangesAndActiveCurveDuration() {
        val payload = DeviceLightLibraryPayload.Custom(
            weekdaysMask = EVERY_DAY_MASK,
            points = listOf(
                point(hour = 0),
                point(hour = 6),
                point(hour = 8, white = 50, red = 70, green = 40, blue = 80),
                point(hour = 12, white = 45, red = 65, green = 35, blue = 75),
                point(hour = 14),
                point(hour = 16),
                point(hour = 18, white = 40, red = 30, green = 70, blue = 20),
                point(hour = 22, white = 40, red = 30, green = 70, blue = 20),
                point(hour = 23)
            )
        )

        assertEquals(0..50, payload.channelRange(DeviceLightLibraryChannel.WHITE))
        assertEquals(0..70, payload.channelRange(DeviceLightLibraryChannel.RED))
        assertEquals(0..70, payload.channelRange(DeviceLightLibraryChannel.GREEN))
        assertEquals(0..80, payload.channelRange(DeviceLightLibraryChannel.BLUE))
        assertEquals(15, payload.activeLightDurationHours())
    }

    @Test
    fun singlePointHasNoMeasurableActiveDuration() {
        val payload = DeviceLightLibraryPayload.Custom(
            weekdaysMask = EVERY_DAY_MASK,
            points = listOf(point(hour = 12, white = 50, red = 20, green = 30, blue = 40))
        )

        assertEquals(0, payload.activeLightDurationHours())
    }

    private fun point(
        hour: Int,
        white: Int = 0,
        red: Int = 0,
        green: Int = 0,
        blue: Int = 0
    ): DeviceLightLibraryCustomPoint = DeviceLightLibraryCustomPoint(
        timeMs = hour * HOUR_MILLIS,
        scene = DeviceLightLibraryScene(
            channels = mapOf(
                DeviceLightLibraryChannel.WHITE to white,
                DeviceLightLibraryChannel.RED to red,
                DeviceLightLibraryChannel.GREEN to green,
                DeviceLightLibraryChannel.BLUE to blue
            )
        )
    )

    private companion object {
        const val EVERY_DAY_MASK = 0x7f
        const val HOUR_MILLIS = 3_600_000L
    }
}
