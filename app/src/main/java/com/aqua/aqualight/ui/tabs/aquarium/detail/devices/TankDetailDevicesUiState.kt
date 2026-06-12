package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

data class TankDetailDevicesUiState(
    val devices: List<TankAssignedDeviceUi> = emptyList(),
    val errorMessage: String? = null
)
