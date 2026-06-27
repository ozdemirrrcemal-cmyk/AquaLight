package com.aqua.aqualight.data.devices.provisioning.model

import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract

data class AqlWifiCredentials(
    val ssid: String,
    val password: String
) {
    init {
        require(ssid.isNotBlank()) { "Wi-Fi SSID must not be blank." }
        require(ssid.length <= AqlBleProvisioningContract.WIFI_SSID_MAX_LENGTH) {
            "Wi-Fi SSID is too long."
        }
        require(password.length <= AqlBleProvisioningContract.WIFI_PASSWORD_MAX_LENGTH) {
            "Wi-Fi password is too long."
        }
    }
}
