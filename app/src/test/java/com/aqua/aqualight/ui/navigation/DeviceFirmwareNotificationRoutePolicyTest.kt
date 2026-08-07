package com.aqua.aqualight.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareNotificationRoutePolicyTest {

    @Test
    fun matchingAuthenticatedOwnerCanOpenDeviceUpdate() {
        assertTrue(
            DeviceFirmwareNotificationRoutePolicy.canOpen(
                deviceUid = "device-1",
                notificationOwnerUid = "owner-a",
                activeOwnerUid = "owner-a",
                isAuthenticated = true
            )
        )
    }

    @Test
    fun mismatchedOwnerIsRejected() {
        assertFalse(
            DeviceFirmwareNotificationRoutePolicy.canOpen(
                deviceUid = "device-1",
                notificationOwnerUid = "owner-a",
                activeOwnerUid = "owner-b",
                isAuthenticated = true
            )
        )
    }

    @Test
    fun unauthenticatedOrBlankDeviceIsRejected() {
        assertFalse(
            DeviceFirmwareNotificationRoutePolicy.canOpen(
                deviceUid = "device-1",
                notificationOwnerUid = "owner-a",
                activeOwnerUid = "owner-a",
                isAuthenticated = false
            )
        )
        assertFalse(
            DeviceFirmwareNotificationRoutePolicy.canOpen(
                deviceUid = " ",
                notificationOwnerUid = "owner-a",
                activeOwnerUid = "owner-a",
                isAuthenticated = true
            )
        )
    }
}
