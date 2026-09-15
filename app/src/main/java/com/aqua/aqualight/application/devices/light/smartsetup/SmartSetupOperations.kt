package com.aqua.aqualight.application.devices.light.smartsetup

import com.aqua.aqualight.application.aquarium.lighting.AquariumLightingProfile

data class SmartSetupSnapshot(
    val deviceUid: String,
    val tankId: Long,
    val tankName: String,
    val input: SmartSetupInput,
    val decision: SmartSetupDecision,
    val installedPlanId: String?,
    val installedPlanRevision: Long,
    val installedPlanFingerprintMatches: Boolean
)

sealed interface SmartSetupReadResult {
    data class Available(val snapshot: SmartSetupSnapshot) : SmartSetupReadResult
    data object DeviceNotAssigned : SmartSetupReadResult
    data object AquariumNotFound : SmartSetupReadResult
    data object NotConnected : SmartSetupReadResult
    data object InvalidDevice : SmartSetupReadResult
    data object InvalidFirmwareData : SmartSetupReadResult
    data object Unavailable : SmartSetupReadResult
}

enum class SmartSetupProfileSaveFailure {
    DEVICE_NOT_ASSIGNED,
    AQUARIUM_NOT_FOUND,
    INVALID_PROFILE,
    UNAVAILABLE
}

sealed interface SmartSetupProfileSaveResult {
    data class Saved(val snapshot: SmartSetupSnapshot) : SmartSetupProfileSaveResult
    data class Failed(val failure: SmartSetupProfileSaveFailure) : SmartSetupProfileSaveResult
}

enum class SmartSetupApplyFailure {
    NOT_READY,
    PREVIEW_CHANGED,
    NOT_CONNECTED,
    UNSUPPORTED,
    STALE_AUTHORITY,
    REJECTED,
    INVALID_FIRMWARE_DATA,
    COMMIT_UNCONFIRMED,
    UNAVAILABLE
}

sealed interface SmartSetupApplyResult {
    data class Applied(
        val planId: String,
        val revision: Long,
        val storageGeneration: Long,
        val profileFingerprint: String
    ) : SmartSetupApplyResult

    data class Failed(val failure: SmartSetupApplyFailure) : SmartSetupApplyResult
}

/** Owner-scoped Smart Setup boundary. Presentation never consumes runtime/data DTOs. */
interface SmartSetupOperations {
    suspend fun read(deviceUid: String): SmartSetupReadResult

    suspend fun saveProfile(
        deviceUid: String,
        setupDateEpochDay: Long,
        profile: AquariumLightingProfile
    ): SmartSetupProfileSaveResult

    suspend fun apply(
        deviceUid: String,
        expectedProfileFingerprint: String
    ): SmartSetupApplyResult
}
