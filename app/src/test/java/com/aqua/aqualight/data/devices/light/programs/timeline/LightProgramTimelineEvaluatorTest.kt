package com.aqua.aqualight.data.devices.light.programs.timeline

import com.aqua.aqualight.data.devices.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LightProgramTimelineEvaluatorTest {

    @Test
    fun outputAtMinute_usesSharedTimelineCurveCalculation() {
        val timeline = LightProgramTimeline(
            phases = listOf(
                LightProgramTimelinePhase(
                    type = LightProgramPhaseType.MAIN_CURVE,
                    label = "Main Program",
                    startMinute = 8 * 60,
                    peakStartMinute = 10 * 60,
                    peakEndMinute = 16 * 60,
                    endMinute = 18 * 60,
                    channelValues = LightCurveChannelValues(
                        red = 80,
                        green = 60,
                        blue = 40,
                        white = 20
                    ),
                    transitionMode = LightCurveTransitionMode.LINEAR
                )
            )
        )

        val output = LightProgramTimelineEvaluator.outputAtMinute(
            timeline = timeline,
            minute = 10 * 60
        )

        assertEquals(80, output.red)
        assertEquals(60, output.green)
        assertEquals(40, output.blue)
        assertEquals(20, output.white)
    }

    @Test
    fun outputAtMinute_returnsZeroWhenNoPhaseIsActive() {
        val timeline = LightProgramTimeline(
            phases = listOf(
                LightProgramTimelinePhase(
                    type = LightProgramPhaseType.MAIN_CURVE,
                    label = "Main Program",
                    startMinute = 8 * 60,
                    peakStartMinute = 10 * 60,
                    peakEndMinute = 16 * 60,
                    endMinute = 18 * 60,
                    channelValues = LightCurveChannelValues(
                        red = 80,
                        green = 60,
                        blue = 40,
                        white = 20
                    ),
                    transitionMode = LightCurveTransitionMode.LINEAR
                )
            )
        )

        val output = LightProgramTimelineEvaluator.outputAtMinute(
            timeline = timeline,
            minute = 23 * 60
        )

        assertEquals(LightCurveChannelValues(red = 0, green = 0, blue = 0, white = 0), output)
    }
}
