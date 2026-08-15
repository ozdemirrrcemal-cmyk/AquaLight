package com.aqua.aqualight.application.devices.dosing

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingSupplyProjectionPolicyTest {

    @Test
    fun `daily recurrence returns calendar day on which the next dose cannot be covered`() {
        val projection = evaluate(
            remainingMicroliters = 47_000L,
            dailyDoseMicroliters = 7_500L,
            scheduledTodayMicroliters = 7_500L
        )

        assertEquals(6, projection.estimatedRemainingDays)
        assertEquals(DeviceDosingSupplySeverity.CRITICAL, projection.supplySeverity)
    }

    @Test
    fun `selected weekdays are projected as calendar days rather than dose count`() {
        val projection = evaluate(
            remainingMicroliters = 20_000L,
            dailyDoseMicroliters = 10_000L,
            weekdays = listOf(false, true, false, true, false, false, false)
        )

        assertEquals(8, projection.estimatedRemainingDays)
        assertEquals(DeviceDosingSupplySeverity.CRITICAL, projection.supplySeverity)
    }

    @Test
    fun `missing recurrence does not manufacture a remaining day estimate`() {
        val projection = evaluate(
            remainingMicroliters = 20_000L,
            dailyDoseMicroliters = 10_000L,
            weekdays = List(7) { false }
        )

        assertNull(projection.estimatedRemainingDays)
        assertEquals(DeviceDosingSupplySeverity.NORMAL, projection.supplySeverity)
    }

    @Test
    fun `ten and twenty day product boundaries remain exact`() {
        assertProjection(
            remainingMicroliters = 80_000L,
            days = 9,
            severity = DeviceDosingSupplySeverity.CRITICAL
        )
        assertProjection(
            remainingMicroliters = 90_000L,
            days = 10,
            severity = DeviceDosingSupplySeverity.WARNING
        )
        assertProjection(
            remainingMicroliters = 190_000L,
            days = 20,
            severity = DeviceDosingSupplySeverity.WARNING
        )
        assertProjection(
            remainingMicroliters = 200_000L,
            days = 21,
            severity = DeviceDosingSupplySeverity.NORMAL
        )
    }

    @Test
    fun `false to true firmware alarm transition never changes projected supply severity`() {
        val withoutAlarm = channelSnapshot(
            remainingMicroliters = 80_000L,
            lowLevelActive = false
        )
        val withAlarm = withoutAlarm.copy(
            reservoir = withoutAlarm.reservoir.copy(lowLevelActive = true)
        )

        assertFalse(withoutAlarm.reservoir.lowLevelActive)
        assertTrue(withAlarm.reservoir.lowLevelActive)
        assertEquals(
            DeviceDosingSupplyProjectionPolicy.evaluate(withoutAlarm, MONDAY),
            DeviceDosingSupplyProjectionPolicy.evaluate(withAlarm, MONDAY)
        )
    }

    @Test
    fun `firmware alarm may be active while supply projection remains normal`() {
        val snapshot = channelSnapshot(
            remainingMicroliters = 200_000L,
            lowLevelActive = true
        )

        val projection = requireNotNull(
            DeviceDosingSupplyProjectionPolicy.evaluate(snapshot, MONDAY)
        )

        assertTrue(snapshot.reservoir.lowLevelActive)
        assertEquals(21, projection.estimatedRemainingDays)
        assertEquals(DeviceDosingSupplySeverity.NORMAL, projection.supplySeverity)
    }

    @Test
    fun `uncertain accounting suppresses estimate without manufacturing an alarm`() {
        val snapshot = channelSnapshot(
            remainingMicroliters = 80_000L,
            lowLevelActive = false,
            reservoirAccountingCertain = false
        )

        val projection = requireNotNull(
            DeviceDosingSupplyProjectionPolicy.evaluate(snapshot, MONDAY)
        )

        assertFalse(snapshot.reservoir.lowLevelActive)
        assertNull(projection.estimatedRemainingDays)
        assertEquals(DeviceDosingSupplySeverity.UNCERTAIN, projection.supplySeverity)
    }

    @Test
    fun `disabled reservoir tracking publishes no supply projection`() {
        val snapshot = channelSnapshot(remainingMicroliters = 0L).copy(
            reservoir = DeviceDosingReservoirSnapshot()
        )

        assertNull(DeviceDosingSupplyProjectionPolicy.evaluate(snapshot, MONDAY))
    }

    @Test
    fun `timer daily amount is application-owned and sums exact doses`() {
        val program = DeviceDosingProgram(
            enabled = true,
            weekdays = List(7) { true },
            schedule = DeviceDosingProgramSchedule.Timer(
                doses = listOf(
                    DeviceDosingTimerDoseDraft(
                        startTimeMs = 3_600_000L,
                        amountMicroliters = 1_500L
                    ),
                    DeviceDosingTimerDoseDraft(
                        startTimeMs = 7_200_000L,
                        amountMicroliters = 2_500L
                    )
                )
            ),
            missedDoseRecoveryEnabled = true
        )

        assertEquals(4_000L, program.dailyDoseMicroliters())
    }

    private fun assertProjection(
        remainingMicroliters: Long,
        days: Int,
        severity: DeviceDosingSupplySeverity
    ) {
        val projection = evaluate(
            remainingMicroliters = remainingMicroliters,
            dailyDoseMicroliters = 10_000L
        )
        assertEquals(days, projection.estimatedRemainingDays)
        assertEquals(severity, projection.supplySeverity)
    }

    private fun evaluate(
        remainingMicroliters: Long,
        dailyDoseMicroliters: Long,
        scheduledTodayMicroliters: Long = 0L,
        weekdays: List<Boolean> = List(7) { true }
    ): DeviceDosingSupplyProjection = requireNotNull(
        DeviceDosingSupplyProjectionPolicy.evaluate(
            snapshot = channelSnapshot(
                remainingMicroliters = remainingMicroliters,
                dailyDoseMicroliters = dailyDoseMicroliters,
                scheduledTodayMicroliters = scheduledTodayMicroliters,
                weekdays = weekdays
            ),
            today = MONDAY
        )
    )

    private fun channelSnapshot(
        remainingMicroliters: Long,
        dailyDoseMicroliters: Long = 10_000L,
        scheduledTodayMicroliters: Long = 0L,
        weekdays: List<Boolean> = List(7) { true },
        lowLevelActive: Boolean = false,
        reservoirAccountingCertain: Boolean = true
    ) = DeviceDosingChannelSnapshot(
        deviceUid = "device-1",
        slotId = "dosing:channel1",
        pumpCount = 2,
        channelNumber = 1,
        channelTitle = "Trace elements",
        revision = 1L,
        runtimeEnabled = true,
        runtimeReason = DeviceDosingRuntimeReason.NONE,
        deliveryAccountingCertain = true,
        calibrated = true,
        lastCalibratedAtEpochSeconds = 1L,
        scheduling = DeviceDosingSchedulingPolicy(),
        program = DeviceDosingProgram(
            enabled = true,
            weekdays = weekdays,
            schedule = DeviceDosingProgramSchedule.Single(
                dailyDoseMicroliters = dailyDoseMicroliters,
                startTimeMillis = 0L
            ),
            missedDoseRecoveryEnabled = true
        ),
        progress = DeviceDosingChannelProgress(
            scheduledAmountMicroliters = scheduledTodayMicroliters,
            completedAmountMicroliters = 0L,
            executionCurrent = true
        ),
        reservoir = DeviceDosingReservoirSnapshot(
            trackingEnabled = true,
            capacityMicroliters = 1_000_000L,
            remainingMicroliters = remainingMicroliters,
            accountingCertain = reservoirAccountingCertain,
            lowLevelActive = lowLevelActive
        ),
        activeRun = DeviceDosingActiveRun(),
        controls = DeviceDosingChannelControls(),
        usageToday = DeviceDosingDailyUsageSnapshot()
    )

    private companion object {
        val MONDAY: LocalDate = LocalDate.of(2026, 8, 10)
    }
}
