package com.aqua.aqualight.data.devices.provisioning.ble

data class AqlBleProvisioningCandidate(
    val address: String,
    val name: String,
    val rssi: Int,
    val firstSeenAtMillis: Long,
    val lastSeenAtMillis: Long,
    val deviceUid: String = "",
    val productName: String = "",
    val model: String = "",
    val serialNumber: String = "",
    val claimState: String = "",
    val rawAdvertisementPayload: String = ""
) {
    val displayTitle: String
        get() = productName
            .ifBlank { model }
            .ifBlank { name }
            .ifBlank { "AquaLight Device" }

    val displaySerial: String
        get() = serialNumber
            .ifBlank { deviceUid }
            .ifBlank { address }

    val displayStatus: String
        get() = claimState
            .ifBlank { "Ready" }
}
