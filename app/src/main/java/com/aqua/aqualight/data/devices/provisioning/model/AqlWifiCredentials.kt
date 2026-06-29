package com.aqua.aqualight.data.devices.provisioning.model

import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract

data class AqlWifiCredentials(
    val ssid: String,
    val password: String,
    val bssid: String = "",
    val channel: Int = 0,
    val timezone: String = "",
    val utcOffsetMinutes: Int = 0
) {
    init {
        require(ssid.isNotBlank()) { "Wi-Fi SSID must not be blank." }
        require(ssid.utf8ByteSize() <= AqlBleProvisioningContract.WIFI_SSID_MAX_LENGTH) {
            "Wi-Fi SSID is too long."
        }
        require(password.utf8ByteSize() <= AqlBleProvisioningContract.WIFI_PASSWORD_MAX_LENGTH) {
            "Wi-Fi password is too long."
        }
        require(channel >= 0) { "Wi-Fi channel must not be negative." }
        require(utcOffsetMinutes >= -840 && utcOffsetMinutes <= 840) {
            "UTC offset is out of range."
        }
    }
}

private fun String.utf8ByteSize(): Int = toByteArray(Charsets.UTF_8).size
