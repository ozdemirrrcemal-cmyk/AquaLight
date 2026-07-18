package com.aqua.aqualight.data.notifications

import com.aqua.aqualight.application.notifications.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationIdentityTest {

    @Test
    fun ownerCategoryAndEntityProduceStableDistinctTagsAndRequestCodes() {
        val careA = NotificationIdentity.tag(
            NotificationCategory.CARE_REMINDERS,
            "owner-a",
            "42"
        )
        val careARepeat = NotificationIdentity.tag(
            NotificationCategory.CARE_REMINDERS,
            "owner-a",
            "42"
        )
        val careB = NotificationIdentity.tag(
            NotificationCategory.CARE_REMINDERS,
            "owner-b",
            "42"
        )
        val updateA = NotificationIdentity.tag(
            NotificationCategory.DEVICE_UPDATES,
            "owner-a",
            "42"
        )

        assertEquals(careA, careARepeat)
        assertNotEquals(careA, careB)
        assertNotEquals(careA, updateA)
        assertTrue(careA.startsWith(NotificationIdentity.ownerTagPrefix("owner-a")))
        assertTrue(careB.startsWith(NotificationIdentity.ownerTagPrefix("owner-b")))

        assertEquals(
            NotificationIdentity.requestCode(
                NotificationCategory.CARE_REMINDERS,
                "owner-a",
                "42"
            ),
            NotificationIdentity.requestCode(
                NotificationCategory.CARE_REMINDERS,
                "owner-a",
                "42"
            )
        )
        assertNotEquals(
            NotificationIdentity.requestCode(
                NotificationCategory.CARE_REMINDERS,
                "owner-a",
                "42"
            ),
            NotificationIdentity.requestCode(
                NotificationCategory.DEVICE_UPDATES,
                "owner-a",
                "42"
            )
        )
    }
}
