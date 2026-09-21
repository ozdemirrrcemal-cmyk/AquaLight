package com.aqua.aqualight.ui.common.devicepresence

import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceMenuUnavailableMessageMapperTest {

    @Test
    fun `only firmware recoverable compatibility reasons expose OTA action`() {
        val actionable = setOf(
            DeviceMenuUnavailableReason.CONTRACT_INCOMPATIBLE,
            DeviceMenuUnavailableReason.FIRMWARE_UPDATE_REQUIRED
        )

        DeviceMenuUnavailableReason.entries.forEach { reason ->
            val expected = if (reason in actionable) {
                DeviceAccessFeedbackAction.OPEN_FIRMWARE_UPDATE
            } else {
                DeviceAccessFeedbackAction.DISMISS
            }
            assertEquals(expected, DeviceMenuUnavailableMessageMapper.feedback(reason).action)
        }
    }
}
