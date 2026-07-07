package com.aqua.aqualight.ui.tabs.devices

import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardUi

/** User-facing wrapper for the Devices tab compact card. */
data class DeviceCardUi(
    val deviceUid: String,
    val card: DeviceCompactCardUi,
    val isSelected: Boolean = false
)
