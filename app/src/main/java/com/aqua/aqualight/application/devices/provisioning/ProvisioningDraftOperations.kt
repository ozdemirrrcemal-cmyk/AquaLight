package com.aqua.aqualight.application.devices.provisioning

/**
 * Application boundary for creating an in-process provisioning session.
 *
 * The UI supplies validated primitive values. Data-store models, BLE address
 * cache access and draft persistence remain behind the implementation.
 */
interface ProvisioningDraftOperations {
    fun createDraft(
        request: ProvisioningDraftRequest
    ): Result<ProvisioningDraftSession>
}

data class ProvisioningDraftRequest(
    val candidateId: String,
    val bleAddress: String,
    val bleName: String,
    val claimCode: String,
    val rawQrPayload: String,
    val deviceTitle: String,
    val deviceSerial: String,
    val deviceModel: String,
    val wifiSsid: String,
    val wifiPassword: String,
    val timezone: String,
    val utcOffsetMinutes: Int
)

data class ProvisioningDraftSession(
    val sessionId: String
)
