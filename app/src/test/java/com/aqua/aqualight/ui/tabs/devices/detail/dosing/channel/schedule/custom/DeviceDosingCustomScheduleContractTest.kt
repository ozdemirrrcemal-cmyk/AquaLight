package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceDosingCustomScheduleContractTest {

    @Test
    fun `draft round trip is canonical and sorted by start time`() {
        val periods = listOf(
            period(startMinute = 600, endMinute = 660, count = 3),
            period(startMinute = 120, endMinute = 180, count = 2)
        )

        val decoded = DeviceDosingCustomScheduleContract.decodeDraft(
            DeviceDosingCustomScheduleContract.encodeDraft(periods)
        )

        assertEquals(listOf(120, 600), decoded?.map { period ->
            DeviceDosingCustomScheduleContract.minutesOfDay(period.startTimeMs)
        })
        assertEquals(5, decoded?.let(DeviceDosingCustomScheduleContract::totalDoseCount))
    }

    @Test
    fun `daily amount is divided across all applications`() {
        val periods = listOf(
            period(startMinute = 120, endMinute = 180, count = 2),
            period(startMinute = 600, endMinute = 660, count = 3)
        )

        assertEquals(
            0.6,
            DeviceDosingCustomScheduleContract.averageDoseMl(3_000L, periods),
            0.0
        )
    }

    @Test
    fun `overlap invalid ranges and firmware dose limits are rejected`() {
        assertEquals(
            DeviceDosingCustomScheduleContract.ValidationError.OVERLAPPING_PERIODS,
            DeviceDosingCustomScheduleContract.validate(
                periods = listOf(
                    period(startMinute = 120, endMinute = 180, count = 2),
                    period(startMinute = 180, endMinute = 240, count = 2)
                ),
                maxPeriods = MAX_PERIODS,
                maxDoseCount = MAX_DOSE_COUNT
            )
        )
        assertEquals(
            DeviceDosingCustomScheduleContract.ValidationError.INVALID_PERIOD,
            DeviceDosingCustomScheduleContract.validate(
                periods = listOf(period(startMinute = 180, endMinute = 120, count = 1)),
                maxPeriods = MAX_PERIODS,
                maxDoseCount = MAX_DOSE_COUNT
            )
        )
        assertEquals(
            DeviceDosingCustomScheduleContract.ValidationError.TOO_MANY_DOSES,
            DeviceDosingCustomScheduleContract.validate(
                periods = listOf(
                    period(startMinute = 120, endMinute = 180, count = 13),
                    period(startMinute = 240, endMinute = 300, count = 12)
                ),
                maxPeriods = MAX_PERIODS,
                maxDoseCount = MAX_DOSE_COUNT
            )
        )
        assertNull(DeviceDosingCustomScheduleContract.decodeDraft("not-a-period"))
    }

    private fun period(startMinute: Int, endMinute: Int, count: Int) =
        DeviceDosingCustomPeriod(
            startTimeMs = DeviceDosingCustomScheduleContract.startTimeMs(startMinute),
            endTimeMs = DeviceDosingCustomScheduleContract.startTimeMs(endMinute),
            doseCount = count
        )

    private companion object {
        const val MAX_PERIODS = 8
        const val MAX_DOSE_COUNT = 24
    }
}
