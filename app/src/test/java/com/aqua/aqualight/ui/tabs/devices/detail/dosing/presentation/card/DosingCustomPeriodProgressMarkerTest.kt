package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCustomPeriodDraft
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import org.junit.Assert.assertEquals
import org.junit.Test

class DosingCustomPeriodProgressMarkerTest {

    @Test
    fun `custom period shows cumulative amount at every visible occurrence within marker budget`() {
        val amounts = List(VISIBLE_OCCURRENCE_COUNT) { DOSE_AMOUNT_ML }
        val occurrences = amounts.toPendingOccurrences()

        val markers = customPeriodProgram(doseCount = VISIBLE_OCCURRENCE_COUNT).toProgressMarkers(
            occurrences = occurrences,
            totalAmountMl = amounts.sum()
        )

        assertEquals(amounts.runningCumulativeAmounts(), markers.map { it.cumulativeAmountMl })
        assertEquals(
            occurrences.map { it.endFraction },
            markers.map { it.positionFraction }
        )
    }

    @Test
    fun `custom period keeps shared six marker cap for dense schedules`() {
        val amounts = List(DENSE_OCCURRENCE_COUNT) { DOSE_AMOUNT_ML }
        val markers = customPeriodProgram(doseCount = DENSE_OCCURRENCE_COUNT).toProgressMarkers(
            occurrences = amounts.toPendingOccurrences(),
            totalAmountMl = amounts.sum()
        )

        assertEquals(MAX_PROGRESS_MARKER_COUNT, markers.size)
        assertEquals(amounts.sum(), markers.last().cumulativeAmountMl, AMOUNT_DELTA)
    }

    private fun List<Double>.toPendingOccurrences(): List<DosingProgressOccurrenceUiState> =
        map { amount ->
            DosingProgressOccurrenceUiState(
                amountMl = amount,
                visualState = DosingOccurrenceVisualState.PENDING
            )
        }.withDoseFractions(sum())

    private fun List<Double>.runningCumulativeAmounts(): List<Double> {
        var cumulative = 0.0
        return map { amount ->
            cumulative += amount
            cumulative
        }
    }

    private fun customPeriodProgram(doseCount: Int) = DeviceDosingProgram(
        enabled = true,
        weekdays = List(DOSING_WEEKDAY_COUNT) { true },
        schedule = DeviceDosingProgramSchedule.CustomPeriods(
            dailyDoseMicroliters = doseCount * DOSE_AMOUNT_MICROLITERS,
            periods = listOf(
                DeviceDosingCustomPeriodDraft(
                    startTimeMs = CUSTOM_PERIOD_START_MS,
                    endTimeMs = CUSTOM_PERIOD_END_MS,
                    doseCount = doseCount
                )
            )
        ),
        missedDoseRecoveryEnabled = true
    )

    private companion object {
        const val VISIBLE_OCCURRENCE_COUNT = 3
        const val DENSE_OCCURRENCE_COUNT = 8
        const val MAX_PROGRESS_MARKER_COUNT = 6
        const val DOSING_WEEKDAY_COUNT = 7
        const val DOSE_AMOUNT_ML = 0.25
        const val DOSE_AMOUNT_MICROLITERS = 250L
        const val CUSTOM_PERIOD_START_MS = 75_600_000L
        const val CUSTOM_PERIOD_END_MS = 82_800_000L
        const val AMOUNT_DELTA = 0.0
    }
}
