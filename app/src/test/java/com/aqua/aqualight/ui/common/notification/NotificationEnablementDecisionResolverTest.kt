package com.aqua.aqualight.ui.common.notification

import com.aqua.aqualight.application.notifications.NotificationChannelState
import com.aqua.aqualight.application.notifications.NotificationDeliveryReadiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationEnablementDecisionResolverTest {

    @Test
    fun `runtime permission is repaired before every other block`() {
        assertEquals(
            NotificationEnablementStep.REQUEST_RUNTIME_PERMISSION,
            resolve(
                runtimePermissionGranted = false,
                appNotificationsEnabled = false,
                channelState = NotificationChannelState.BLOCKED,
                requiresPreciseReminders = true,
                preciseRemindersGranted = false
            )
        )
    }

    @Test
    fun `app notification access is repaired before category channel`() {
        assertEquals(
            NotificationEnablementStep.OPEN_APP_SETTINGS,
            resolve(
                appNotificationsEnabled = false,
                channelState = NotificationChannelState.BLOCKED
            )
        )
    }

    @Test
    fun `blocked and missing category channels route to channel settings`() {
        listOf(
            NotificationChannelState.BLOCKED,
            NotificationChannelState.MISSING
        ).forEach { channelState ->
            assertEquals(
                NotificationEnablementStep.OPEN_CHANNEL_SETTINGS,
                resolve(channelState = channelState)
            )
        }
    }

    @Test
    fun `care reminder request requires precise reminder access`() {
        assertEquals(
            NotificationEnablementStep.REQUEST_PRECISE_REMINDERS,
            resolve(
                requiresPreciseReminders = true,
                preciseRemindersGranted = false
            )
        )
        assertEquals(
            NotificationEnablementStep.READY,
            resolve(
                requiresPreciseReminders = false,
                preciseRemindersGranted = false
            )
        )
    }

    @Test
    fun `delivery state also requires the existing owner preference`() {
        assertFalse(
            NotificationEnablementState(
                ownerPreferenceEnabled = false,
                step = NotificationEnablementStep.READY
            ).canDeliver
        )
        assertTrue(
            NotificationEnablementState(
                ownerPreferenceEnabled = true,
                step = NotificationEnablementStep.READY
            ).canDeliver
        )
    }

    private fun resolve(
        runtimePermissionGranted: Boolean = true,
        appNotificationsEnabled: Boolean = true,
        channelState: NotificationChannelState = NotificationChannelState.ENABLED,
        requiresPreciseReminders: Boolean = false,
        preciseRemindersGranted: Boolean = true
    ): NotificationEnablementStep {
        return NotificationEnablementDecisionResolver.resolve(
            delivery = NotificationDeliveryReadiness(
                runtimePermissionGranted = runtimePermissionGranted,
                appNotificationsEnabled = appNotificationsEnabled,
                channelState = channelState
            ),
            requiresPreciseReminders = requiresPreciseReminders,
            preciseRemindersGranted = preciseRemindersGranted
        )
    }
}
