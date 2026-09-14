package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLightAutomaticCycleDialTest {

    @Test
    fun clockwiseQuarterTurnsMapToSixHourIntervals() {
        assertEquals(hours(MIDNIGHT_HOUR), automaticCycleTimeFromClockDegrees(TOP_DEGREES, STEP_MS))
        assertEquals(hours(MORNING_HOUR), automaticCycleTimeFromClockDegrees(RIGHT_DEGREES, STEP_MS))
        assertEquals(hours(NOON_HOUR), automaticCycleTimeFromClockDegrees(BOTTOM_DEGREES, STEP_MS))
        assertEquals(hours(EVENING_HOUR), automaticCycleTimeFromClockDegrees(LEFT_DEGREES, STEP_MS))
    }

    @Test
    fun dragTimeSnapsToFirmwareStepAndWrapsAtMidnight() {
        assertEquals(minutes(SNAPPED_MINUTES), snapAutomaticCycleTime(minutes(RAW_MINUTES), STEP_MS))
        assertEquals(hours(MIDNIGHT_HOUR), snapAutomaticCycleTime(minutes(END_OF_DAY_MINUTES), STEP_MS))
    }

    @Test
    fun nearestHandleUsesCircularDistanceAcrossMidnight() {
        assertEquals(
            DeviceLightAutomaticTimeField.START,
            nearestAutomaticCycleField(
                touchedTimeMs = minutes(TOUCH_MINUTES),
                startTimeMs = hours(LATE_START_HOUR),
                endTimeMs = hours(MORNING_HOUR)
            )
        )
    }

    @Test
    fun missingHandlesAreCreatedInStartThenEndOrder() {
        assertEquals(
            DeviceLightAutomaticTimeField.START,
            nearestAutomaticCycleField(
                touchedTimeMs = hours(MORNING_HOUR),
                startTimeMs = null,
                endTimeMs = null
            )
        )
        assertEquals(
            DeviceLightAutomaticTimeField.END,
            nearestAutomaticCycleField(
                touchedTimeMs = hours(EVENING_HOUR),
                startTimeMs = hours(MORNING_HOUR),
                endTimeMs = null
            )
        )
    }
}

private fun hours(value: Int): Long = minutes(value * MINUTES_PER_HOUR)
private fun minutes(value: Int): Long = value * MILLIS_PER_MINUTE

private const val MIDNIGHT_HOUR = 0
private const val MORNING_HOUR = 6
private const val NOON_HOUR = 12
private const val EVENING_HOUR = 18
private const val LATE_START_HOUR = 23
private const val TOP_DEGREES = 0.0
private const val RIGHT_DEGREES = 90.0
private const val BOTTOM_DEGREES = 180.0
private const val LEFT_DEGREES = 270.0
private const val RAW_MINUTES = 487
private const val SNAPPED_MINUTES = 485
private const val END_OF_DAY_MINUTES = 1_439
private const val TOUCH_MINUTES = 5
private const val MINUTES_PER_HOUR = 60
private const val MILLIS_PER_MINUTE = 60_000L
private const val STEP_MS = 5L * MILLIS_PER_MINUTE
