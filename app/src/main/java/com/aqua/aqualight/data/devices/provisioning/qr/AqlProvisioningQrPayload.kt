package com.aqua.aqualight.data.devices.provisioning.qr

import com.aqua.aqualight.data.devices.model.DeviceUid

data class AqlProvisioningQrPayload(
    val version: Int,
    val brand: String,
    val deviceUid: DeviceUid,
    val serialNumber: String,
    val productId: String,
    val model: String,
    val displayName: String,
    val hardwareRevision: String,
    val skuCode: String,
    val provisioningId: String,
    val claimCode: String,
    val bleName: String,
    val raw: String,
    val fields: Map<String, String> = emptyMap()
)
