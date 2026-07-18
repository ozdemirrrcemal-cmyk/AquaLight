package com.aqua.aqualight.data.care.reminder

import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CareReminderSchedulePolicyTest {

    @Test
    fun futurePendingTaskSchedulesDueOccurrence() {
        val now = 1_000_000L
        val task = task(dueAtMillis = now + 5_000L)

        val plan = CareReminderSchedulePolicy.plan(task, now)

        assertEquals(CareReminderOccurrence.DUE, plan?.occurrence)
        assertEquals(task.dueAtMillis, plan?.triggerAtMillis)
    }

    @Test
    fun pastDueTaskSchedulesDeterministicMissedOccurrence() {
        val now = 10_000_000L
        val dueAt = now - TimeUnit.DAYS.toMillis(1)
        val task = task(
            dueAtMillis = dueAt,
            missedReminderEnabled = true,
            missedReminderDays = 3
        )

        val plan = CareReminderSchedulePolicy.plan(task, now)

        assertEquals(CareReminderOccurrence.MISSED, plan?.occurrence)
        assertEquals(
            dueAt + TimeUnit.DAYS.toMillis(3),
            plan?.triggerAtMillis
        )
    }

    @Test
    fun missedOccurrenceAlreadyPastDoesNotReplayAfterBoot() {
        val now = 20_000_000L
        val dueAt = now - TimeUnit.DAYS.toMillis(5)
        val task = task(
            dueAtMillis = dueAt,
            missedReminderEnabled = true,
            missedReminderDays = 2
        )

        assertNull(CareReminderSchedulePolicy.plan(task, now))
    }

    @Test
    fun completedOrDisabledTaskNeverSchedules() {
        val now = 30_000_000L

        assertNull(
            CareReminderSchedulePolicy.plan(
                task(
                    dueAtMillis = now + 1_000L,
                    status = CareTaskStatus.COMPLETED
                ),
                now
            )
        )
        assertNull(
            CareReminderSchedulePolicy.plan(
                task(
                    dueAtMillis = now + 1_000L,
                    reminderEnabled = false
                ),
                now
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeClockValueFailsClosed() {
        CareReminderSchedulePolicy.plan(
            task(dueAtMillis = 1_000L),
            nowMillis = -1L
        )
    }

    private fun task(
        dueAtMillis: Long,
        status: CareTaskStatus = CareTaskStatus.PENDING,
        reminderEnabled: Boolean = true,
        missedReminderEnabled: Boolean = false,
        missedReminderDays: Int = 1
    ): CareTask {
        return CareTask(
            id = 1L,
            ownerUid = "owner-a",
            tankId = 10L,
            title = "Water change",
            description = "",
            type = CareTaskType.WATER_CHANGE,
            source = CareTaskSource.MANUAL,
            status = status,
            dueAtMillis = dueAtMillis,
            completedAtMillis = null,
            repeatEnabled = false,
            repeatIntervalDays = 1,
            reminderEnabled = reminderEnabled,
            missedReminderEnabled = missedReminderEnabled,
            missedReminderDays = missedReminderDays,
            waterChangePercent = 25,
            note = "",
            generatedRuleKey = "",
            createdAtMillis = 1L,
            updatedAtMillis = 1L
        )
    }
}
