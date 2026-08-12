package com.aqua.aqualight.ui.common.devicemenu

/** Configuration for a compact trailing action in a device-menu row. */
data class AquaDeviceMenuRowAction(
    val text: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true
)
