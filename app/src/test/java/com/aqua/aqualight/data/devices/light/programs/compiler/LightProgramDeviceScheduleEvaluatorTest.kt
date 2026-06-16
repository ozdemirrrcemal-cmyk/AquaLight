package com.aqua.aqualight.data.devices.light.programs.compiler

import com.aqua.aqualight.data.devices.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.data.devices.light.curve.model.LightCurvePoint
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LightProgramDeviceScheduleEvaluatorTest {

    @Test
    fun evaluatesCompiledLinearScheduleWithControllerPointInterpolation() {
        val schedule = LightProgramDevicePointExpander.expand(
            draft = draft(
                transitionMode = LightCurveTransitionMode.LINEAR,
                values = LightCurveChannelValues(
                    red = 20,
                    green = 40,
                    blue = 60,
                    white = 80
                )
            )
        )

        val beforeStart = LightProgramDeviceScheduleEvaluator.outputAtMinute(
            schedule = schedule,
            minuteOfDay = 7 * 60
        )
        val rampMiddle = LightProgramDeviceScheduleEvaluator.outputAtMinute(
            schedule = schedule,
            minuteOfDay = 9 * 60
        )
        val peak = LightProgramDeviceScheduleEvaluator.outputAtMinute(
            schedule = schedule,
            minuteOfDay = 12 * 60
        )
        val afterEnd = LightProgramDeviceScheduleEvaluator.outputAtMinute(
            schedule = schedule,
            minuteOfDay = 19 * 60
        )

        assertEquals(LightCurveChannelValues(red = 0, green = 0, blue = 0, white = 0), beforeStart)
        assertEquals(LightCurveChannelValues(red = 10, green = 20, blue = 30, white = 40), rampMiddle)
        assertEquals(LightCurveChannelValues(red = 20, green = 40, blue = 60, white = 80), peak)
        assertEquals(LightCurveChannelValues(red = 0, green = 0, blue = 0, white = 0), afterEnd)
    }

    private fun draft(
        transitionMode: LightCurveTransitionMode,
        values: LightCurveChannelValues
    ): LightProgramDraft {
        return LightProgramDraft(
            start = LightCurvePoint.of(8, 0),
            peakStart = LightCurvePoint.of(10, 0),
            peakEnd = LightCurvePoint.of(16, 0),
            end = LightCurvePoint.of(18, 0),
            channelValues = values,
            repeatMode = RepeatMode.EVERY,
            selectedDays = setOf(1, 2, 3, 4, 5, 6, 7),
            transitionMode = transitionMode
        )
    }
}
