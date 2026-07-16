package com.aqua.aqualight.data.care.reminder

import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CareReminderDeliveryPolicyTest {

    @Test
    fun `pending reminder is delivered only for an existing enabled tank`() {
        assertTrue(
            CareReminderDeliveryPolicy.shouldDeliver(
                task = task(),
                tank = tank(careRemindersEnabled = true)
            )
        )
    }

    @Test
    fun `disabled tank suppresses an already scheduled reminder`() {
        assertFalse(
            CareReminderDeliveryPolicy.shouldDeliver(
                task = task(),
                tank = tank(careRemindersEnabled = false)
            )
        )
    }

    @Test
    fun `deleted tank suppresses an already scheduled reminder`() {
        assertFalse(
            CareReminderDeliveryPolicy.shouldDeliver(
                task = task(),
                tank = null
            )
        )
    }

    @Test
    fun `completed or disabled task is never delivered`() {
        assertFalse(
            CareReminderDeliveryPolicy.shouldDeliver(
                task = task(status = CareTaskStatus.COMPLETED),
                tank = tank(careRemindersEnabled = true)
            )
        )
        assertFalse(
            CareReminderDeliveryPolicy.shouldDeliver(
                task = task(reminderEnabled = false),
                tank = tank(careRemindersEnabled = true)
            )
        )
    }

    private fun task(
        status: CareTaskStatus = CareTaskStatus.PENDING,
        reminderEnabled: Boolean = true
    ): CareTask = CareTask(
        id = 7L,
        ownerUid = "owner-a",
        tankId = 11L,
        title = "Water change",
        description = "Replace water",
        type = CareTaskType.WATER_CHANGE,
        source = CareTaskSource.MANUAL,
        status = status,
        dueAtMillis = 1000L,
        completedAtMillis = null,
        repeatEnabled = false,
        repeatIntervalDays = 1,
        reminderEnabled = reminderEnabled,
        missedReminderEnabled = false,
        missedReminderDays = 1,
        waterChangePercent = 25,
        note = "",
        generatedRuleKey = "",
        createdAtMillis = 1L,
        updatedAtMillis = 1L
    )

    private fun tank(careRemindersEnabled: Boolean): SavedAquariumTank =
        SavedAquariumTank(
            id = 11L,
            ownerUid = "owner-a",
            name = "Tank 1",
            description = "",
            photoUri = null,
            setupDateMillis = null,
            widthCm = 60,
            lengthCm = 40,
            heightCm = 40,
            sizeUnit = "cm",
            volumeUnit = "L",
            tankType = "Freshwater",
            tankStyle = "Nature",
            createdAtMillis = 1L,
            smartCareEnabled = true,
            careRemindersEnabled = careRemindersEnabled,
            plants = emptyList(),
            materials = emptyList(),
            livestock = emptyList()
        )
}
