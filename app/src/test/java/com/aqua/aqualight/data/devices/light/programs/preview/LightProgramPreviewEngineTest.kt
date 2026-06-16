package com.aqua.aqualight.data.devices.light.programs.preview

import com.aqua.aqualight.data.devices.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.data.devices.light.curve.model.LightCurvePoint
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightProgramPreviewEngineTest {

    @Test
    fun previewFrameUsesCompiledScheduleAndMapsElapsedTimeToDayTime() {
        val schedule = LightProgramPreviewEngine.compileSchedule(
            draft = draft(LightCurveTransitionMode.LINEAR)
        )

        val frame = LightProgramPreviewEngine.frameAt(
            schedule = schedule,
            elapsedMillis = 30_000L,
            previewDurationMillis = 60_000L
        )

        assertEquals(50, frame.progressPercent)
        assertEquals(12 * 60, frame.simulatedMinuteOfDay)
        assertEquals(LightCurvePoint.of(12, 0), frame.simulatedTime)
        assertEquals(80, frame.outputValues.white)
    }

    @Test
    fun completedPreviewEndsAtTwentyFourHundredAndZeroOutput() {
        val schedule = LightProgramPreviewEngine.compileSchedule(
            draft = draft(LightCurveTransitionMode.NATURAL)
        )

        val frame = LightProgramPreviewEngine.frameAt(
            schedule = schedule,
            elapsedMillis = 60_000L,
            previewDurationMillis = 60_000L
        )

        assertEquals(100, frame.progressPercent)
        assertEquals(24 * 60, frame.simulatedMinuteOfDay)
        assertEquals("24:00", frame.simulatedTime.label)
        assertEquals(LightCurveChannelValues(red = 0, green = 0, blue = 0, white = 0), frame.outputValues)
        assertTrue(schedule.totalPointCount > 16)
    }

    private fun draft(
        transitionMode: LightCurveTransitionMode
    ): LightProgramDraft {
        return LightProgramDraft(
            start = LightCurvePoint.of(8, 0),
            peakStart = LightCurvePoint.of(10, 0),
            peakEnd = LightCurvePoint.of(16, 0),
            end = LightCurvePoint.of(18, 0),
            channelValues = LightCurveChannelValues(
                red = 0,
                green = 0,
                blue = 0,
                white = 80
            ),
            repeatMode = RepeatMode.EVERY,
            selectedDays = setOf(1, 2, 3, 4, 5, 6, 7),
            transitionMode = transitionMode
        )
    }
}
