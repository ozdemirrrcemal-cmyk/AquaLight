package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.hourly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingHourlyScheduleContractTest {

    @Test
    fun `time of day round trips through hourly start time`() {
        val startTimeMs = DeviceDosingHourlyScheduleContract.startTimeMs(10 * 60 + 15)

        assertEquals(36_900_000L, startTimeMs)
        assertEquals(615, DeviceDosingHourlyScheduleContract.minutesOfDay(startTimeMs))
    }

    @Test
    fun `hourly start time accepts the full day without normalizing firmware milliseconds`() {
        assertTrue(DeviceDosingHourlyScheduleContract.isValidStartTime(0L))
        assertTrue(
            DeviceDosingHourlyScheduleContract.isValidStartTime(
                DeviceDosingHourlyScheduleContract.LAST_MILLISECOND_OF_DAY
            )
        )
        assertTrue(DeviceDosingHourlyScheduleContract.isValidStartTime(36_900_123L))
        assertFalse(DeviceDosingHourlyScheduleContract.isValidStartTime(-1L))
        assertFalse(DeviceDosingHourlyScheduleContract.isValidStartTime(86_400_000L))
        assertEquals(615, DeviceDosingHourlyScheduleContract.minutesOfDay(36_900_123L))
    }

    @Test
    fun `last hourly occurrence keeps the firmware program day offset`() {
        val start = DeviceDosingHourlyScheduleContract.startTimeMs(10 * 60 + 15)

        assertEquals(
            DeviceDosingHourlyScheduleContract.startTimeMs(9 * 60 + 15),
            DeviceDosingHourlyScheduleContract.lastDoseTimeMs(start)
        )
        assertTrue(DeviceDosingHourlyScheduleContract.lastDoseFallsOnNextDay(start))
        assertFalse(DeviceDosingHourlyScheduleContract.lastDoseFallsOnNextDay(900_000L))
    }

    @Test
    fun `daily dose is divided into twenty four equal average doses`() {
        assertEquals(3.0, DeviceDosingHourlyScheduleContract.dailyDoseMl(3_000L), 0.0)
        assertEquals(0.125, DeviceDosingHourlyScheduleContract.averageDoseMl(3_000L), 0.0)
        assertEquals(
            3.25 / DeviceDosingHourlyScheduleContract.DOSES_PER_DAY,
            DeviceDosingHourlyScheduleContract.averageDoseMl(3_250L),
            0.0
        )
    }

    @Test
    fun `invalid hourly draft values are rejected at the contract boundary`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingHourlyScheduleContract.startTimeMs(-1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingHourlyScheduleContract.startTimeMs(1_440)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingHourlyScheduleContract.dailyDoseMl(-1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingHourlyScheduleContract.lastDoseTimeMs(
                DeviceDosingHourlyScheduleContract.MILLIS_PER_DAY
            )
        }
    }
}
