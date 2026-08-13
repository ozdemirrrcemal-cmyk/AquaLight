package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceDosingTimerScheduleContractTest {

    @Test
    fun `timer daily dose is the exact sum of independent entries`() {
        val doses = listOf(
            dose(minutes = 14 * 60, amountMicroliters = 5_000L),
            dose(minutes = 10 * 60, amountMicroliters = 3_000L)
        )

        assertEquals(8_000L, DeviceDosingTimerScheduleContract.totalDoseMicroliters(doses))
        assertEquals(
            listOf(10 * 60, 14 * 60),
            DeviceDosingTimerScheduleContract.decodeDraft(
                DeviceDosingTimerScheduleContract.encodeDraft(doses)
            )?.map { timerDose ->
                DeviceDosingTimerScheduleContract.minutesOfDay(timerDose.startTimeMs)
            }
        )
    }

    @Test
    fun `duplicate clock times are rejected`() {
        assertEquals(
            DeviceDosingTimerScheduleContract.ValidationError.DUPLICATE_TIME,
            DeviceDosingTimerScheduleContract.validate(
                doses = listOf(
                    dose(minutes = 600, amountMicroliters = 1_000L),
                    dose(minutes = 600, amountMicroliters = 2_000L)
                ),
                maxDoseCount = MAX_DOSE_COUNT
            )
        )
    }

    @Test
    fun `timer enforces firmware entry limit and overflow-safe totals`() {
        assertEquals(
            DeviceDosingTimerScheduleContract.ValidationError.TOO_MANY_DOSES,
            DeviceDosingTimerScheduleContract.validate(
                doses = (0..24).map { minute -> dose(minute, 1L) },
                maxDoseCount = MAX_DOSE_COUNT
            )
        )
        assertEquals(
            DeviceDosingTimerScheduleContract.ValidationError.TOTAL_OVERFLOW,
            DeviceDosingTimerScheduleContract.validate(
                doses = listOf(dose(1, Long.MAX_VALUE), dose(2, 1L)),
                maxDoseCount = MAX_DOSE_COUNT
            )
        )
        assertNull(DeviceDosingTimerScheduleContract.decodeDraft("invalid"))
    }

    private fun dose(minutes: Int, amountMicroliters: Long) = DeviceDosingTimerDose(
        startTimeMs = DeviceDosingTimerScheduleContract.startTimeMs(minutes),
        amountMicroliters = amountMicroliters
    )

    private companion object {
        const val MAX_DOSE_COUNT = 24
    }
}
