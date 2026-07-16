package com.aqua.aqualight.application.devices.provisioning

/**
 * Application boundary for creating a provisioning session.
 *
 * The UI supplies validated primitive values and, for QR setup, only an opaque
 * reference to encrypted claim material. Data models, secrets, BLE cache access
 * and draft persistence remain behind the implementation.
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
    val qrSecretReference: String,
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
