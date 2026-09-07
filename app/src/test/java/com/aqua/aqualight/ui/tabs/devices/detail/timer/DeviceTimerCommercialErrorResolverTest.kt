package com.aqua.aqualight.ui.tabs.devices.detail.timer

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.timer.DeviceTimerCommandFailure
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTimerCommercialErrorResolverTest {

    @Test
    fun `every command failure resolves to stable commercial copy`() {
        val expectedMessages = mapOf(
            DeviceTimerCommandFailure.CONFLICT to
                R.string.device_timer_error_conflict_message,
            DeviceTimerCommandFailure.INVALID_REQUEST to
                R.string.device_timer_error_invalid_request_message,
            DeviceTimerCommandFailure.INVALID_CONFIGURATION to
                R.string.device_timer_error_invalid_configuration_message,
            DeviceTimerCommandFailure.CHANNEL_UNAVAILABLE to
                R.string.device_timer_error_channel_unavailable_message,
            DeviceTimerCommandFailure.RESOURCE_UNAVAILABLE to
                R.string.device_timer_error_resource_unavailable_message,
            DeviceTimerCommandFailure.HARDWARE_FAILURE to
                R.string.device_timer_error_hardware_failure_message,
            DeviceTimerCommandFailure.STORAGE_FAILURE to
                R.string.device_timer_error_storage_failure_message,
            DeviceTimerCommandFailure.RUNTIME_LOCKED to
                R.string.device_timer_error_runtime_locked_message,
            DeviceTimerCommandFailure.PROTOCOL_ERROR to
                R.string.device_timer_error_protocol_message,
            DeviceTimerCommandFailure.UNKNOWN_REJECTION to
                R.string.device_timer_error_rejected_message
        )

        assertEquals(DeviceTimerCommandFailure.entries.toSet(), expectedMessages.keys)
        expectedMessages.forEach { (failure, messageRes) ->
            assertEquals(messageRes, failure.toCommercialTimerError().messageRes)
        }
    }

    @Test
    fun `rejected control failure delegates to its closed application reason`() {
        val message = DeviceTimerControlFailure.Rejected(
            DeviceTimerCommandFailure.RUNTIME_LOCKED
        ).toCommercialTimerError()

        assertEquals(R.string.device_timer_error_runtime_locked_title, message.titleRes)
        assertEquals(R.string.device_timer_error_runtime_locked_message, message.messageRes)
    }

    @Test
    fun `clock unavailable resolves as a status instead of a command error`() {
        val message = DeviceTimerChannelStatusNotice.CLOCK_UNAVAILABLE
            .toCommercialTimerStatus()

        assertEquals(R.string.device_timer_status_clock_unavailable_title, message.titleRes)
        assertEquals(R.string.device_timer_status_clock_unavailable_message, message.messageRes)
    }
}
