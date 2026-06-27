package com.aqua.aqualight.data.devices.provisioning.ble

data class AqlBleProvisioningAdvertisement(
    val deviceUid: String = "",
    val productName: String = "",
    val model: String = "",
    val serialNumber: String = "",
    val claimState: String = "",
    val bleName: String = "",
    val rawPayload: String = "",
    val fields: Map<String, String> = emptyMap()
) {
    val displayTitle: String
        get() = productName
            .ifBlank { model }
            .ifBlank { bleName }
            .ifBlank { "AquaLight Device" }

    val displaySerial: String
        get() = serialNumber
            .ifBlank { deviceUid }

    val displayStatus: String
        get() = claimState
            .ifBlank { "Ready" }
}
