package com.aqua.aqualight.application.devices

/** Shared application boundary for owner-editable device Settings values. */
interface DeviceSettingsOperations {
    /** A blank value clears the user override and restores the immutable product name. */
    suspend fun updateCustomName(deviceUid: String, customName: String): Result<Unit>
}

/** UI character guard; the data layer additionally enforces the exact 64 UTF-8 byte limit. */
const val DEVICE_CUSTOM_NAME_MAX_LENGTH = 64
