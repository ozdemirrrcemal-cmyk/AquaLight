package com.aqua.aqualight.data.care.reminder

import com.aqua.aqualight.data.user.UserDataScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CareReminderOwnerIdentityTest {

    @Test
    fun sameOwnerAndTaskProduceStableIdentity() {
        val firstKey = CareReminderIdentity.stableKey(
            ownerUid = "owner-a",
            taskId = 42L
        )
        val secondKey = CareReminderIdentity.stableKey(
            ownerUid = "owner-a",
            taskId = 42L
        )
        val firstRequestCode = UserDataScope.notificationRequestCode(
            taskId = 42L,
            ownerUid = "owner-a"
        )
        val secondRequestCode = UserDataScope.notificationRequestCode(
            taskId = 42L,
            ownerUid = "owner-a"
        )

        assertEquals(firstKey, secondKey)
        assertEquals(firstRequestCode, secondRequestCode)
    }

    @Test
    fun sameTaskIdAcrossOwnersHasDistinctIdentityEvenIfRequestCodeHashesCollide() {
        val ownerA = CareReminderIdentity.stableKey(
            ownerUid = "owner-a",
            taskId = 42L
        )
        val ownerB = CareReminderIdentity.stableKey(
            ownerUid = "owner-b",
            taskId = 42L
        )

        assertNotEquals(ownerA, ownerB)
        assertNotEquals(
            CareReminderIdentity.ownerWorkTag("owner-a"),
            CareReminderIdentity.ownerWorkTag("owner-b")
        )
    }

    @Test
    fun sameOwnerDifferentTasksAndOccurrencesHaveDistinctWorkIdentity() {
        val firstTask = CareReminderIdentity.deliveryWorkName(
            ownerUid = "owner-a",
            taskId = 42L,
            occurrence = CareReminderOccurrence.DUE
        )
        val secondTask = CareReminderIdentity.deliveryWorkName(
            ownerUid = "owner-a",
            taskId = 43L,
            occurrence = CareReminderOccurrence.DUE
        )
        val missedOccurrence = CareReminderIdentity.deliveryWorkName(
            ownerUid = "owner-a",
            taskId = 42L,
            occurrence = CareReminderOccurrence.MISSED
        )

        assertNotEquals(firstTask, secondTask)
        assertNotEquals(firstTask, missedOccurrence)
    }

    @Test
    fun ownerWorkTagAndDeliveryNameAreStable() {
        assertEquals(
            CareReminderIdentity.ownerWorkTag(" owner-a "),
            CareReminderIdentity.ownerWorkTag("owner-a")
        )
        assertEquals(
            CareReminderIdentity.deliveryWorkName(
                ownerUid = "owner-a",
                taskId = 42L,
                occurrence = CareReminderOccurrence.DUE
            ),
            CareReminderIdentity.deliveryWorkName(
                ownerUid = " owner-a ",
                taskId = 42L,
                occurrence = CareReminderOccurrence.DUE
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankOwnerFailsClosed() {
        CareReminderIdentity.stableKey(
            ownerUid = " ",
            taskId = 42L
        )
    }
}
