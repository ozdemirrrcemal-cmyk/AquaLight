package com.aqua.aqualight.data.care.reminder

import com.aqua.aqualight.data.user.UserDataScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CareReminderOwnerIdentityTest {

    @Test
    fun sameOwnerAndTaskProduceStableIdentity() {
        val first = UserDataScope.notificationRequestCode(
            taskId = 42L,
            ownerUid = "owner-a"
        )
        val second = UserDataScope.notificationRequestCode(
            taskId = 42L,
            ownerUid = "owner-a"
        )

        assertEquals(first, second)
    }

    @Test
    fun sameTaskIdAcrossOwnersDoesNotShareAlarmOrNotificationIdentity() {
        val ownerA = UserDataScope.notificationRequestCode(
            taskId = 42L,
            ownerUid = "owner-a"
        )
        val ownerB = UserDataScope.notificationRequestCode(
            taskId = 42L,
            ownerUid = "owner-b"
        )

        assertNotEquals(ownerA, ownerB)
    }

    @Test
    fun sameOwnerDifferentTasksDoNotShareIdentity() {
        val firstTask = UserDataScope.notificationRequestCode(
            taskId = 42L,
            ownerUid = "owner-a"
        )
        val secondTask = UserDataScope.notificationRequestCode(
            taskId = 43L,
            ownerUid = "owner-a"
        )

        assertNotEquals(firstTask, secondTask)
    }
}
