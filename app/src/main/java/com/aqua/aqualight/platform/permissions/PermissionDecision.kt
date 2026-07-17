package com.aqua.aqualight.platform.permissions

/** Standard result returned by the central permission policy. */
enum class PermissionDecision {
    GRANTED,
    REQUEST,
    RATIONALE,
    OPEN_SETTINGS
}
