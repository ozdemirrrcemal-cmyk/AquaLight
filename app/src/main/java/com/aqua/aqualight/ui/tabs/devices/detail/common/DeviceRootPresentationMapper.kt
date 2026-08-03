package com.aqua.aqualight.ui.tabs.devices.detail.common

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.application.devices.DeviceRootCapability
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.ui.common.text.AquaUiText

object DeviceRootPresentationMapper {

    @StringRes
    fun availabilityLabelRes(snapshot: DeviceRootSnapshot): Int =
        if (snapshot.availability == OwnerDeviceAvailability.REACHABLE) {
            R.string.device_online
        } else {
            R.string.device_offline
        }

    @StringRes
    fun otaFailureMessageRes(reason: DeviceOtaFailureReason): Int =
        OTA_FAILURE_MESSAGE_RESOURCES.getValue(reason)

    fun primaryCount(snapshot: DeviceRootSnapshot, kind: DeviceRootKind): Int = when (kind) {
        DeviceRootKind.DOSING -> snapshot.dosingChannelCount
        DeviceRootKind.TIMER -> snapshot.timerChannelCount
        DeviceRootKind.COOLING -> snapshot.fanOutputCount
    }

    fun overviewFeatureText(snapshot: DeviceRootSnapshot, kind: DeviceRootKind): AquaUiText {
        val labels = buildList<AquaUiText> {
            when (kind) {
                DeviceRootKind.DOSING -> if (DeviceRootCapability.DOSING in snapshot.capabilities) {
                    addResource(R.string.device_feature_dosing)
                }
                DeviceRootKind.TIMER -> if (DeviceRootCapability.STANDALONE_TIMER in snapshot.capabilities) {
                    addResource(R.string.device_feature_timer)
                }
                DeviceRootKind.COOLING -> {
                    if (DeviceRootCapability.COOLING in snapshot.capabilities) {
                        addResource(R.string.device_feature_cooling)
                    }
                    if (DeviceRootCapability.FAN in snapshot.capabilities) {
                        addResource(R.string.device_feature_fan)
                    }
                    if (DeviceRootCapability.TEMPERATURE in snapshot.capabilities) {
                        addResource(R.string.device_feature_temperature)
                    }
                }
            }
            if (DeviceRootCapability.TIME_SYNC in snapshot.capabilities) {
                addResource(R.string.device_feature_time_sync)
            }
            if (DeviceRootCapability.OTA in snapshot.capabilities) {
                addResource(R.string.device_feature_ota)
            }
            addDynamic(snapshot.supportedFeatures)
            addDynamic(snapshot.supportedScreens)
        }
        return labels.asUiText()
    }

    fun lightFeatureText(snapshot: DeviceRootSnapshot): AquaUiText {
        val labels = buildList<AquaUiText> {
            if (DeviceRootCapability.MANUAL_LIGHT in snapshot.capabilities) {
                addResource(R.string.device_feature_manual_light)
            }
            if (DeviceRootCapability.LIGHT_PROGRAM in snapshot.capabilities) {
                addResource(R.string.device_feature_program)
            }
            if (DeviceRootCapability.LIGHT_PRESETS in snapshot.capabilities) {
                addResource(R.string.device_feature_presets)
            }
            if (DeviceRootCapability.LIGHT_SIMULATION in snapshot.capabilities) {
                addResource(R.string.device_feature_simulation)
            }
            if (DeviceRootCapability.TEMPERATURE in snapshot.capabilities) {
                addResource(R.string.device_feature_temperature)
            }
            if (DeviceRootCapability.OTA in snapshot.capabilities) {
                addResource(R.string.device_feature_ota)
            }
            addDynamic(snapshot.supportedFeatures)
            addDynamic(snapshot.supportedScreens)
        }
        return labels.asUiText()
    }

    private fun MutableList<AquaUiText>.addResource(@StringRes resId: Int) {
        add(AquaUiText.Resource(resId))
    }

    private fun MutableList<AquaUiText>.addDynamic(values: Iterable<String>) {
        values.filter(String::isNotBlank).mapTo(this) { AquaUiText.Dynamic(it) }
    }

    private fun List<AquaUiText>.asUiText(): AquaUiText {
        val distinctLabels = distinct()
        return if (distinctLabels.isEmpty()) {
            AquaUiText.Resource(R.string.device_unknown)
        } else {
            AquaUiText.Joined(
                parts = distinctLabels,
                separatorRes = R.string.common_list_separator
            )
        }
    }
}

private val OTA_FAILURE_MESSAGE_RESOURCES = mapOf(
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
    DeviceOtaFailureReason.FLASH_WRITE_FAILED to
        R.string.device_settings_update_error_flash_write,
    DeviceOtaFailureReason.SECURITY_VALIDATION_FAILED to
        R.string.device_settings_update_error_security_validation,
    DeviceOtaFailureReason.PROTOCOL_MISMATCH to
        R.string.device_settings_update_error_protocol_mismatch,
    DeviceOtaFailureReason.DEVICE_INTERNAL to
        R.string.device_settings_update_error_device_internal
)
