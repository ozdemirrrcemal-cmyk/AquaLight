package com.aqua.aqualight.data.devices.light.programs.model

import com.aqua.aqualight.data.devices.light.programs.capability.LightProgramFirmwareCapabilities

/**
 * Commercial local source-of-truth for a saved light program.
 *
 * The app can hold many saved programs per device. Current ESP32 firmware is
 * expected to run one active LP schedule at a time, so device upload/activation
 * must be handled separately from local persistence.
 */
data class SavedLightProgram(
    val id: String,
    val ownerUid: String = "",
    val deviceId: Long,
    val deviceUid: String = "",
    val productId: String = "",
    val name: String,
    val draft: LightProgramDraft,
    val isActive: Boolean = false,
    val syncStatus: LightProgramSyncStatus = LightProgramSyncStatus.LOCAL_ONLY,
    val source: LightProgramSource = LightProgramSource.USER_CREATED,
    val compiledChecksum: String = "",
    val firmwareProfile: LightProgramFirmwareProfile = LightProgramFirmwareProfile.currentEsp32(),
    val createdAt: Long,
    val updatedAt: Long,
    val activatedAt: Long? = null,
    val lastSyncedAt: Long? = null,
    val lastError: String = "",
    val schemaVersion: Int = SCHEMA_VERSION
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

enum class LightProgramSyncStatus {
    LOCAL_ONLY,
    SYNCED_TO_DEVICE,
    SYNC_FAILED,
    READ_FROM_DEVICE
}

enum class LightProgramSource {
    USER_CREATED,
    IMPORTED_FROM_DEVICE,
    QUICK_SETUP,
    DUPLICATED,
    PRESET_CONVERTED
}

data class LightProgramFirmwareProfile(
    val supportsWeeklySchedule: Boolean,
    val supportsNativeTransition: Boolean,
    val supportsTemporaryLivePreview: Boolean
) {
    companion object {
        fun currentEsp32(): LightProgramFirmwareProfile {
            val capabilities = LightProgramFirmwareCapabilities.CURRENT_ESP32_LP_POINTS_ONLY
            return LightProgramFirmwareProfile(
                supportsWeeklySchedule = capabilities.supportsWeeklySchedule,
                supportsNativeTransition = capabilities.supportsNativeTransition,
                supportsTemporaryLivePreview = capabilities.supportsTemporaryLivePreview
            )
        }
    }
}
