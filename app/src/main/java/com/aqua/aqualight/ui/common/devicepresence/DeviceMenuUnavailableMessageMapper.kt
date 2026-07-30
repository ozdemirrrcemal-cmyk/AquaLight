package com.aqua.aqualight.ui.common.devicepresence

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason

object DeviceMenuUnavailableMessageMapper {

    @StringRes
    fun messageRes(reason: DeviceMenuUnavailableReason): Int {
        return when (reason) {
            DeviceMenuUnavailableReason.LOCAL_NETWORK_UNAVAILABLE -> {
                R.string.device_menu_local_network_unavailable_message
            }
            DeviceMenuUnavailableReason.AUTHENTICATION_REQUIRED -> {
                R.string.device_menu_authentication_required_message
            }
            DeviceMenuUnavailableReason.DEVICE_UNRESPONSIVE -> {
                R.string.device_menu_device_unresponsive_message
            }
            DeviceMenuUnavailableReason.VERIFICATION_TIMED_OUT -> {
                R.string.device_menu_verification_timed_out_message
            }
            DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH -> {
                R.string.device_unsupported_family_message
            }
            DeviceMenuUnavailableReason.INVALID_DEVICE_UID,
            DeviceMenuUnavailableReason.DEVICE_NOT_REGISTERED,
            DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN -> {
                R.string.device_menu_offline_message
            }
        }
    }
}
