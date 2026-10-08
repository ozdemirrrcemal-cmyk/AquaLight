package com.aqua.aqualight.ui.common.devicepresence

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMenuUnavailableMessageMapperTest {

    @Test
    fun `every unavailable reason has complete resource presentation`() {
        DeviceMenuUnavailableReason.entries.forEach { reason ->
            val presentation = DeviceMenuUnavailableMessageMapper.presentation(reason)
            assertTrue(reason.name, presentation.titleRes != 0)
            assertTrue(reason.name, presentation.messageRes != 0)
        }
    }

    @Test
    fun `offline unresponsive and timeout use distinct commercial copy`() {
        val offline = DeviceMenuUnavailableMessageMapper.presentation(
            DeviceMenuUnavailableReason.DEVICE_OFFLINE
        )
        assertEquals(R.string.device_menu_offline_dialog_title, offline.titleRes)
        assertEquals(R.string.device_menu_offline_message, offline.messageRes)

        val unresponsive = DeviceMenuUnavailableMessageMapper.presentation(
            DeviceMenuUnavailableReason.DEVICE_UNRESPONSIVE
        )
        assertEquals(R.string.device_menu_device_unresponsive_title, unresponsive.titleRes)
        assertEquals(R.string.device_menu_device_unresponsive_message, unresponsive.messageRes)

        val timeout = DeviceMenuUnavailableMessageMapper.presentation(
            DeviceMenuUnavailableReason.VERIFICATION_TIMED_OUT
        )
        assertEquals(R.string.device_menu_verification_timed_out_title, timeout.titleRes)
        assertEquals(R.string.device_menu_verification_timed_out_message, timeout.messageRes)
    }

    @Test
    fun `unverified state does not claim definitive offline presence`() {
        val presentation = DeviceMenuUnavailableMessageMapper.presentation(
            DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
        )

        assertEquals(R.string.device_menu_status_unverified_title, presentation.titleRes)
        assertEquals(R.string.device_menu_status_unverified_message, presentation.messageRes)
    }
}
