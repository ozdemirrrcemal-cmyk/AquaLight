package com.aqua.aqualight.data.care

import com.aqua.aqualight.application.care.CareTaskInputLimits
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.store.StoreInvariantViolation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CareTaskCommercialLimitContractTest {

    @Test
    fun applicationParserRejectsBlankZeroAndAboveCommercialBounds() {
        assertEquals(1, CareTaskInputLimits.parseRepeatIntervalDays("1"))
        assertEquals(365, CareTaskInputLimits.parseRepeatIntervalDays("365"))
        assertNull(CareTaskInputLimits.parseRepeatIntervalDays(""))
        assertNull(CareTaskInputLimits.parseRepeatIntervalDays("0"))
        assertNull(CareTaskInputLimits.parseRepeatIntervalDays("366"))

        assertEquals(1, CareTaskInputLimits.parseMissedReminderDays("1"))
        assertEquals(30, CareTaskInputLimits.parseMissedReminderDays("30"))
        assertNull(CareTaskInputLimits.parseMissedReminderDays(""))
        assertNull(CareTaskInputLimits.parseMissedReminderDays("0"))
        assertNull(CareTaskInputLimits.parseMissedReminderDays("31"))
    }

    @Test
    fun storeAndApplicationUseTheSameCommercialLimits() {
        assertEquals(
            CareTaskInputLimits.MIN_REPEAT_INTERVAL_DAYS,
            CareTaskStoreRules.MIN_REPEAT_INTERVAL_DAYS
        )
        assertEquals(
            CareTaskInputLimits.MAX_REPEAT_INTERVAL_DAYS,
            CareTaskStoreRules.MAX_REPEAT_INTERVAL_DAYS
        )
        assertEquals(
            CareTaskInputLimits.MIN_MISSED_REMINDER_DAYS,
            CareTaskStoreRules.MIN_MISSED_REMINDER_DAYS
        )
        assertEquals(
            CareTaskInputLimits.MAX_MISSED_REMINDER_DAYS,
            CareTaskStoreRules.MAX_MISSED_REMINDER_DAYS
        )
    }

    @Test
    fun storeAcceptsBoundaryValuesAndRejectsValuesAboveThem() {
        CareTaskStoreRules.validateStoredTask(
            validTask(repeatIntervalDays = 365, missedReminderDays = 30)
        )

        assertThrows(StoreInvariantViolation::class.java) {
            CareTaskStoreRules.validateStoredTask(
                validTask(repeatIntervalDays = 366, missedReminderDays = 30)
            )
        }
        assertThrows(StoreInvariantViolation::class.java) {
            CareTaskStoreRules.validateStoredTask(
                validTask(repeatIntervalDays = 365, missedReminderDays = 31)
            )
        }
    }

    private fun validTask(
        repeatIntervalDays: Int,
        missedReminderDays: Int
    ): StoredCareTask = StoredCareTask.newBuilder()
        .setId(901L)
        .setOwnerUid("owner-a")
        .setTankId(77L)
        .setTitle("Commercial limit test")
        .setDescription("")
        .setType(CareTaskType.CUSTOM.name)
        .setSource(CareTaskSource.MANUAL.name)
        .setStatus(CareTaskStatus.PENDING.name)
        .setDueAtMillis(1_767_312_000_000L)
        .setCompletedAtMillis(0L)
        .setRepeatEnabled(true)
        .setRepeatIntervalDays(repeatIntervalDays)
        .setReminderEnabled(true)
        .setMissedReminderEnabled(true)
        .setMissedReminderDays(missedReminderDays)
        .setWaterChangePercent(0)
        .setNote("")
        .setGeneratedRuleKey("")
        .setCreatedAtMillis(1_767_225_600_000L)
        .setUpdatedAtMillis(1_767_225_600_000L)
        .build()
}
