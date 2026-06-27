package com.aqua.aqualight.data.devices.provisioning.ble

data class AqlBleProvisioningCandidate(
    val address: String,
    val name: String,
    val rssi: Int,
    val firstSeenAtMillis: Long,
    val lastSeenAtMillis: Long
)
