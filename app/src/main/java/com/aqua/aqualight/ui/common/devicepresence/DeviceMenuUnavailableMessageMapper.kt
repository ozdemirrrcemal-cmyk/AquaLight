package com.aqua.aqualight.ui.common.devicepresence

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason

data class DeviceAccessFeedback(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int
)

/**
 * Single commercial presentation mapping for device access failures.
 *
 * Connectivity, compatibility and feature availability remain distinct all the way to the user.
 */
object DeviceMenuUnavailableMessageMapper {

    fun feedback(reason: DeviceMenuUnavailableReason): DeviceAccessFeedback = when (reason) {
        DeviceMenuUnavailableReason.LOCAL_NETWORK_UNAVAILABLE -> DeviceAccessFeedback(
            titleRes = R.string.device_access_local_network_title,
            messageRes = R.string.device_access_local_network_message
        )
        DeviceMenuUnavailableReason.AUTHENTICATION_REQUIRED -> DeviceAccessFeedback(
            titleRes = R.string.device_access_authentication_title,
            messageRes = R.string.device_access_authentication_message
        )
        DeviceMenuUnavailableReason.DEVICE_UNRESPONSIVE -> DeviceAccessFeedback(
            titleRes = R.string.device_access_unresponsive_title,
            messageRes = R.string.device_access_unresponsive_message
        )
        DeviceMenuUnavailableReason.VERIFICATION_TIMED_OUT,
        DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN -> DeviceAccessFeedback(
            titleRes = R.string.device_access_verification_title,
            messageRes = R.string.device_access_verification_message
        )
        DeviceMenuUnavailableReason.INVALID_DEVICE_UID,
        DeviceMenuUnavailableReason.DEVICE_NOT_REGISTERED -> DeviceAccessFeedback(
            titleRes = R.string.device_access_not_registered_title,
            messageRes = R.string.device_access_not_registered_message
        )
        DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH -> DeviceAccessFeedback(
            titleRes = R.string.device_access_unsupported_product_title,
            messageRes = R.string.device_access_unsupported_product_message
        )
        DeviceMenuUnavailableReason.CONTRACT_INCOMPATIBLE -> DeviceAccessFeedback(
            titleRes = R.string.device_access_contract_incompatible_title,
            messageRes = R.string.device_access_contract_incompatible_message
        )
        DeviceMenuUnavailableReason.MALFORMED_DEVICE_STATE -> DeviceAccessFeedback(
            titleRes = R.string.device_access_malformed_state_title,
            messageRes = R.string.device_access_malformed_state_message
        )
        DeviceMenuUnavailableReason.FEATURE_UNAVAILABLE -> DeviceAccessFeedback(
            titleRes = R.string.device_access_feature_unavailable_title,
            messageRes = R.string.device_access_feature_unavailable_message
        )
        DeviceMenuUnavailableReason.FIRMWARE_UPDATE_REQUIRED -> DeviceAccessFeedback(
            titleRes = R.string.device_access_firmware_update_required_title,
            messageRes = R.string.device_access_firmware_update_required_message
        )
        DeviceMenuUnavailableReason.APPLICATION_UPDATE_REQUIRED -> DeviceAccessFeedback(
            titleRes = R.string.device_access_app_update_required_title,
            messageRes = R.string.device_access_app_update_required_message
        )
    }

    @StringRes
    fun messageRes(reason: DeviceMenuUnavailableReason): Int = feedback(reason).messageRes

    @StringRes
    fun titleRes(reason: DeviceMenuUnavailableReason): Int = feedback(reason).titleRes
}
