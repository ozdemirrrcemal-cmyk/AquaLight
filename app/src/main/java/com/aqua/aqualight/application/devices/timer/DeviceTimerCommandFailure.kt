package com.aqua.aqualight.application.devices.timer

/** Stable application semantics for every Timer V1 command rejection family. */
enum class DeviceTimerCommandFailure {
    CONFLICT,
    INVALID_REQUEST,
    INVALID_CONFIGURATION,
    CHANNEL_UNAVAILABLE,
    RESOURCE_UNAVAILABLE,
    HARDWARE_FAILURE,
    STORAGE_FAILURE,
    RUNTIME_LOCKED,
    PROTOCOL_ERROR,
    UNKNOWN_REJECTION
}
