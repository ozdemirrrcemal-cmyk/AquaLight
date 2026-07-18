package com.aqua.aqualight.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CareTaskNotificationRoutePolicyTest {

    @Test
    fun matchingAuthenticatedOwnerCanOpenTask() {
        assertTrue(
            CareTaskNotificationRoutePolicy.canOpen(
                taskId = 42L,
                notificationOwnerUid = "owner-a",
                activeOwnerUid = "owner-a",
                isAuthenticated = true
            )
        )
    }

    @Test
    fun blankOrDifferentOwnerFailsClosed() {
        assertFalse(
            CareTaskNotificationRoutePolicy.canOpen(
                taskId = 42L,
                notificationOwnerUid = "",
                activeOwnerUid = "owner-a",
                isAuthenticated = true
            )
        )
        assertFalse(
            CareTaskNotificationRoutePolicy.canOpen(
                taskId = 42L,
                notificationOwnerUid = "owner-b",
                activeOwnerUid = "owner-a",
                isAuthenticated = true
            )
        )
    }

    @Test
    fun unauthenticatedOrInvalidTaskFailsClosed() {
        assertFalse(
            CareTaskNotificationRoutePolicy.canOpen(
                taskId = 42L,
                notificationOwnerUid = "owner-a",
                activeOwnerUid = "owner-a",
                isAuthenticated = false
            )
        )
        assertFalse(
            CareTaskNotificationRoutePolicy.canOpen(
                taskId = -1L,
                notificationOwnerUid = "owner-a",
                activeOwnerUid = "owner-a",
                isAuthenticated = true
            )
        )
    }
}
