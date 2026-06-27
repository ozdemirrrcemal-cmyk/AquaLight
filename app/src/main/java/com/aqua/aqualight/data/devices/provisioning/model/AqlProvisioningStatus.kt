package com.aqua.aqualight.data.devices.provisioning.model

import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract

enum class AqlProvisioningStatus(
    val wireValue: String
) {
    IDLE(AqlBleProvisioningContract.Status.IDLE),
    FACTORY(AqlBleProvisioningContract.Status.FACTORY),
    PHYSICAL_RESET(AqlBleProvisioningContract.Status.PHYSICAL_RESET),
    PROVISIONING_IN_PROGRESS(AqlBleProvisioningContract.Status.PROVISIONING_IN_PROGRESS),
    CLAIM_VALIDATING(AqlBleProvisioningContract.Status.CLAIM_VALIDATING),
    CLAIM_REJECTED(AqlBleProvisioningContract.Status.CLAIM_REJECTED),
    WIFI_CREDENTIALS_RECEIVED(AqlBleProvisioningContract.Status.WIFI_CREDENTIALS_RECEIVED),
    WIFI_CONNECTING(AqlBleProvisioningContract.Status.WIFI_CONNECTING),
    WIFI_CONNECTED(AqlBleProvisioningContract.Status.WIFI_CONNECTED),
    WIFI_FAILED(AqlBleProvisioningContract.Status.WIFI_FAILED),
    WEB_SOCKET_TOKEN_READY(AqlBleProvisioningContract.Status.WEB_SOCKET_TOKEN_READY),
    COMPLETED(AqlBleProvisioningContract.Status.COMPLETED),
    TIMEOUT(AqlBleProvisioningContract.Status.TIMEOUT),
    ERROR(AqlBleProvisioningContract.Status.ERROR),
    UNKNOWN("unknown");

    companion object {
        fun fromWireValue(value: String?): AqlProvisioningStatus {
            val normalized = value?.trim().orEmpty()
            return entries.firstOrNull { status ->
                status.wireValue.equals(normalized, ignoreCase = true)
            } ?: UNKNOWN
        }
    }
}
