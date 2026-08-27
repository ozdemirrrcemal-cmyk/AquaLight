package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.hourly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingHourlyScheduleContractTest {

    @Test
    fun `minute of hour accepts only the firmware range`() {
        assertFalse(DeviceDosingHourlyScheduleContract.isValidMinuteOfHour(-1))
        assertTrue(DeviceDosingHourlyScheduleContract.isValidMinuteOfHour(0))
        assertTrue(DeviceDosingHourlyScheduleContract.isValidMinuteOfHour(59))
        assertFalse(DeviceDosingHourlyScheduleContract.isValidMinuteOfHour(60))
    }

    @Test
    fun `hourly occurrences stay on the selected calendar day`() {
        assertEquals(15, DeviceDosingHourlyScheduleContract.firstDoseMinutesOfDay(15))
        assertEquals(23 * 60 + 15, DeviceDosingHourlyScheduleContract.lastDoseMinutesOfDay(15))
        assertEquals(0, DeviceDosingHourlyScheduleContract.firstDoseMinutesOfDay(0))
        assertEquals(23 * 60, DeviceDosingHourlyScheduleContract.lastDoseMinutesOfDay(0))
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
            DeviceDosingHourlyScheduleContract.firstDoseMinutesOfDay(-1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingHourlyScheduleContract.lastDoseMinutesOfDay(60)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingHourlyScheduleContract.dailyDoseMl(-1L)
        }
    }
}
