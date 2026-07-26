package com.aqua.aqualight.data.care.reminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CareTaskReminderSchedulerTest {

    @Test
    fun `supported pre Android 12 devices use exact alarms without special access`() {
        assertTrue(CareTaskReminderScheduler.shouldUseExactAlarm(27, false))
        assertTrue(CareTaskReminderScheduler.shouldUseExactAlarm(30, false))
    }

    @Test
    fun `Android 12 and newer require granted precise timing access`() {
        assertFalse(CareTaskReminderScheduler.shouldUseExactAlarm(31, false))
        assertTrue(CareTaskReminderScheduler.shouldUseExactAlarm(31, true))
        assertFalse(CareTaskReminderScheduler.shouldUseExactAlarm(36, false))
        assertTrue(CareTaskReminderScheduler.shouldUseExactAlarm(36, true))
        assertFalse(CareTaskReminderScheduler.shouldUseExactAlarm(37, false))
        assertTrue(CareTaskReminderScheduler.shouldUseExactAlarm(37, true))
    }
}
