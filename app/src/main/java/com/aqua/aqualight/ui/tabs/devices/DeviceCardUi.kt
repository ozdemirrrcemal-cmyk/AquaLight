package com.aqua.aqualight.ui.tabs.devices

import androidx.annotation.DrawableRes
import com.aqua.aqualight.data.devices.model.DeviceOnlineState

/** User-facing card model for the Devices tab. */
data class DeviceCardUi(
    val deviceUid: String,
    val title: String,
    val subtitle: String,
    val statusLabel: String,
    val statusStyle: StatusStyle,
    val ipText: String,
    val serialText: String,
    val firmwareText: String,
    val lastSeenText: String,
    val productText: String,
    val onlineState: DeviceOnlineState,
    @DrawableRes val iconRes: Int,
    val isSelected: Boolean = false
) {
    enum class StatusStyle {
        ONLINE,
        CONNECTING,
        WARNING,
        OFFLINE
    }
}
