package com.aqua.aqualight.ui.tabs.devices.detail.common

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("LongMethod")
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
            DeviceOtaFailureReason.RELEASE_CONNECTION_FAILED to
                R.string.device_settings_update_error_release_connection_failed,
            DeviceOtaFailureReason.DEVICE_NETWORK_UNAVAILABLE to
                R.string.device_settings_update_error_device_network_unavailable,
            DeviceOtaFailureReason.RELEASE_UNAVAILABLE to
                R.string.device_settings_update_error_release_unavailable,
            DeviceOtaFailureReason.RELEASE_ACCESS_DENIED to
                R.string.device_settings_update_error_release_access_denied,
            DeviceOtaFailureReason.RELEASE_RATE_LIMITED to
                R.string.device_settings_update_error_release_rate_limited,
            DeviceOtaFailureReason.RELEASE_REDIRECT_FAILED to
                R.string.device_settings_update_error_release_redirect_failed,
            DeviceOtaFailureReason.RELEASE_REQUEST_REJECTED to
                R.string.device_settings_update_error_release_request_rejected,
            DeviceOtaFailureReason.RELEASE_SERVER_UNAVAILABLE to
                R.string.device_settings_update_error_release_server_unavailable,
            DeviceOtaFailureReason.INCOMPATIBLE_FIRMWARE to
                R.string.device_settings_update_error_incompatible_firmware,
            DeviceOtaFailureReason.INSUFFICIENT_SPACE to
                R.string.device_settings_update_error_insufficient_space,
            DeviceOtaFailureReason.DOWNLOAD_CONNECTION_FAILED to
                R.string.device_settings_update_error_download_connection_failed,
            DeviceOtaFailureReason.DOWNLOAD_SEND_FAILED to
                R.string.device_settings_update_error_download_send_failed,
            DeviceOtaFailureReason.DOWNLOAD_CONNECTION_LOST to
                R.string.device_settings_update_error_download_connection_lost,
            DeviceOtaFailureReason.DOWNLOAD_STREAM_UNAVAILABLE to
                R.string.device_settings_update_error_download_stream_unavailable,
            DeviceOtaFailureReason.DOWNLOAD_SERVER_NO_RESPONSE to
                R.string.device_settings_update_error_download_server_no_response,
            DeviceOtaFailureReason.DOWNLOAD_DEVICE_MEMORY_LOW to
                R.string.device_settings_update_error_download_device_memory_low,
            DeviceOtaFailureReason.DOWNLOAD_ENCODING_UNSUPPORTED to
                R.string.device_settings_update_error_download_encoding_unsupported,
            DeviceOtaFailureReason.DOWNLOAD_STREAM_WRITE_FAILED to
                R.string.device_settings_update_error_download_stream_write_failed,
            DeviceOtaFailureReason.DOWNLOAD_TIMEOUT to
                R.string.device_settings_update_error_download_timeout,
            DeviceOtaFailureReason.DOWNLOAD_URL_OPEN_FAILED to
                R.string.device_settings_update_error_download_url_open_failed,
            DeviceOtaFailureReason.DOWNLOAD_STREAM_INTERRUPTED to
                R.string.device_settings_update_error_download_stream_interrupted,
            DeviceOtaFailureReason.DOWNLOAD_SIZE_MISMATCH to
                R.string.device_settings_update_error_download_size_mismatch,
            DeviceOtaFailureReason.DOWNLOAD_FAILED to
                R.string.device_settings_update_error_download_failed,
            DeviceOtaFailureReason.INTEGRITY_CHECK_FAILED to
                R.string.device_settings_update_error_integrity_check,
            DeviceOtaFailureReason.SAFE_MODE_FAILED to
                R.string.device_settings_update_error_safe_mode,
            DeviceOtaFailureReason.SAFE_MODE_RESTORE_FAILED to
                R.string.device_settings_update_error_safe_mode_restore,
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
