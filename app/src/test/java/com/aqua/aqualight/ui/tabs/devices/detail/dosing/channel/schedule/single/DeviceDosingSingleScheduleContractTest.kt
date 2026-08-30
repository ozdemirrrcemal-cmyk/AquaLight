package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.single

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingSingleScheduleContractTest {

    @Test
    fun `start time round trips through minute of day`() {
        val minutesOfDay = 9 * 60 + 35
        val startTimeMs = DeviceDosingSingleScheduleContract.startTimeMs(minutesOfDay)

        assertEquals(minutesOfDay, DeviceDosingSingleScheduleContract.minutesOfDay(startTimeMs))
        assertEquals(34_500_000L, startTimeMs)
    }

    @Test
    fun `start time is kept inside one wall clock day and aligned to minutes`() {
        assertTrue(DeviceDosingSingleScheduleContract.isValidStartTime(0L))
        assertTrue(
            DeviceDosingSingleScheduleContract.isValidStartTime(
                DeviceDosingSingleScheduleContract.LAST_MILLISECOND_OF_DAY
            )
        )
        assertFalse(DeviceDosingSingleScheduleContract.isValidStartTime(-1L))
        assertFalse(DeviceDosingSingleScheduleContract.isValidStartTime(86_400_000L))
        assertEquals(
            60_000L,
            DeviceDosingSingleScheduleContract.minuteAlignedStartTime(119_999L)
        )
    }

    @Test
    fun `daily microliters map to milliliters without losing precision`() {
        assertEquals(0.0, DeviceDosingSingleScheduleContract.dailyDoseMl(0L), 0.0)
        assertEquals(3.25, DeviceDosingSingleScheduleContract.dailyDoseMl(3_250L), 0.0)
    }

    @Test
    fun `invalid draft values are rejected at the contract boundary`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingSingleScheduleContract.startTimeMs(-1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingSingleScheduleContract.dailyDoseMl(-1L)
        }
    }
}
