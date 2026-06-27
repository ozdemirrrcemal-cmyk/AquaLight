package com.aqua.aqualight.data.devices.provisioning.model

data class AqlProvisioningDraft(
    val sessionId: String,
    val candidateId: String,
    val bleAddress: String,
    val deviceTitle: String,
    val deviceSerial: String,
    val deviceModel: String,
    val wifiCredentials: AqlWifiCredentials,
    val createdAtMillis: Long
)
