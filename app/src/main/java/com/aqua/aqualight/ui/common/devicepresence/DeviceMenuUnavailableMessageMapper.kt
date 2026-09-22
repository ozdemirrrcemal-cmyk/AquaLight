package com.aqua.aqualight.ui.common.devicepresence

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason

data class DeviceAccessFeedback(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int
)

object DeviceMenuUnavailableMessageMapper {

    fun feedback(reason: DeviceMenuUnavailableReason): DeviceAccessFeedback = when (reason) {
        DeviceMenuUnavailableReason.DEVICE_OFFLINE -> DeviceAccessFeedback(
            R.string.device_menu_offline_dialog_title,
            R.string.device_menu_offline_message
        )
        DeviceMenuUnavailableReason.LOCAL_NETWORK_UNAVAILABLE -> DeviceAccessFeedback(
            R.string.device_access_local_network_title,
            R.string.device_menu_local_network_unavailable_message
        )
        DeviceMenuUnavailableReason.AUTHENTICATION_REQUIRED -> DeviceAccessFeedback(
            R.string.device_access_authentication_title,
            R.string.device_menu_authentication_required_message
        )
        DeviceMenuUnavailableReason.DEVICE_UNRESPONSIVE -> DeviceAccessFeedback(
            R.string.device_access_unresponsive_title,
            R.string.device_menu_device_unresponsive_message
        )
        DeviceMenuUnavailableReason.VERIFICATION_TIMED_OUT -> DeviceAccessFeedback(
            R.string.device_access_state_unverified_title,
            R.string.device_menu_verification_timed_out_message
        )
        DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN,
        DeviceMenuUnavailableReason.MALFORMED_DEVICE_STATE -> DeviceAccessFeedback(
            R.string.device_access_state_unverified_title,
            R.string.device_access_state_unverified_message
        )
        DeviceMenuUnavailableReason.INVALID_DEVICE_UID,
        DeviceMenuUnavailableReason.DEVICE_NOT_REGISTERED -> DeviceAccessFeedback(
            R.string.device_access_unavailable_title,
            R.string.device_access_unavailable_message
        )
        DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH -> DeviceAccessFeedback(
            R.string.device_access_unsupported_title,
            R.string.device_unsupported_family_message
        )
        DeviceMenuUnavailableReason.CONTRACT_INCOMPATIBLE -> DeviceAccessFeedback(
            R.string.device_access_contract_incompatible_title,
            R.string.device_access_contract_incompatible_message
        )
        DeviceMenuUnavailableReason.FEATURE_UNAVAILABLE -> DeviceAccessFeedback(
            R.string.device_access_feature_unavailable_title,
            R.string.device_access_feature_unavailable_message
        )
        DeviceMenuUnavailableReason.FIRMWARE_UPDATE_REQUIRED -> DeviceAccessFeedback(
            R.string.device_access_firmware_update_required_title,
            R.string.device_access_firmware_update_required_message
        )
        DeviceMenuUnavailableReason.APPLICATION_UPDATE_REQUIRED -> DeviceAccessFeedback(
            R.string.device_access_app_update_required_title,
            R.string.device_access_app_update_required_message
        )
    }

    @StringRes
    fun messageRes(reason: DeviceMenuUnavailableReason): Int = feedback(reason).messageRes

    @StringRes
    fun titleRes(reason: DeviceMenuUnavailableReason): Int = feedback(reason).titleRes
}
