package com.aqua.aqualight.application.devices.provisioning

import kotlinx.coroutines.flow.Flow

/** Stable application boundary for Nearby Scan and QR preflight discovery. */
interface ProvisioningDiscoveryOperations {
    val candidates: Flow<List<ProvisioningCandidateSnapshot>>
    val scanFailures: Flow<ProvisioningScanFailure>

    fun startScan(): ProvisioningScanStartResult

    fun stopScan()

    fun clearCandidates()

    fun parseQr(rawValue: String): Result<ProvisioningQrPayload>

    fun discardQrPayload(payload: ProvisioningQrPayload)

    suspend fun awaitQrCandidate(
        payload: ProvisioningQrPayload,
        timeoutMillis: Long
    ): ProvisioningCandidateSnapshot?

    suspend fun verifyManualCandidate(
        candidate: ProvisioningCandidateSnapshot
    ): ProvisioningManualPreflightResult

    fun hasCandidates(): Boolean

    fun isRegistered(deviceUid: String): Boolean
}

data class ProvisioningCandidateSnapshot(
    val address: String,
    val bleName: String,
    val rssi: Int,
    val deviceUid: String,
    val displayTitle: String,
    val model: String,
    val displaySerial: String,
    val displayStatus: String,
    val rawAdvertisementPayload: String
)

/** QR identity plus an opaque reference to encrypted claim material. */
data class ProvisioningQrPayload(
    val deviceUid: String,
    val serialNumber: String,
    val productId: String,
    val model: String,
    val displayName: String,
    val hardwareRevision: String,
    val skuCode: String,
    val provisioningId: String,
    val secretReference: String,
    val bleName: String
)

sealed interface ProvisioningScanStartResult {
    data object Started : ProvisioningScanStartResult
    data object MissingPermission : ProvisioningScanStartResult
    data object BluetoothUnavailable : ProvisioningScanStartResult
    data object BluetoothOff : ProvisioningScanStartResult
    data class Failed(val failure: ProvisioningScanFailure) : ProvisioningScanStartResult
}

enum class ProvisioningScanFailure {
    ALREADY_RUNNING,
    APP_REGISTRATION_FAILED,
    INTERNAL_ERROR,
    FEATURE_UNSUPPORTED,
    OUT_OF_RESOURCES,
    TOO_FREQUENT
}

sealed interface ProvisioningManualPreflightResult {
    data class Allowed(
        val candidate: ProvisioningCandidateSnapshot
    ) : ProvisioningManualPreflightResult

    data object QrRequired : ProvisioningManualPreflightResult
    data object ResetRequired : ProvisioningManualPreflightResult
    data object MissingPermission : ProvisioningManualPreflightResult
    data object BluetoothOff : ProvisioningManualPreflightResult
    data object BluetoothUnavailable : ProvisioningManualPreflightResult
    data object ConnectionFailed : ProvisioningManualPreflightResult
    data object IncompatibleDevice : ProvisioningManualPreflightResult
}
