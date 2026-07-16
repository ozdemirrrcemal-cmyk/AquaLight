package com.aqua.aqualight.data.devices.provisioning.repository

import android.content.Context
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftOperations
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftRequest
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftSession
import com.aqua.aqualight.data.devices.provisioning.model.AqlWifiCredentials
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningBleAddressCache
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningDraftStore
import com.aqua.aqualight.data.devices.provisioning.store.ProvisioningDraftStorage

class DefaultProvisioningDraftOperations internal constructor(
    private val draftStore: ProvisioningDraftStorage,
    private val cachedBleAddress: (String) -> String = AqlProvisioningBleAddressCache::get
) : ProvisioningDraftOperations {

    constructor(context: Context) : this(
        draftStore = AqlProvisioningDraftStore(context.applicationContext)
    )

    override fun createDraft(
        request: ProvisioningDraftRequest
    ): Result<ProvisioningDraftSession> = runCatching {
        val credentials = AqlWifiCredentials(
            ssid = request.wifiSsid,
            password = request.wifiPassword,
            timezone = request.timezone,
            utcOffsetMinutes = request.utcOffsetMinutes
        )
        val draft = draftStore.create(
            candidateId = request.candidateId,
            bleAddress = request.bleAddress.ifBlank {
                cachedBleAddress(request.bleName)
            },
            bleName = request.bleName,
            claimCode = request.claimCode,
            rawQrPayload = request.rawQrPayload,
            deviceTitle = request.deviceTitle,
            deviceSerial = request.deviceSerial,
            deviceModel = request.deviceModel,
            wifiCredentials = credentials
        )
        ProvisioningDraftSession(sessionId = draft.sessionId)
    }
}
