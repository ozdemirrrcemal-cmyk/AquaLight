package com.aqua.aqualight.ui.common.devicepresence

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason

data class DeviceMenuUnavailablePresentation(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int
)

object DeviceMenuUnavailableMessageMapper {

    fun presentation(
        reason: DeviceMenuUnavailableReason
    ): DeviceMenuUnavailablePresentation {
        return when (reason) {
            DeviceMenuUnavailableReason.INVALID_DEVICE_UID -> DeviceMenuUnavailablePresentation(
                titleRes = R.string.device_menu_unavailable_dialog_title,
                messageRes = R.string.device_menu_invalid_device_message
            )
            DeviceMenuUnavailableReason.DEVICE_NOT_REGISTERED ->
                DeviceMenuUnavailablePresentation(
                    titleRes = R.string.device_menu_unavailable_dialog_title,
                    messageRes = R.string.device_menu_not_registered_message
                )
            DeviceMenuUnavailableReason.LOCAL_NETWORK_UNAVAILABLE ->
                DeviceMenuUnavailablePresentation(
                    titleRes = R.string.device_menu_local_network_unavailable_title,
                    messageRes = R.string.device_menu_local_network_unavailable_message
                )
            DeviceMenuUnavailableReason.AUTHENTICATION_REQUIRED ->
                DeviceMenuUnavailablePresentation(
                    titleRes = R.string.device_menu_authentication_required_title,
                    messageRes = R.string.device_menu_authentication_required_message
                )
            DeviceMenuUnavailableReason.DEVICE_OFFLINE ->
                DeviceMenuUnavailablePresentation(
                    titleRes = R.string.device_menu_offline_dialog_title,
                    messageRes = R.string.device_menu_offline_message
                )
            DeviceMenuUnavailableReason.DEVICE_UNRESPONSIVE ->
                DeviceMenuUnavailablePresentation(
                    titleRes = R.string.device_menu_device_unresponsive_title,
                    messageRes = R.string.device_menu_device_unresponsive_message
                )
            DeviceMenuUnavailableReason.VERIFICATION_TIMED_OUT ->
                DeviceMenuUnavailablePresentation(
                    titleRes = R.string.device_menu_verification_timed_out_title,
                    messageRes = R.string.device_menu_verification_timed_out_message
                )
            DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN ->
                DeviceMenuUnavailablePresentation(
                    titleRes = R.string.device_menu_status_unverified_title,
                    messageRes = R.string.device_menu_status_unverified_message
                )
            DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH ->
                DeviceMenuUnavailablePresentation(
                    titleRes = R.string.screen_title_unsupported_device,
                    messageRes = R.string.device_menu_commercial_product_mismatch_message
                )
        }
    }

    @StringRes
    fun messageRes(reason: DeviceMenuUnavailableReason): Int = presentation(reason).messageRes
}
