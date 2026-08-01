package com.aqua.aqualight.application.devices

/** Shared application boundary for owner-editable device Settings values. */
interface DeviceSettingsOperations {
    suspend fun updateCustomName(deviceUid: String, customName: String): Result<Unit>
}

const val DEVICE_CUSTOM_NAME_MAX_LENGTH = 64
