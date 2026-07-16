package com.aqua.aqualight.data.devices.provisioning.repository

import android.content.Context
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftOperations
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftRequest
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftSession
import com.aqua.aqualight.data.devices.provisioning.model.AqlWifiCredentials
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningBleAddressCache
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningDraftStore
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningQrSecretStore
import com.aqua.aqualight.data.devices.provisioning.store.ProvisioningDraftStorage
import com.aqua.aqualight.data.devices.provisioning.store.ProvisioningQrSecret
import com.aqua.aqualight.data.devices.provisioning.store.ProvisioningQrSecretStorage

class DefaultProvisioningDraftOperations internal constructor(
    private val draftStore: ProvisioningDraftStorage,
    private val qrSecretStore: ProvisioningQrSecretStorage,
    private val cachedBleAddress: (String) -> String = AqlProvisioningBleAddressCache::get
) : ProvisioningDraftOperations {

    constructor(context: Context) : this(
        draftStore = AqlProvisioningDraftStore(context.applicationContext),
        qrSecretStore = AqlProvisioningQrSecretStore(context.applicationContext)
    )

    override fun createDraft(
        request: ProvisioningDraftRequest
    ): Result<ProvisioningDraftSession> = runCatching {
        val secretReference = request.qrSecretReference.trim()
        val qrSecret = if (secretReference.isBlank()) {
            ProvisioningQrSecret(claimCode = "", rawPayload = "")
        } else {
            requireNotNull(qrSecretStore.get(secretReference)) {
                "Provisioning QR secret is unavailable or expired."
            }
        }
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
            claimCode = qrSecret.claimCode,
            rawQrPayload = qrSecret.rawPayload,
            deviceTitle = request.deviceTitle,
            deviceSerial = request.deviceSerial,
            deviceModel = request.deviceModel,
            wifiCredentials = credentials
        )

        // Read without consuming the encrypted QR material. A rejected Wi-Fi
        // credential returns the user to the same form, which creates a replacement
        // draft from the original QR reference. The owner-scoped secret store already
        // limits exposure with encryption, a 15-minute TTL and bounded record count.
        ProvisioningDraftSession(sessionId = draft.sessionId)
    }
}
