package com.aqua.aqualight.ui.tabs.devices.detail.common

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceRootPresentationMapperTest {

    @Test
    fun `every OTA failure reason maps to the intended settings string`() {
        val expected = mapOf(
            DeviceOtaFailureReason.CHECK_FAILED to
                R.string.device_settings_update_error_check_failed,
            DeviceOtaFailureReason.CONNECTION to
                R.string.device_settings_update_error_connection,
            DeviceOtaFailureReason.AUTHENTICATION to
                R.string.device_settings_update_error_authentication,
            DeviceOtaFailureReason.DEVICE_BUSY to
                R.string.device_settings_update_error_device_busy,
            DeviceOtaFailureReason.UNSUPPORTED to
                R.string.device_settings_update_unsupported_status,
            DeviceOtaFailureReason.RELEASE_UNAVAILABLE to
                R.string.device_settings_update_error_release_unavailable,
            DeviceOtaFailureReason.INCOMPATIBLE_FIRMWARE to
                R.string.device_settings_update_error_incompatible_firmware,
            DeviceOtaFailureReason.INSUFFICIENT_SPACE to
                R.string.device_settings_update_error_insufficient_space,
            DeviceOtaFailureReason.DOWNLOAD_FAILED to
                R.string.device_settings_update_error_download_failed,
            DeviceOtaFailureReason.INTEGRITY_CHECK_FAILED to
                R.string.device_settings_update_error_integrity_check,
            DeviceOtaFailureReason.SAFE_MODE_FAILED to
                R.string.device_settings_update_error_safe_mode,
            DeviceOtaFailureReason.FLASH_WRITE_FAILED to
                R.string.device_settings_update_error_flash_write,
            DeviceOtaFailureReason.SECURITY_VALIDATION_FAILED to
                R.string.device_settings_update_error_security_validation,
            DeviceOtaFailureReason.PROTOCOL_MISMATCH to
                R.string.device_settings_update_error_protocol_mismatch,
            DeviceOtaFailureReason.DEVICE_INTERNAL to
                R.string.device_settings_update_error_device_internal
        )

        assertEquals(DeviceOtaFailureReason.entries.toSet(), expected.keys)
        expected.forEach { (reason, resource) ->
            assertEquals(resource, DeviceRootPresentationMapper.otaFailureMessageRes(reason))
        }
    }
}
