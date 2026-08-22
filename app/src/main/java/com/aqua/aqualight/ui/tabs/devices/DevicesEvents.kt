package com.aqua.aqualight.ui.tabs.devices

import androidx.annotation.StringRes
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute

sealed class DevicesEvent {
    data class OpenRoute(
        val route: DeviceRoute,
        val requestDeviceUid: String = route.deviceUid
    ) : DevicesEvent()

    data class ShowDeviceUnavailable(
        val requestDeviceUid: String,
        val title: String,
        @StringRes val messageRes: Int
    ) : DevicesEvent()

    data class ShowDeletePartialSuccess(
        val succeededCount: Int,
        val failedCount: Int
    ) : DevicesEvent()

    data class ShowDeleteFailed(
        val failedCount: Int
    ) : DevicesEvent()
}
