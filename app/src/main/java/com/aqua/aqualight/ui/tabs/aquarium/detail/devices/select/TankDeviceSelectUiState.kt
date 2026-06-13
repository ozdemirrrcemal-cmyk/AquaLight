package com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select

data class TankDeviceSelectUiState(
    val devices: List<TankDeviceSelectItem> = emptyList(),
    val isEmpty: Boolean = true,
    val isAssigning: Boolean = false
)
