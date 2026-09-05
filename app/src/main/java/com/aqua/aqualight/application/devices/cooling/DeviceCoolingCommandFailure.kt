package com.aqua.aqualight.application.devices.cooling

/**
 * Stable product semantics for Cooling command failures.
 *
 * Firmware error strings never escape the data boundary. Data maps the strict V1
 * `code + field + message` identity into one of these reasons; presentation then
 * localizes the reason into commercial copy.
 */
enum class DeviceCoolingCommandFailure {
    CONFLICT,
    INVALID_REQUEST,
    INVALID_CONFIGURATION,
    MANUAL_MODE_REQUIRED,
    HARDWARE_UNAVAILABLE,
    HARDWARE_FAILURE,
    STORAGE_FAILURE,
    CLOCK_UNSYNCED,
    PROTOCOL_ERROR,
    UNKNOWN_REJECTION
}
