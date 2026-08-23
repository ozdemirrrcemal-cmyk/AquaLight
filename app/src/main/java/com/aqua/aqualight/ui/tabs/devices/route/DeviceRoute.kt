package com.aqua.aqualight.ui.tabs.devices.route

import androidx.annotation.StringRes

/**
 * Navigation decision produced from firmware supplied product family/capabilities.
 *
 * Supported control surfaces navigate by stable device identity only. Their dynamic user-visible
 * title belongs to the central device snapshot and must never be copied into navigation as a second
 * state authority. [unsupportedTitle] exists only for the unsupported fallback screen, which has no
 * supported device-root state holder of its own.
 */
data class DeviceRoute(
    val deviceUid: String,
    val target: DeviceRouteTarget,
    val unsupportedTitle: String = "",
    @StringRes val messageRes: Int = 0
)

enum class DeviceRouteTarget {
    LIGHT_ROOT,
    DOSING_ROOT,
    TIMER_ROOT,
    COOLING_ROOT,
    UNSUPPORTED
}
