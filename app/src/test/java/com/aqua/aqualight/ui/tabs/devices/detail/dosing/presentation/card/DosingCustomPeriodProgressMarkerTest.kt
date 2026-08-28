@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCustomPeriodDraft
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import org.junit.Assert.assertEquals
import org.junit.Test

class DosingCustomPeriodProgressMarkerTest {

    @Test
    fun `custom period shows cumulative amount at every visible occurrence within marker budget`() {
        val amounts = listOf(0.25, 0.25, 0.25)
        val occurrences = amounts.map { amount ->
            DosingProgressOccurrenceUiState(
                amountMl = amount,
                visualState = DosingOccurrenceVisualState.PENDING
            )
        }.withDoseFractions(amounts.sum())

        val markers = customPeriodProgram(doseCount = 3).toProgressMarkers(
            occurrences = occurrences,
            totalAmountMl = amounts.sum()
        )

        assertEquals(listOf(0.25, 0.50, 0.75), markers.map { it.cumulativeAmountMl })
        assertEquals(1f / 3f, markers[0].positionFraction, POSITION_DELTA)
        assertEquals(2f / 3f, markers[1].positionFraction, POSITION_DELTA)
        assertEquals(1f, markers[2].positionFraction, POSITION_DELTA)
    }

    @Test
    fun `custom period keeps shared six marker cap for dense schedules`() {
        val amounts = List(8) { 0.25 }
        val occurrences = amounts.map { amount ->
            DosingProgressOccurrenceUiState(
                amountMl = amount,
                visualState = DosingOccurrenceVisualState.PENDING
            )
        }.withDoseFractions(amounts.sum())

        val markers = customPeriodProgram(doseCount = 8).toProgressMarkers(
            occurrences = occurrences,
            totalAmountMl = amounts.sum()
        )

        assertEquals(6, markers.size)
        assertEquals(
            listOf(0.50, 0.75, 1.00, 1.50, 1.75, 2.00),
            markers.map { it.cumulativeAmountMl }
        )
    }

    private fun customPeriodProgram(doseCount: Int) = DeviceDosingProgram(
        enabled = true,
        weekdays = List(7) { true },
        schedule = DeviceDosingProgramSchedule.CustomPeriods(
            dailyDoseMicroliters = doseCount * 250L,
            periods = listOf(
                DeviceDosingCustomPeriodDraft(
                    startTimeMs = 21L * 60L * 60L * 1_000L,
                    endTimeMs = 23L * 60L * 60L * 1_000L,
                    doseCount = doseCount
                )
            )
        ),
        missedDoseRecoveryEnabled = true
    )

    private companion object {
        const val POSITION_DELTA = 0.0001f
    }
}
