package com.aqua.aqualight.data.devices.provisioning.ble

import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningStatus

data class AqlBleProvisioningStatusMessage(
    val status: AqlProvisioningStatus,
    val message: String = "",
    val errorCode: String = "",
    val retryable: Boolean = false,
    val raw: String = ""
)
