package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.hourly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingHourlyScheduleContractTest {

    @Test
    fun `minute offset round trips through hourly start time`() {
        val startTimeMs = DeviceDosingHourlyScheduleContract.startTimeMs(15)

        assertEquals(900_000L, startTimeMs)
        assertEquals(15, DeviceDosingHourlyScheduleContract.minuteOfHour(startTimeMs))
    }

    @Test
    fun `hourly start time stays inside the first hour and aligns to minutes`() {
        assertTrue(DeviceDosingHourlyScheduleContract.isValidStartTime(0L))
        assertTrue(
            DeviceDosingHourlyScheduleContract.isValidStartTime(
                DeviceDosingHourlyScheduleContract.LAST_MILLISECOND_OF_HOUR
            )
        )
        assertFalse(DeviceDosingHourlyScheduleContract.isValidStartTime(-1L))
        assertFalse(DeviceDosingHourlyScheduleContract.isValidStartTime(3_600_000L))
        assertEquals(
            60_000L,
            DeviceDosingHourlyScheduleContract.minuteAlignedStartTime(119_999L)
        )
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
            DeviceDosingHourlyScheduleContract.startTimeMs(60)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingHourlyScheduleContract.dailyDoseMl(-1L)
        }
    }
}
