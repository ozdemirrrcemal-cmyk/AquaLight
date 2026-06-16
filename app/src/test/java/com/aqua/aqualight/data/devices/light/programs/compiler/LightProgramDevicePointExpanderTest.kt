package com.aqua.aqualight.data.devices.light.programs.compiler

import com.aqua.aqualight.data.devices.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.data.devices.light.curve.model.LightCurvePoint
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightProgramDevicePointExpanderTest {

    @Test
    fun linearKeepsExactlyFourUserAnchorsForEveryFirmwareChannel() {
        val draft = draft(
            transitionMode = LightCurveTransitionMode.LINEAR,
            channelValues = LightCurveChannelValues(
                white = 60,
                red = 80,
                green = 0,
                blue = 40
            )
        )

        val schedule = LightProgramDevicePointExpander.expand(draft)

        assertEquals(
            LightProgramDeviceTransitionStrategy.EXPANDED_POINTS,
            schedule.strategy
        )
        assertEquals(
            listOf(0, 1, 2, 3),
            schedule.channels.map { channel -> channel.firmwareChannelIndex }
        )
        schedule.channels.forEach { channel ->
            assertEquals(4, channel.points.size)
            assertEquals(listOf(480, 600, 960, 1080), channel.points.map { it.minuteOfDay })
        }

        val whitePoints = schedule.channels
            .first { channel -> channel.channel == LightProgramDeviceChannel.WHITE }
            .points
        val greenPoints = schedule.channels
            .first { channel -> channel.channel == LightProgramDeviceChannel.GREEN }
            .points

        assertEquals(listOf(0, 60, 60, 0), whitePoints.map { it.percent })
        assertEquals(listOf(0, 0, 0, 0), greenPoints.map { it.percent })
    }

    @Test
    fun smoothExpandsActiveChannelsButKeepsInactiveChannelsAsFourClearAnchors() {
        val draft = draft(
            transitionMode = LightCurveTransitionMode.SMOOTH,
            channelValues = LightCurveChannelValues(
                white = 0,
                red = 80,
                green = 0,
                blue = 0
            )
        )

        val schedule = LightProgramDevicePointExpander.expand(draft)
        val red = schedule.channels.first { it.channel == LightProgramDeviceChannel.RED }
        val green = schedule.channels.first { it.channel == LightProgramDeviceChannel.GREEN }

        assertTrue(red.points.size > 4)
        assertEquals(4, green.points.size)
        assertEquals(listOf(0, 0, 0, 0), green.points.map { it.percent })
        assertEquals(480, red.points.first().minuteOfDay)
        assertEquals(1080, red.points.last().minuteOfDay)
        assertEquals(0, red.points.first().percent)
        assertEquals(0, red.points.last().percent)
        assertEquals(80, red.points.maxOf { it.percent })
    }

    @Test
    fun naturalUsesAdaptiveSparseControllerPointsBelowCatalogLimit() {
        val draft = draft(
            transitionMode = LightCurveTransitionMode.NATURAL,
            channelValues = LightCurveChannelValues(
                white = 0,
                red = 100,
                green = 0,
                blue = 0
            )
        )

        val schedule = LightProgramDevicePointExpander.expand(draft)
        val red = schedule.channels.first { it.channel == LightProgramDeviceChannel.RED }

        // Four user anchors plus a small adaptive set of generated samples.
        // The 24-point catalog limit is a hard ceiling, not a target to fill.
        assertEquals(12, red.points.size)
        assertTrue(red.points.size < LightProgramPointExpansionOptions.DEFAULT_MAXIMUM_POINTS_PER_CHANNEL)
        assertEquals(480, red.points.first().minuteOfDay)
        assertEquals(1080, red.points.last().minuteOfDay)
        assertTrue(red.points.any { point -> point.minuteOfDay in 481 until 600 && point.percent in 1 until 100 })
        assertTrue(red.points.any { point -> point.minuteOfDay in 961 until 1080 && point.percent in 1 until 100 })
    }

    @Test
    fun nativeTransitionStrategyKeepsFourAnchorsForFutureFirmware() {
        val draft = draft(
            transitionMode = LightCurveTransitionMode.NATURAL,
            channelValues = LightCurveChannelValues(
                white = 100,
                red = 75,
                green = 50,
                blue = 25
            )
        )

        val schedule = LightProgramDevicePointExpander.expand(
            draft = draft,
            options = LightProgramPointExpansionOptions(
                strategy = LightProgramDeviceTransitionStrategy.NATIVE_TRANSITION
            )
        )

        assertEquals(
            LightProgramDeviceTransitionStrategy.NATIVE_TRANSITION,
            schedule.strategy
        )
        schedule.channels.forEach { channel ->
            assertEquals(4, channel.points.size)
        }
    }

    private fun draft(
        transitionMode: LightCurveTransitionMode,
        channelValues: LightCurveChannelValues
    ): LightProgramDraft {
        return LightProgramDraft(
            start = LightCurvePoint.of(8, 0),
            peakStart = LightCurvePoint.of(10, 0),
            peakEnd = LightCurvePoint.of(16, 0),
            end = LightCurvePoint.of(18, 0),
            channelValues = channelValues,
            repeatMode = RepeatMode.EVERY,
            selectedDays = setOf(1, 2, 3, 4, 5, 6, 7),
            transitionMode = transitionMode
        )
    }
}
