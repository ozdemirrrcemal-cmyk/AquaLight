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
    fun sameTaskIdAcrossOwnersHasDistinctPendingIntentDataEvenIfHashesEverCollide() {
        val ownerA = CareReminderIdentity.stableKey(
            ownerUid = "owner-a",
            taskId = 42L
        )
        val ownerB = CareReminderIdentity.stableKey(
            ownerUid = "owner-b",
            taskId = 42L
        )

        assertNotEquals(ownerA, ownerB)
    }

    @Test
    fun sameOwnerDifferentTasksHaveDistinctPendingIntentData() {
        val firstTask = CareReminderIdentity.stableKey(
            ownerUid = "owner-a",
            taskId = 42L
        )
        val secondTask = CareReminderIdentity.stableKey(
            ownerUid = "owner-a",
            taskId = 43L
        )

        assertNotEquals(firstTask, secondTask)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankOwnerFailsClosed() {
        CareReminderIdentity.stableKey(
            ownerUid = " ",
            taskId = 42L
        )
    }
}
