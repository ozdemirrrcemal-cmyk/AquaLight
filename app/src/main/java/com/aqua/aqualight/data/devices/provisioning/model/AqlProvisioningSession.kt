package com.aqua.aqualight.data.devices.provisioning.model

import com.aqua.aqualight.data.devices.provisioning.qr.AqlProvisioningQrPayload

data class AqlProvisioningSession(
    val qrPayload: AqlProvisioningQrPayload,
    val status: AqlProvisioningStatus = AqlProvisioningStatus.IDLE,
    val wifiCredentials: AqlWifiCredentials? = null,
    val runtimeHandoff: AqlProvisioningRuntimeHandoff? = null,
    val message: String = "",
    val errorMessage: String = ""
) {
    val isCompleted: Boolean
        get() = status == AqlProvisioningStatus.COMPLETED && runtimeHandoff?.isUsable == true

    val hasError: Boolean
        get() = status == AqlProvisioningStatus.ERROR ||
            status == AqlProvisioningStatus.TIMEOUT ||
            status == AqlProvisioningStatus.CLAIM_REJECTED ||
            status == AqlProvisioningStatus.WIFI_FAILED ||
            errorMessage.isNotBlank()
}
