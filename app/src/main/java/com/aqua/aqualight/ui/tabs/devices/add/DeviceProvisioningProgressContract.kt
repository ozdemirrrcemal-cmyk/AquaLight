package com.aqua.aqualight.ui.tabs.devices.add

import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute

sealed interface DeviceProvisioningProgressEvent {
    data class OpenAddedDevice(
        val route: DeviceRoute
    ) : DeviceProvisioningProgressEvent
}

data class DeviceProvisioningWifiCredentialFailure(
    val message: String,
    val field: DeviceProvisioningWifiCredentialField
)

enum class DeviceProvisioningWifiCredentialField {
    SSID,
    PASSWORD
}

data class DeviceProvisioningProgressUiState(
    val title: String = "",
    val message: String = "",
    val deviceName: String = "",
    val deviceSerial: String = "",
    val bleAddress: String = "",
    val wifiSsid: String = "",
    val stepOne: String = "",
    val stepTwo: String = "",
    val stepThree: String = "",
    val canStart: Boolean = false,
    val buttonText: String = "",
    val showProgress: Boolean = false,
    val wifiCredentialFailure: DeviceProvisioningWifiCredentialFailure? = null
)
