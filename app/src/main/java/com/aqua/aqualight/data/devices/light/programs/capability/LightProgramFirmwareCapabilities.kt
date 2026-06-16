package com.aqua.aqualight.data.devices.light.programs.capability

/**
 * Describes the program features supported by the active light controller
 * firmware/protocol.
 *
 * The Program Editor reads these capabilities through the ViewModel state.
 * It must not hardcode protocol assumptions in the Fragment, so the screen
 * can stay stable when a future ESP32/API firmware adds native weekly repeat
 * scheduling or native transition support.
 */
data class LightProgramFirmwareCapabilities(
    val supportsWeeklySchedule: Boolean,
    val supportsNativeTransition: Boolean
) {
    companion object {
        /**
         * Current ESP32 Light program firmware stores LP time/value points per
         * channel. It does not support per-day program selection or native
         * transition mode fields yet.
         */
        val CURRENT_ESP32_LP_POINTS_ONLY = LightProgramFirmwareCapabilities(
            supportsWeeklySchedule = false,
            supportsNativeTransition = false
        )
    }
}
