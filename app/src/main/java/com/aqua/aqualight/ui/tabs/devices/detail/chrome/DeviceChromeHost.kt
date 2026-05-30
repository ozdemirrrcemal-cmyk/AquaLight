package com.aqua.aqualight.ui.tabs.devices.detail.chrome

interface DeviceChromeHost {

    fun setDeviceHeader(
        title: String,
        actions: List<DeviceHeaderAction> = emptyList(),
        onBackClick: (() -> Unit)? = null
    )
}