package com.aqua.aqualight.application.devices.dosing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceDosingChannelOperationsContractTest {

    @Test
    fun `hourly program accepts a full-day firmware start without rounding`() {
        val program = program(
            schedule = DeviceDosingProgramSchedule.Hourly24(
                dailyDoseMicroliters = 24_000L,
                startTimeMillis = 36_900_123L
            )
        )

        assertTrue(program.isValidFor(DeviceDosingSchedulingPolicy()))
        assertFalse(
            program.copy(
                schedule = DeviceDosingProgramSchedule.Hourly24(
                    dailyDoseMicroliters = 24_000L,
                    startTimeMillis = 86_400_000L
                )
            ).isValidFor(DeviceDosingSchedulingPolicy())
        )
    }

    @Test
    fun `firmware scheduling policy bounds custom periods and compiled occurrences`() {
        val twoPeriods = DeviceDosingProgramSchedule.CustomPeriods(
            dailyDoseMicroliters = 10_000L,
            periods = listOf(
                DeviceDosingCustomPeriodDraft(0L, 3_600_000L, 2),
                DeviceDosingCustomPeriodDraft(7_200_000L, 10_800_000L, 2)
            )
        )

        assertFalse(
            program(twoPeriods).isValidFor(
                DeviceDosingSchedulingPolicy(maxCustomPeriodsPerChannel = 1)
            )
        )
        assertFalse(
            program(twoPeriods).isValidFor(
                DeviceDosingSchedulingPolicy(maxEventsPerChannel = 3)
            )
        )
        assertTrue(program(twoPeriods).isValidFor(DeviceDosingSchedulingPolicy()))
    }

    @Test
    fun `disabled program may preserve a valid config with no selected weekdays`() {
        val program = DeviceDosingProgram(
            enabled = false,
            weekdays = List(7) { false },
            schedule = DeviceDosingProgramSchedule.Single(
                dailyDoseMicroliters = 1_000L,
                startTimeMillis = 0L
            ),
            missedDoseRecoveryEnabled = false
        )

        assertTrue(program.isValidFor(DeviceDosingSchedulingPolicy()))
        assertFalse(program.copy(enabled = true).isValidFor(DeviceDosingSchedulingPolicy()))
    }

    @Test
    fun `manual dose obeys exact amount resolution and firmware maximum`() {
        val policy = DeviceDosingSchedulingPolicy(
            amountResolutionMicroliters = 5L,
            maximumManualDoseMicroliters = 1_000L
        )

        assertTrue(policy.acceptsManualDose(995L))
        assertFalse(policy.acceptsManualDose(996L))
        assertFalse(policy.acceptsManualDose(1_005L))
    }

    @Test
    fun `daily manual usage remains independent and total must reconcile exactly`() {
        val usage = DeviceDosingDailyUsageSnapshot(
            valid = true,
            scheduledDeliveredMicroliters = 3_000L,
            manualDeliveredMicroliters = 10_000L,
            totalDeliveredMicroliters = 13_000L
        )

        assertTrue(usage.valid)
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingDailyUsageSnapshot(
                valid = true,
                scheduledDeliveredMicroliters = 3_000L,
                manualDeliveredMicroliters = 10_000L,
                totalDeliveredMicroliters = 3_000L
            )
        }
    }

    @Test
    fun `compiled progress permits at most one physically running occurrence`() {
        val runningOccurrence = DeviceDosingOccurrenceProgress(
            index = 0,
            eventId = 1L,
            programDayOffset = 0,
            timeMillis = 0L,
            amountMicroliters = 1_000L,
            state = DeviceDosingOccurrenceState.RUNNING
        )

        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingChannelProgress(
                scheduledAmountMicroliters = 2_000L,
                occurrences = listOf(
                    runningOccurrence,
                    runningOccurrence.copy(index = 1, eventId = 2L)
                ),
                executionCurrent = true
            )
        }
    }

    @Test
    fun `reservoir settings enforce the application capacity range`() {
        DeviceDosingReservoirSettings(
            trackingEnabled = true,
            capacityMicroliters = 4_294_967_295L,
            lowLevelAlertEnabled = true
        )

        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingReservoirSettings(
                trackingEnabled = true,
                capacityMicroliters = 4_294_967_296L,
                lowLevelAlertEnabled = true
            )
        }
    }

    private fun program(schedule: DeviceDosingProgramSchedule) = DeviceDosingProgram(
        enabled = true,
        weekdays = List(7) { true },
        schedule = schedule,
        missedDoseRecoveryEnabled = true
    )
}
