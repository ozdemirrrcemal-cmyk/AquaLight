package com.aqua.aqualight.application.devices

/** Shared application boundary for owner-editable device Settings values. */
interface DeviceSettingsOperations {
    suspend fun updateCustomName(deviceUid: String, customName: String): Result<Unit>
}

/** UI character ceiling; the operation also enforces the authoritative UTF-8 byte limit. */
const val DEVICE_CUSTOM_NAME_MAX_LENGTH = 64
const val DEVICE_CUSTOM_NAME_MAX_BYTES = 64
