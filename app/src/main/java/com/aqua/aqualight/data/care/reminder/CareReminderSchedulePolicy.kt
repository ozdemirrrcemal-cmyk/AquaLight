package com.aqua.aqualight.data.care.reminder

import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskStatus
import java.util.concurrent.TimeUnit

/**
 * Pure, deterministic scheduling policy for one persisted care task.
 *
 * The policy never reads process time implicitly and never depends on whether a
 * previous receiver happened to run. This makes boot/package restore produce the
 * same alarm plan as normal in-app scheduling.
 */
internal object CareReminderSchedulePolicy {

    fun plan(
        task: CareTask,
        nowMillis: Long
    ): CareReminderSchedulePlan? {
        require(nowMillis >= 0L) {
            "nowMillis must not be negative"
        }

        if (
            task.status != CareTaskStatus.PENDING ||
            !task.reminderEnabled
        ) {
            return null
        }

        if (task.dueAtMillis > nowMillis) {
            return CareReminderSchedulePlan(
                occurrence = CareReminderOccurrence.DUE,
                triggerAtMillis = task.dueAtMillis
            )
        }

        if (!task.missedReminderEnabled) {
            return null
        }

        val missedAtMillis = runCatching {
            Math.addExact(
                task.dueAtMillis,
                TimeUnit.DAYS.toMillis(task.missedReminderDays.toLong())
            )
        }.getOrNull() ?: return null

        if (missedAtMillis <= nowMillis) {
            return null
        }

        return CareReminderSchedulePlan(
            occurrence = CareReminderOccurrence.MISSED,
            triggerAtMillis = missedAtMillis
        )
    }
}

internal data class CareReminderSchedulePlan(
    val occurrence: CareReminderOccurrence,
    val triggerAtMillis: Long
)

internal enum class CareReminderOccurrence {
    DUE,
    MISSED
}
