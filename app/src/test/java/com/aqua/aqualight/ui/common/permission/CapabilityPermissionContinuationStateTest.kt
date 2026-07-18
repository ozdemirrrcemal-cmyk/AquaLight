package com.aqua.aqualight.ui.common.permission

import com.aqua.aqualight.platform.permissions.AppCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityPermissionContinuationStateTest {

    @Test
    fun `settings grant survives recreation and is consumed exactly once`() {
        val original = CapabilityPermissionContinuationState().apply {
            begin(
                capability = AppCapability.NOTIFICATIONS,
                actionToken = "enable_notifications",
                notificationChannelId = "care_reminders"
            )
            markWaitingForSettings()
        }

        val recreated = CapabilityPermissionContinuationState().apply {
            restore(original.snapshot())
        }

        assertTrue(recreated.waitingForSettings)
        assertEquals(AppCapability.NOTIFICATIONS, recreated.pendingCapability)
        assertEquals("care_reminders", recreated.pendingNotificationChannelId)
        assertEquals(
            "enable_notifications",
            recreated.consumeSettingsReturn(isGranted = true)
        )
        assertNull(recreated.consumeSettingsReturn(isGranted = true))
        assertNull(recreated.consumeAction())
        assertNull(recreated.pendingCapability)
    }

    @Test
    fun `settings denial clears pending action`() {
        val state = CapabilityPermissionContinuationState().apply {
            begin(
                capability = AppCapability.CAMERA_QR,
                actionToken = "scan_qr"
            )
            markWaitingForSettings()
        }

        assertNull(state.consumeSettingsReturn(isGranted = false))
        assertFalse(state.waitingForSettings)
        assertNull(state.pendingCapability)
        assertNull(state.pendingActionToken)
    }

    @Test
    fun `duplicate lifecycle and activity result callbacks cannot run action twice`() {
        val state = CapabilityPermissionContinuationState().apply {
            begin(
                capability = AppCapability.BLE_PROVISIONING,
                actionToken = "start_provisioning"
            )
            markWaitingForSettings()
        }

        assertEquals(
            "start_provisioning",
            state.consumeSettingsReturn(isGranted = true)
        )
        assertNull(state.consumeIfGranted(isGranted = true))
        assertNull(state.consumeSettingsReturn(isGranted = true))
    }

    @Test
    fun `ordinary grant is consumed once without settings state`() {
        val state = CapabilityPermissionContinuationState().apply {
            begin(
                capability = AppCapability.WIFI_SSID,
                actionToken = "read_ssid"
            )
        }

        assertEquals("read_ssid", state.consumeIfGranted(isGranted = true))
        assertNull(state.consumeIfGranted(isGranted = true))
    }

    @Test
    fun `invalid restored snapshot is discarded`() {
        val state = CapabilityPermissionContinuationState()
        state.restore(
            CapabilityPermissionContinuationSnapshot(
                capabilityName = "NOT_A_CAPABILITY",
                actionToken = "action",
                notificationChannelId = "care_reminders",
                waitingForSettings = true
            )
        )

        assertNull(state.pendingCapability)
        assertNull(state.pendingActionToken)
        assertFalse(state.waitingForSettings)
    }
}
