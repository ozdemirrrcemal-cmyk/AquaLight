package com.aqua.aqualight.ui.tabs.devices.add

import androidx.annotation.StringRes
import com.aqua.aqualight.application.devices.provisioning.ProvisionedDevice

sealed interface DeviceProvisioningProgressEvent {
    data class OpenAddedDevice(
        val device: ProvisionedDevice
    ) : DeviceProvisioningProgressEvent

    data class ShowAddedDeviceUnavailable(
        val title: String,
        @StringRes val messageRes: Int
    ) : DeviceProvisioningProgressEvent

    data object ExitProvisioning : DeviceProvisioningProgressEvent

    data object ShowCancellationFailed : DeviceProvisioningProgressEvent
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
    val isCancelling: Boolean = false,
    val requiresFreshDeviceSelection: Boolean = false,
    val wifiCredentialFailure: DeviceProvisioningWifiCredentialFailure? = null
)
