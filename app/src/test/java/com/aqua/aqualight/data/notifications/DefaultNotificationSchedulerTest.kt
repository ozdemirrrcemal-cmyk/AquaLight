package com.aqua.aqualight.data.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultNotificationSchedulerTest {

    @Test
    fun deliveredFinalOccurrenceRemainsVisibleAfterAlarmIsConsumed() {
        assertFalse(
            DefaultNotificationScheduler.shouldCancelVisibleNotification(
                futureAlarmScheduled = false,
                preserveVisibleNotificationWhenConsumed = true
            )
        )
    }

    @Test
    fun ordinaryUnscheduledReminderStillClearsVisibleState() {
        assertTrue(
            DefaultNotificationScheduler.shouldCancelVisibleNotification(
                futureAlarmScheduled = false,
                preserveVisibleNotificationWhenConsumed = false
            )
        )
    }

    @Test
    fun reminderWithFutureOccurrenceRemainsVisible() {
        assertFalse(
            DefaultNotificationScheduler.shouldCancelVisibleNotification(
                futureAlarmScheduled = true,
                preserveVisibleNotificationWhenConsumed = false
            )
        )
    }
}
