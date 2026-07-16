package com.aqua.aqualight.data.devices.provisioning.store

import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlWifiCredentials

internal interface ProvisioningDraftStorage {
    fun create(
        candidateId: String,
        bleAddress: String,
        bleName: String = "",
        claimCode: String = "",
        rawQrPayload: String = "",
        deviceTitle: String,
        deviceSerial: String,
        deviceModel: String,
        wifiCredentials: AqlWifiCredentials,
        createdAtMillis: Long = System.currentTimeMillis()
    ): AqlProvisioningDraft

    fun get(sessionId: String): AqlProvisioningDraft?

    fun remove(sessionId: String)

    fun clearOwner()
}
